package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("HermesBridgeInstaller")

object HermesBridgeInstaller {

    private const val BEGIN_MARKER = "# >>> agent-belay >>>"
    private const val END_MARKER = "# <<< agent-belay <<<"
    private const val PRE_TOOL_CALL_SCRIPT_NAME = "hermes-pre-tool-call.sh"
    private const val POST_TOOL_CALL_SCRIPT_NAME = "hermes-post-tool-call.sh"
    private const val USER_PROMPT_SUBMIT_SCRIPT_NAME = "hermes-user-prompt-submit.sh"
    private const val SESSION_START_SCRIPT_NAME = "hermes-session-start.sh"

    private const val PRE_TOOL_USE_ENDPOINT = "pre-tool-use-hermes"
    private const val POST_TOOL_USE_ENDPOINT = "post-tool-use-hermes"
    private const val USER_PROMPT_SUBMIT_ENDPOINT = "capability/inject-hermes"
    private const val SESSION_START_ENDPOINT = "capability/session-start-hermes"

    private fun configFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".hermes/config.yaml")
    }

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-belay")
    }

    private fun preToolCallScriptFile(): File = File(scriptDir(), PRE_TOOL_CALL_SCRIPT_NAME)
    private fun postToolCallScriptFile(): File = File(scriptDir(), POST_TOOL_CALL_SCRIPT_NAME)
    private fun userPromptSubmitScriptFile(): File = File(scriptDir(), USER_PROMPT_SUBMIT_SCRIPT_NAME)
    private fun sessionStartScriptFile(): File = File(scriptDir(), SESSION_START_SCRIPT_NAME)

    private fun preToolCallScriptPath(): String = preToolCallScriptFile().absolutePath
    private fun postToolCallScriptPath(): String = postToolCallScriptFile().absolutePath
    private fun userPromptSubmitScriptPath(): String = userPromptSubmitScriptFile().absolutePath
    private fun sessionStartScriptPath(): String = sessionStartScriptFile().absolutePath

    fun isRegistered(port: Int): Boolean {
        val file = configFile()
        val scripts = listOf(preToolCallScriptFile(), postToolCallScriptFile())
        if (scripts.any { !it.exists() || !it.canExecute() }) return false
        if (!file.exists()) return false
        return try {
            val text = file.readText()
            val block = extractManagedBlock(text) ?: return false
            block.contains(preToolCallScriptPath()) &&
                block.contains(postToolCallScriptPath()) &&
                scripts.all { it.readText().contains("localhost:$port") }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read ${file.absolutePath}" }
            false
        }
    }

    fun register(port: Int) {
        val file = configFile()
        file.parentFile.mkdirs()

        withFileLock(file) {
            val original = if (file.exists()) file.readText() else ""
            val currentBlock = extractManagedBlock(original)
            val includeUserPrompt = currentBlock?.contains(userPromptSubmitScriptPath()) == true
            val includeSessionStart = currentBlock?.contains(sessionStartScriptPath()) == true

            installScripts(port = port)
            if (includeUserPrompt) {
                atomicWriteExecutable(userPromptSubmitScriptFile(), buildScriptContent(USER_PROMPT_SUBMIT_ENDPOINT, port))
            }
            if (includeSessionStart) {
                atomicWriteExecutable(sessionStartScriptFile(), buildScriptContent(SESSION_START_ENDPOINT, port))
            }

            val withoutBlock = stripManagedBlock(original)
            val block = buildManagedBlock(
                port = port,
                includeMainHooks = true,
                includeUserPromptSubmit = includeUserPrompt,
                includeSessionStart = includeSessionStart,
            )
            val updated = if (withoutBlock.isEmpty()) {
                block + "\n"
            } else if (withoutBlock.endsWith("\n")) {
                withoutBlock + "\n" + block + "\n"
            } else {
                withoutBlock + "\n\n" + block + "\n"
            }
            atomicWrite(file, updated)
            logger.i { "Registered Hermes hooks for port $port" }
        }
    }

    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        listOf(preToolCallScriptFile(), postToolCallScriptFile()).forEach { script ->
            if (script.exists()) {
                script.delete()
                logger.i { "Removed bridge script ${script.absolutePath}" }
            }
        }

        val file = configFile()
        if (!file.exists()) return

        withFileLock(file) {
            val original = file.readText()
            val currentBlock = extractManagedBlock(original) ?: return@withFileLock
            val includeUserPrompt = currentBlock.contains(userPromptSubmitScriptPath())
            val includeSessionStart = currentBlock.contains(sessionStartScriptPath())
            val stripped = stripManagedBlock(original)

            if (includeUserPrompt || includeSessionStart) {
                // Re-register ONLY capability hooks
                val block = buildManagedBlock(
                    port = port,
                    includeMainHooks = false,
                    includeUserPromptSubmit = includeUserPrompt,
                    includeSessionStart = includeSessionStart,
                )
                val updated = if (stripped.isEmpty()) {
                    block + "\n"
                } else if (stripped.endsWith("\n")) {
                    stripped + "\n" + block + "\n"
                } else {
                    stripped + "\n\n" + block + "\n"
                }
                atomicWrite(file, updated)
                logger.i { "Unregistered Hermes main hooks but preserved capability hooks for port $port" }
            } else {
                if (stripped.isBlank()) {
                    file.delete()
                    logger.i { "Removed empty ${file.absolutePath} after unregistering Hermes hooks" }
                } else {
                    atomicWrite(file, stripped)
                    logger.i { "Unregistered Hermes hooks from ${file.absolutePath}" }
                }
            }
        }
    }

    fun isCapabilityHookRegistered(port: Int): Boolean {
        val file = configFile()
        if (!file.exists()) return false
        return try {
            val text = file.readText()
            val block = extractManagedBlock(text) ?: return false
            val scripts = buildList {
                if (block.contains(userPromptSubmitScriptPath())) add(userPromptSubmitScriptFile())
                if (block.contains(sessionStartScriptPath())) add(sessionStartScriptFile())
            }
            scripts.isNotEmpty() &&
                scripts.all { it.exists() && it.canExecute() && it.readText().contains("localhost:$port") }
        } catch (e: Exception) {
            false
        }
    }

    fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean) {
        val file = configFile()
        file.parentFile.mkdirs()

        withFileLock(file) {
            val original = if (file.exists()) file.readText() else ""
            val currentBlock = extractManagedBlock(original)
            val hasMainHooks = currentBlock?.contains(preToolCallScriptPath()) == true

            if (userPromptSubmit) {
                scriptDir().mkdirs()
                atomicWriteExecutable(userPromptSubmitScriptFile(), buildScriptContent(USER_PROMPT_SUBMIT_ENDPOINT, port))
            } else {
                userPromptSubmitScriptFile().takeIf { it.exists() }?.delete()
            }

            if (sessionStart) {
                scriptDir().mkdirs()
                atomicWriteExecutable(sessionStartScriptFile(), buildScriptContent(SESSION_START_ENDPOINT, port))
            } else {
                sessionStartScriptFile().takeIf { it.exists() }?.delete()
            }

            val withoutBlock = stripManagedBlock(original)
            val block = buildManagedBlock(
                port = port,
                includeMainHooks = hasMainHooks,
                includeUserPromptSubmit = userPromptSubmit,
                includeSessionStart = sessionStart,
            )
            val updated = if (withoutBlock.isEmpty()) {
                block + "\n"
            } else if (withoutBlock.endsWith("\n")) {
                withoutBlock + "\n" + block + "\n"
            } else {
                withoutBlock + "\n\n" + block + "\n"
            }
            atomicWrite(file, updated)
            logger.i { "Registered Hermes capability hooks for port $port (userPromptSubmit=$userPromptSubmit, sessionStart=$sessionStart)" }
        }
    }

    fun unregisterCapabilityHook(port: Int) {
        listOf(userPromptSubmitScriptFile(), sessionStartScriptFile()).forEach { script ->
            if (script.exists()) {
                script.delete()
                logger.i { "Removed Hermes capability bridge script ${script.absolutePath}" }
            }
        }

        val file = configFile()
        if (!file.exists()) return

        withFileLock(file) {
            val original = file.readText()
            val currentBlock = extractManagedBlock(original) ?: return@withFileLock
            val hasMainHooks = currentBlock.contains(preToolCallScriptPath())
            val stripped = stripManagedBlock(original)

            if (hasMainHooks) {
                val block = buildManagedBlock(
                    port = port,
                    includeMainHooks = true,
                    includeUserPromptSubmit = false,
                    includeSessionStart = false,
                )
                val updated = if (stripped.isEmpty()) {
                    block + "\n"
                } else if (stripped.endsWith("\n")) {
                    stripped + "\n" + block + "\n"
                } else {
                    stripped + "\n\n" + block + "\n"
                }
                atomicWrite(file, updated)
                logger.i { "Unregistered Hermes capability hooks but preserved main hooks for port $port" }
            } else {
                if (stripped.isBlank()) {
                    file.delete()
                    logger.i { "Removed empty ${file.absolutePath} after unregistering Hermes capability hooks" }
                } else {
                    atomicWrite(file, stripped)
                    logger.i { "Unregistered Hermes capability hooks from ${file.absolutePath}" }
                }
            }
        }
    }

    internal fun buildManagedBlock(
        port: Int,
        includeMainHooks: Boolean = true,
        includeUserPromptSubmit: Boolean = false,
        includeSessionStart: Boolean = false,
    ): String = buildString {
        appendLine(BEGIN_MARKER)
        appendLine("# Managed by Agent Belay - do not edit. Re-register in Agent Belay")
        appendLine("# to update the port; unregister to remove.")
        appendLine("# First Hermes run prompts to allowlist these commands (TTY only).")
        appendLine("# For non-TTY / gateway use: export HERMES_ACCEPT_HOOKS=1 or run")
        appendLine("# 'hermes --accept-hooks chat' once to persist the allowlist entry.")
        appendLine("hooks:")
        if (includeMainHooks) {
            appendLine("  pre_tool_call:")
            appendLine("    - matcher: \".*\"")
            appendLine("      command: \"${preToolCallScriptPath()}\"")
            appendLine("      timeout: 300")
            appendLine("  post_tool_call:")
            appendLine("    - matcher: \".*\"")
            appendLine("      command: \"${postToolCallScriptPath()}\"")
            appendLine("      timeout: 120")
        }
        if (includeUserPromptSubmit) {
            appendLine("  pre_llm_call:")
            appendLine("    - command: \"${userPromptSubmitScriptPath()}\"")
            appendLine("      timeout: 120")
        }
        if (includeSessionStart) {
            appendLine("  on_session_start:")
            appendLine("    - command: \"${sessionStartScriptPath()}\"")
            appendLine("      timeout: 120")
        }
        append(END_MARKER)
    }

    internal fun extractManagedBlock(text: String): String? {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return null
        val end = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (end < 0) return null
        return text.substring(begin + BEGIN_MARKER.length, end)
    }

    internal fun stripManagedBlock(text: String): String {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return text
        val endStart = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (endStart < 0) return text
        var endExclusive = endStart + END_MARKER.length
        if (endExclusive < text.length && text[endExclusive] == '\n') endExclusive++
        var beginInclusive = begin
        if (beginInclusive > 0 && text[beginInclusive - 1] == '\n') {
            if (beginInclusive >= 2 && text[beginInclusive - 2] == '\n') beginInclusive--
        }
        return text.substring(0, beginInclusive) + text.substring(endExclusive)
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun installScripts(port: Int) {
        scriptDir().mkdirs()
        atomicWriteExecutable(preToolCallScriptFile(), buildScriptContent(PRE_TOOL_USE_ENDPOINT, port))
        atomicWriteExecutable(postToolCallScriptFile(), buildScriptContent(POST_TOOL_USE_ENDPOINT, port))
        logger.i { "Installed Hermes bridge scripts in ${scriptDir().absolutePath}" }
    }

    private fun buildScriptContent(endpoint: String, port: Int): String = """
        |#!/usr/bin/env bash
        |# Agent Belay bridge script for Hermes Agent
        |# Reads hook JSON from stdin, POSTs to Agent Belay, returns response.
        |
        |set -uo pipefail
        |
        |URL="http://localhost:$port/$endpoint"
        |INPUT=${'$'}(cat)
        |
        |RESPONSE=${'$'}(printf '%s' "${'$'}INPUT" | curl -sS --max-time 300 \
        |    -X POST \
        |    -H "Content-Type: application/json" \
        |    --data-binary @- \
        |    "${'$'}URL" 2>/dev/null)
        |CURL_EXIT=${'$'}?
        |
        |if [ "${'$'}CURL_EXIT" -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
        |    # Server unreachable — fail open.
        |    exit 0
        |fi
        |
        |printf '%s' "${'$'}RESPONSE"
    """.trimMargin()

    private fun atomicWriteExecutable(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.setExecutable(true)) {
            logger.w { "Failed to set executable bit on ${tmp.absolutePath}" }
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
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
        }
    }
}
