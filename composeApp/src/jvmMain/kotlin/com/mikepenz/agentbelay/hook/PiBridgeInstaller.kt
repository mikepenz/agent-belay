package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val piLogger = Logger.withTag("PiBridgeInstaller")

/**
 * Installs Agent Belay as a global Pi extension.
 *
 * Pi auto-discovers TypeScript extensions from `~/.pi/agent/extensions/`.
 * The generated extension intercepts `tool_call`, POSTs the tool request to
 * Belay, and returns `{ block: true, reason }` when Belay denies it.
 */
object PiBridgeInstaller {

    private const val EXTENSION_FILE_NAME = "agent-belay.ts"

    private fun extensionDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".pi/agent/extensions")
    }

    private fun extensionFile(): File = File(extensionDir(), EXTENSION_FILE_NAME)

    fun isRegistered(@Suppress("UNUSED_PARAMETER") port: Int): Boolean = extensionFile().exists()

    fun register(port: Int) {
        writeExtension(port)
        piLogger.i { "Registered Pi extension for port $port" }
    }

    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        val file = extensionFile()
        if (file.exists()) {
            file.delete()
            piLogger.i { "Removed Pi extension ${file.absolutePath}" }
        }
    }

    private fun writeExtension(port: Int) {
        val dir = extensionDir()
        dir.mkdirs()
        atomicWrite(extensionFile(), buildExtensionContent(port))
        piLogger.i { "Installed Pi extension at ${extensionFile().absolutePath}" }
    }

    private fun buildExtensionContent(port: Int): String = """
        |// Agent Belay extension for Pi
        |// Auto-generated - do not edit manually. Re-register in Agent Belay to update.
        |// Server: http://localhost:$port
        |
        |import type { ExtensionAPI } from "@mariozechner/pi-coding-agent";
        |
        |const BASE_URL = "http://localhost:$port";
        |const TIMEOUT_MS = 300000; // 5 minutes - matches Agent Belay's default
        |
        |export default function (pi: ExtensionAPI) {
        |  pi.on("tool_call", async (event, ctx) => {
        |    const sessionManager = (ctx as any).sessionManager;
        |    const sessionId =
        |      sessionManager?.getSessionFile?.() ??
        |      sessionManager?.sessionFile ??
        |      `pi-${'$'}{Date.now()}-${'$'}{Math.random().toString(36).slice(2)}`;
        |
        |    const payload = {
        |      toolName: event.toolName,
        |      toolInput: event.input ?? {},
        |      cwd: ctx.cwd ?? "",
        |      sessionId,
        |      timestamp: Date.now(),
        |    };
        |
        |    let response;
        |    try {
        |      response = await fetch(`${'$'}{BASE_URL}/approve-pi`, {
        |        method: "POST",
        |        headers: { "Content-Type": "application/json" },
        |        body: JSON.stringify(payload),
        |        signal: AbortSignal.timeout(TIMEOUT_MS),
        |      });
        |    } catch {
        |      // Agent Belay unreachable - fail open.
        |      return;
        |    }
        |
        |    if (!response.ok) {
        |      // Non-200 - fail open.
        |      return;
        |    }
        |
        |    let result;
        |    try {
        |      result = await response.json();
        |    } catch {
        |      // Invalid Belay response - fail open.
        |      return;
        |    }
        |
        |    if (result.behavior === "deny") {
        |      const reason = result.message ?? "Blocked by Agent Belay";
        |      ctx.ui.notify(reason, "error");
        |      return { block: true, reason };
        |    }
        |    // behavior === "allow" or unrecognized - proceed.
        |  });
        |}
    """.trimMargin()

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
