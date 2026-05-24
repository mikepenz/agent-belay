package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("AntigravityBridgeInstaller")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    explicitNulls = false
}

@Serializable
private data class AntigravityHookDef(
    val matcher: String = ".*",
    val command: String
)

object AntigravityBridgeInstaller {

    private const val PRE_TOOL_USE_SCRIPT_NAME = "antigravity-pre-tool-use.sh"
    private const val PRE_TOOL_USE_ENDPOINT = "pre-tool-use-antigravity"

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-belay")
    }

    private fun preToolUseScriptFile(): File = File(scriptDir(), PRE_TOOL_USE_SCRIPT_NAME)
    private fun preToolUseScriptPath(): String = preToolUseScriptFile().absolutePath

    private fun configFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".antigravitycli/settings.json")
    }

    // Legacy Gemini files for cleanup/migration
    private fun legacyConfigFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".gemini/settings.json")
    }

    private fun legacyScriptFile1(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-belay/gemini-pre-tool-use.sh")
    }

    private fun legacyScriptFile2(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-approver/gemini-pre-tool-use.sh")
    }

    fun isRegistered(port: Int): Boolean {
        val script = preToolUseScriptFile()
        if (!script.exists() || !script.canExecute()) return false

        val config = configFile()
        if (!config.exists()) return false

        return try {
            val text = config.readText()
            if (text.isBlank()) return false
            val root = json.parseToJsonElement(text).jsonObject
            val beforeTool = root["BeforeTool"]?.jsonArray ?: return false
            beforeTool.any { entry ->
                val command = entry.jsonObject["command"]?.jsonPrimitive?.content ?: ""
                command == preToolUseScriptPath()
            } && script.readText().contains("localhost:$port")
        } catch (e: Exception) {
            logger.w(e) { "Failed to read ${config.absolutePath}" }
            false
        }
    }

    fun register(port: Int) {
        // Clean up legacy Gemini integration first
        cleanupLegacyGemini()

        val script = preToolUseScriptFile()
        script.parentFile.mkdirs()

        // 1. Write the bridge script
        val scriptContents = buildBridgeScript(port)
        script.writeText(scriptContents)
        script.setExecutable(true)

        // 2. Write/merge hook settings file
        val config = configFile()
        config.parentFile.mkdirs()

        withFileLock(config) {
            val root = if (config.exists()) {
                try {
                    json.parseToJsonElement(config.readText()).jsonObject
                } catch (e: Exception) {
                    logger.w(e) { "Corrupt settings.json — overwriting" }
                    JsonObject(emptyMap())
                }
            } else {
                JsonObject(emptyMap())
            }

            val existingBeforeTool = root["BeforeTool"]?.jsonArray?.toMutableList() ?: mutableListOf()
            val alreadyRegistered = existingBeforeTool.any { entry ->
                entry.jsonObject["command"]?.jsonPrimitive?.content == preToolUseScriptPath()
            }

            if (!alreadyRegistered) {
                val newEntry = json.encodeToJsonElement(
                    AntigravityHookDef.serializer(),
                    AntigravityHookDef(matcher = ".*", command = preToolUseScriptPath())
                )
                existingBeforeTool.add(newEntry)
            }

            val updatedRoot = buildJsonObject {
                root.forEach { (key, value) ->
                    if (key != "BeforeTool") put(key, value)
                }
                put("BeforeTool", JsonArray(existingBeforeTool))
            }

            atomicWrite(config, json.encodeToString(JsonElement.serializer(), updatedRoot))
            logger.i { "Registered Antigravity user-scoped pre-tool hook for port $port" }
        }
    }

    fun unregister(port: Int) {
        // Clean up legacy Gemini integration
        cleanupLegacyGemini()

        val script = preToolUseScriptFile()
        if (script.exists()) {
            script.delete()
        }

        val config = configFile()
        if (config.exists()) {
            var shouldDeleteConfig = false
            withFileLock(config) {
                val root = try {
                    json.parseToJsonElement(config.readText()).jsonObject
                } catch (e: Exception) {
                    logger.w(e) { "Corrupt settings.json — deleting" }
                    shouldDeleteConfig = true
                    return@withFileLock
                }

                val existingBeforeTool = root["BeforeTool"]?.jsonArray
                if (existingBeforeTool == null) {
                    if (root.isEmpty()) {
                        shouldDeleteConfig = true
                    }
                    return@withFileLock
                }
                val filteredList = existingBeforeTool.filter { entry ->
                    entry.jsonObject["command"]?.jsonPrimitive?.content != preToolUseScriptPath()
                }

                val updatedRoot = buildJsonObject {
                    root.forEach { (key, value) ->
                        if (key != "BeforeTool") put(key, value)
                    }
                    if (filteredList.isNotEmpty()) {
                        put("BeforeTool", JsonArray(filteredList))
                    }
                }

                val isEmpty = updatedRoot.isEmpty()
                if (isEmpty) {
                    shouldDeleteConfig = true
                } else {
                    atomicWrite(config, json.encodeToString(JsonElement.serializer(), updatedRoot))
                    logger.i { "Unregistered Antigravity hook from settings.json" }
                }
            }

            if (shouldDeleteConfig) {
                config.delete()
                val lockFile = File(config.parentFile, "${config.name}.lock")
                if (lockFile.exists()) lockFile.delete()
                deleteDirectoryIfEmpty(config.parentFile)
                logger.i { "Removed Antigravity settings file (empty)" }
            }
        }
    }

    private fun cleanupLegacyGemini() {
        runCatching {
            // Delete legacy shims
            val script1 = legacyScriptFile1()
            if (script1.exists()) script1.delete()

            val script2 = legacyScriptFile2()
            if (script2.exists()) script2.delete()
            deleteDirectoryIfEmpty(script2.parentFile) // Delete .agent-approver if empty

            // Remove hook from legacy config
            val config = legacyConfigFile()
            var shouldDeleteConfig = false
            if (config.exists()) {
                withFileLock(config) {
                    val root = try {
                        json.parseToJsonElement(config.readText()).jsonObject
                    } catch (e: Exception) {
                        shouldDeleteConfig = true
                        return@withFileLock
                    }
                    val beforeTool = root["BeforeTool"]?.jsonArray
                    if (beforeTool != null) {
                        val filtered = beforeTool.filter { entry ->
                            val cmd = entry.jsonObject["command"]?.jsonPrimitive?.content ?: ""
                            !cmd.contains("gemini-pre-tool-use.sh")
                        }

                        val updated = buildJsonObject {
                            root.forEach { (key, value) ->
                                if (key != "BeforeTool") put(key, value)
                            }
                            if (filtered.isNotEmpty()) {
                                put("BeforeTool", JsonArray(filtered))
                            }
                        }

                        if (updated.isEmpty()) {
                            shouldDeleteConfig = true
                        } else {
                            atomicWrite(config, json.encodeToString(JsonElement.serializer(), updated))
                            logger.i { "Migrated/removed Gemini hooks from settings.json" }
                        }
                    } else {
                        if (root.isEmpty()) {
                            shouldDeleteConfig = true
                        }
                    }
                }

                if (shouldDeleteConfig) {
                    config.delete()
                    val lockFile = File(config.parentFile, "${config.name}.lock")
                    if (lockFile.exists()) lockFile.delete()
                    deleteDirectoryIfEmpty(config.parentFile)
                    logger.i { "Cleaned up empty legacy Gemini config directory" }
                }
            }
        }.onFailure { e ->
            logger.w(e) { "Failed to clean up legacy Gemini files" }
        }
    }

    private fun buildBridgeScript(port: Int): String {
        return """
            |#!/usr/bin/env bash
            |# Agent Belay bridge script for Antigravity CLI
            |# Reads hook JSON from stdin, POSTs to Agent Belay, returns response.
            |# Fail-open: if server is unreachable, exits 0 so Antigravity proceeds normally.
            |
            |set -uo pipefail
            |
            |URL="http://localhost:$port/$PRE_TOOL_USE_ENDPOINT"
            |INPUT=${'$'}(cat)
            |
            |RESPONSE=${'$'}(printf '%s' "${'$'}INPUT" | curl -sS --max-time 10 \
            |    -X POST \
            |    -H "Content-Type: application/json" \
            |    --data-binary @- \
            |    "${'$'}URL" 2>/dev/null)
            |CURL_EXIT=${'$'}?
            |
            |if [ "${'$'}CURL_EXIT" -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
            |    # Server unreachable — fail open
            |    exit 0
            |fi
            |
            |printf '%s' "${'$'}RESPONSE"
        """.trimMargin()
    }

    private fun atomicWrite(file: File, contents: String) {
        val temp = File.createTempFile(".belay-settings", ".json", file.parentFile)
        try {
            temp.writeText(contents)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun deleteDirectoryIfEmpty(dir: File?) {
        if (dir != null && dir.exists() && dir.isDirectory) {
            val children = dir.list()
            if (children == null || children.isEmpty()) {
                dir.delete()
            }
        }
    }

    private inline fun withFileLock(file: File, block: () -> Unit) {
        val lockFile = File(file.parentFile, "${file.name}.lock")
        lockFile.parentFile.mkdirs()
        var raf: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            raf = RandomAccessFile(lockFile, "rw")
            lock = try {
                raf.channel.lock()
            } catch (e: Exception) {
                logger.w(e) { "Could not acquire file lock on ${lockFile.absolutePath} — proceeding unlocked" }
                null
            }
            block()
        } finally {
            lock?.release()
            raf?.close()
            if (lockFile.exists()) {
                lockFile.delete()
            }
        }
    }
}
