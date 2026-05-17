package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("CodexBridgeInstaller")

/**
 * Installs Agent Belay as a hook source for OpenAI's Codex CLI.
 *
 * Codex stores configuration in `~/.codex/config.toml`. Its hook runner
 * executes command handlers, so this installer writes small bridge scripts
 * under `~/.agent-belay/` and points Codex's inline TOML hook entries at
 * those commands.
 *
 * Rather than pull in a TOML parser, we bracket our hook entries inside
 * a managed block:
 *
 * ```
 * # >>> agent-belay >>>
 * # Managed by Agent Belay — do not edit. Re-register in Agent Belay
 * # to update the port; unregister to remove.
 * [[hooks.PermissionRequest]]
 * matcher = "*"
 * [[hooks.PermissionRequest.hooks]]
 * type = "command"
 * command = "/home/user/.agent-belay/codex-approve.sh"
 * …
 * # <<< agent-belay <<<
 * ```
 *
 * `register` reads the file, replaces any existing block (or appends a
 * new one if absent), and atomically writes the result back.
 * `unregister` strips the block. User content outside the markers is
 * preserved untouched, so coexistence with hand-edited keys is safe.
 *
 * If a user manually moves or breaks the markers, we treat the file as
 * not-registered and a fresh `register` will append a new block — never
 * mutating user lines.
 */
object CodexBridgeInstaller {

    private const val BEGIN_MARKER = "# >>> agent-belay >>>"
    private const val END_MARKER = "# <<< agent-belay <<<"
    private const val PERMISSION_REQUEST_SCRIPT_NAME = "codex-approve.sh"
    private const val PRE_TOOL_USE_SCRIPT_NAME = "codex-pre-tool-use.sh"
    private const val POST_TOOL_USE_SCRIPT_NAME = "codex-post-tool-use.sh"
    private const val USER_PROMPT_SUBMIT_SCRIPT_NAME = "codex-user-prompt-submit.sh"
    private const val SESSION_START_SCRIPT_NAME = "codex-session-start.sh"

    private const val PERMISSION_REQUEST_ENDPOINT = "approve-codex"
    private const val PRE_TOOL_USE_ENDPOINT = "pre-tool-use-codex"
    private const val POST_TOOL_USE_ENDPOINT = "post-tool-use-codex"
    private const val USER_PROMPT_SUBMIT_ENDPOINT = "capability/inject-codex"
    private const val SESSION_START_ENDPOINT = "capability/session-start-codex"

    private fun configFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".codex/config.toml")
    }

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-belay")
    }

    private fun permissionRequestScriptFile(): File = File(scriptDir(), PERMISSION_REQUEST_SCRIPT_NAME)
    private fun preToolUseScriptFile(): File = File(scriptDir(), PRE_TOOL_USE_SCRIPT_NAME)
    private fun postToolUseScriptFile(): File = File(scriptDir(), POST_TOOL_USE_SCRIPT_NAME)
    private fun userPromptSubmitScriptFile(): File = File(scriptDir(), USER_PROMPT_SUBMIT_SCRIPT_NAME)
    private fun sessionStartScriptFile(): File = File(scriptDir(), SESSION_START_SCRIPT_NAME)

    private fun permissionRequestScriptPath(): String = permissionRequestScriptFile().absolutePath
    private fun preToolUseScriptPath(): String = preToolUseScriptFile().absolutePath
    private fun postToolUseScriptPath(): String = postToolUseScriptFile().absolutePath
    private fun userPromptSubmitScriptPath(): String = userPromptSubmitScriptFile().absolutePath
    private fun sessionStartScriptPath(): String = sessionStartScriptFile().absolutePath

    fun isRegistered(port: Int): Boolean {
        val file = configFile()
        val scripts = listOf(permissionRequestScriptFile(), preToolUseScriptFile(), postToolUseScriptFile())
        if (scripts.any { !it.exists() || !it.canExecute() }) return false
        if (!file.exists()) return false
        return try {
            val text = file.readText()
            val block = extractManagedBlock(text) ?: return false
            hasHooksFeature(text) &&
                block.contains(permissionRequestScriptPath()) &&
                block.contains(preToolUseScriptPath()) &&
                block.contains(postToolUseScriptPath()) &&
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
            val includeUserPromptSubmit = currentBlock?.contains(userPromptSubmitScriptPath()) == true
            val includeSessionStart = currentBlock?.contains(sessionStartScriptPath()) == true
            installScripts(
                port = port,
                includePermissionHooks = true,
                includeUserPromptSubmit = includeUserPromptSubmit,
                includeSessionStart = includeSessionStart,
            )
            val withoutBlock = ensureHooksFeature(stripManagedBlock(original))
            val block = buildManagedBlock(
                port = port,
                includePermissionHooks = true,
                includeUserPromptSubmit = includeUserPromptSubmit,
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
            logger.i { "Registered Codex hooks for port $port" }
        }
    }

    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        listOf(permissionRequestScriptFile(), preToolUseScriptFile(), postToolUseScriptFile()).forEach { script ->
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
            val includeUserPromptSubmit = currentBlock.contains(userPromptSubmitScriptPath())
            val includeSessionStart = currentBlock.contains(sessionStartScriptPath())
            val stripped = stripManagedBlock(original)
            val updated = if (includeUserPromptSubmit || includeSessionStart) {
                val block = buildManagedBlock(
                    port = port,
                    includePermissionHooks = false,
                    includeUserPromptSubmit = includeUserPromptSubmit,
                    includeSessionStart = includeSessionStart,
                )
                if (stripped.isBlank()) block + "\n"
                else if (stripped.endsWith("\n")) stripped + "\n" + block + "\n"
                else stripped + "\n\n" + block + "\n"
            } else {
                stripped
            }
            // If the file is now empty / whitespace-only, delete it to avoid
            // leaving an orphaned config file we created.
            if (updated.isBlank()) {
                file.delete()
                logger.i { "Removed empty ${file.absolutePath} after unregistering Codex hooks" }
            } else {
                atomicWrite(file, updated)
                logger.i { "Unregistered Codex hooks from ${file.absolutePath}" }
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
            hasHooksFeature(text) &&
                scripts.isNotEmpty() &&
                scripts.all { it.exists() && it.canExecute() && it.readText().contains("localhost:$port") }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read ${file.absolutePath}" }
            false
        }
    }

    fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean) {
        if (!userPromptSubmit && !sessionStart) {
            unregisterCapabilityHook(port)
            return
        }

        val file = configFile()
        file.parentFile.mkdirs()

        withFileLock(file) {
            val original = if (file.exists()) file.readText() else ""
            val currentBlock = extractManagedBlock(original)
            val includePermissionHooks = currentBlock?.contains(permissionRequestScriptPath()) == true ||
                currentBlock?.contains(preToolUseScriptPath()) == true ||
                currentBlock?.contains(postToolUseScriptPath()) == true
            installScripts(
                port = port,
                includePermissionHooks = includePermissionHooks,
                includeUserPromptSubmit = userPromptSubmit,
                includeSessionStart = sessionStart,
            )
            if (!userPromptSubmit) userPromptSubmitScriptFile().takeIf { it.exists() }?.delete()
            if (!sessionStart) sessionStartScriptFile().takeIf { it.exists() }?.delete()
            val withoutBlock = ensureHooksFeature(stripManagedBlock(original))
            val block = buildManagedBlock(
                port = port,
                includePermissionHooks = includePermissionHooks,
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
            logger.i { "Registered Codex capability hooks for port $port" }
        }
    }

    fun unregisterCapabilityHook(@Suppress("UNUSED_PARAMETER") port: Int) {
        listOf(userPromptSubmitScriptFile(), sessionStartScriptFile()).forEach { script ->
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
            val includePermissionHooks = currentBlock.contains(permissionRequestScriptPath()) ||
                currentBlock.contains(preToolUseScriptPath()) ||
                currentBlock.contains(postToolUseScriptPath())
            val stripped = stripManagedBlock(original)
            val updated = if (includePermissionHooks) {
                val block = buildManagedBlock(
                    port = port,
                    includePermissionHooks = true,
                    includeUserPromptSubmit = false,
                    includeSessionStart = false,
                )
                if (stripped.isBlank()) block + "\n"
                else if (stripped.endsWith("\n")) stripped + "\n" + block + "\n"
                else stripped + "\n\n" + block + "\n"
            } else {
                stripped
            }
            if (updated.isBlank()) {
                file.delete()
                logger.i { "Removed empty ${file.absolutePath} after unregistering Codex capability hooks" }
            } else {
                atomicWrite(file, updated)
                logger.i { "Unregistered Codex capability hooks from ${file.absolutePath}" }
            }
        }
    }

    internal fun buildManagedBlock(
        @Suppress("UNUSED_PARAMETER") port: Int,
        includePermissionHooks: Boolean = true,
        includeUserPromptSubmit: Boolean = false,
        includeSessionStart: Boolean = false,
    ): String = buildString {
        appendLine(BEGIN_MARKER)
        appendLine("# Managed by Agent Belay - do not edit. Re-register in Agent Belay")
        appendLine("# to update the port; unregister to remove. Requires:")
        appendLine("# [features]")
        appendLine("# hooks = true")
        if (includePermissionHooks) {
            appendLine(
                """
                |[[hooks.PermissionRequest]]
                |matcher = "*"
                |[[hooks.PermissionRequest.hooks]]
                |type = "command"
                |command = "${permissionRequestScriptPath()}"
                |timeout = 300
                |statusMessage = "Checking approval request"
                |
                |[[hooks.PreToolUse]]
                |matcher = "*"
                |[[hooks.PreToolUse.hooks]]
                |type = "command"
                |command = "${preToolUseScriptPath()}"
                |timeout = 300
                |statusMessage = "Checking tool use"
                |
                |[[hooks.PostToolUse]]
                |matcher = "*"
                |[[hooks.PostToolUse.hooks]]
                |type = "command"
                |command = "${postToolUseScriptPath()}"
                |timeout = 120
                |statusMessage = "Recording tool result"
                """.trimMargin().trimEnd(),
            )
        }
        if (includeUserPromptSubmit) {
            if (last() != '\n') appendLine()
            appendLine()
            appendLine(
                """
                |[[hooks.UserPromptSubmit]]
                |matcher = "*"
                |[[hooks.UserPromptSubmit.hooks]]
                |type = "command"
                |command = "${userPromptSubmitScriptPath()}"
                |timeout = 120
                |statusMessage = "Injecting Agent Belay context"
                """.trimMargin().trimEnd(),
            )
        }
        if (includeSessionStart) {
            if (last() != '\n') appendLine()
            appendLine()
            appendLine(
                """
                |[[hooks.SessionStart]]
                |matcher = "*"
                |[[hooks.SessionStart.hooks]]
                |type = "command"
                |command = "${sessionStartScriptPath()}"
                |timeout = 120
                |statusMessage = "Injecting Agent Belay session context"
                """.trimMargin().trimEnd(),
            )
        }
        if (last() != '\n') appendLine()
        append(END_MARKER)
    }

    /** Returns the contents between the markers (exclusive), or null if not found. */
    internal fun extractManagedBlock(text: String): String? {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return null
        val end = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (end < 0) return null
        return text.substring(begin + BEGIN_MARKER.length, end)
    }

    /** Removes the managed block (and one trailing newline) from [text]. */
    internal fun stripManagedBlock(text: String): String {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return text
        val endStart = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (endStart < 0) return text
        var endExclusive = endStart + END_MARKER.length
        if (endExclusive < text.length && text[endExclusive] == '\n') endExclusive++
        // Also collapse a leading blank line we might have inserted.
        var beginInclusive = begin
        if (beginInclusive > 0 && text[beginInclusive - 1] == '\n') {
            // peek one further back to drop the separator we added
            if (beginInclusive >= 2 && text[beginInclusive - 2] == '\n') beginInclusive--
        }
        return text.substring(0, beginInclusive) + text.substring(endExclusive)
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun installScripts(
        port: Int,
        includePermissionHooks: Boolean,
        includeUserPromptSubmit: Boolean,
        includeSessionStart: Boolean,
    ) {
        scriptDir().mkdirs()
        if (includePermissionHooks) {
            atomicWriteExecutable(permissionRequestScriptFile(), buildScriptContent(PERMISSION_REQUEST_ENDPOINT, port))
            atomicWriteExecutable(preToolUseScriptFile(), buildScriptContent(PRE_TOOL_USE_ENDPOINT, port))
            atomicWriteExecutable(postToolUseScriptFile(), buildScriptContent(POST_TOOL_USE_ENDPOINT, port))
        }
        if (includeUserPromptSubmit) {
            atomicWriteExecutable(userPromptSubmitScriptFile(), buildScriptContent(USER_PROMPT_SUBMIT_ENDPOINT, port))
        }
        if (includeSessionStart) {
            atomicWriteExecutable(sessionStartScriptFile(), buildScriptContent(SESSION_START_ENDPOINT, port))
        }
        logger.i { "Installed Codex bridge scripts in ${scriptDir().absolutePath}" }
    }

    private fun buildScriptContent(endpoint: String, port: Int): String = """
        |#!/usr/bin/env bash
        |# Agent Belay bridge script for Codex CLI
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
        |    # Server unreachable — fail open so Codex keeps its native flow.
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

    private fun hasHooksFeature(text: String): Boolean {
        val features = findTable(text, "features") ?: return false
        return Regex("""(?m)^\s*hooks\s*=\s*true\s*(?:#.*)?$""").containsMatchIn(features)
    }

    private fun ensureHooksFeature(text: String): String {
        val table = findTableBounds(text, "features")
        if (table == null) {
            val prefix = if (text.isBlank()) "" else text.trimEnd() + "\n\n"
            return prefix + "[features]\nhooks = true\n"
        }

        val section = text.substring(table.bodyStart, table.end)
        val hooksSetting = Regex("""(?m)^(\s*)hooks\s*=\s*(?:true|false)\s*(?:#.*)?$""")
        val deprecatedSetting = Regex("""(?m)^\s*codex_hooks\s*=\s*(?:true|false)\s*(?:#.*)?\r?\n?""")
        val withoutDeprecated = deprecatedSetting.replace(section, "")
        val updatedSection = if (hooksSetting.containsMatchIn(withoutDeprecated)) {
            hooksSetting.replace(withoutDeprecated) { match -> "${match.groupValues[1]}hooks = true" }
        } else {
            val insertion = if (withoutDeprecated.isEmpty() || withoutDeprecated.endsWith("\n")) {
                "hooks = true\n"
            } else {
                "\nhooks = true\n"
            }
            withoutDeprecated + insertion
        }
        return text.substring(0, table.bodyStart) + updatedSection + text.substring(table.end)
    }

    private fun findTable(text: String, name: String): String? {
        val bounds = findTableBounds(text, name) ?: return null
        return text.substring(bounds.bodyStart, bounds.end)
    }

    private data class TableBounds(val bodyStart: Int, val end: Int)

    private fun findTableBounds(text: String, name: String): TableBounds? {
        val header = Regex("""(?m)^\s*\[$name]\s*(?:#.*)?$""").find(text) ?: return null
        val bodyStart = text.indexOf('\n', header.range.last + 1).let {
            if (it < 0) text.length else it + 1
        }
        val nextHeader = Regex("""(?m)^\s*\[""").find(text, bodyStart)
        return TableBounds(bodyStart = bodyStart, end = nextHeader?.range?.first ?: text.length)
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
