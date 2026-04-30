package com.mikepenz.agentbelay.testutil

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Test-only HTTP client that mimics how each harness's bridge actually
 * calls Belay's server. The point is to encode the wrapper layer above the
 * adapter — the parts that today only exist as shell-script heredocs in
 * [com.mikepenz.agentbelay.hook.CopilotBridgeInstaller] or as embedded
 * TypeScript inside an OpenCode plugin — so route tests can exercise
 * fail-open / fail-closed / interrupt-on-deny behaviour mechanically
 * instead of by inspection.
 *
 * Each method names the harness it simulates and is a 1:1 translation of
 * what the bridge does on the wire. Behaviour the bridge layer adds on
 * top of HTTP (retry, timeout-as-deny, throws-on-deny) lives here.
 */
class FakeHarnessClient(private val baseUrl: String) : AutoCloseable {

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 1_000
        }
        expectSuccess = false
    }

    override fun close() = http.close()

    /**
     * Claude Code calls Belay directly via its `~/.claude/settings.json`
     * `http` hook entries — no shim. The harness will read whatever the
     * server emits and act on `hookSpecificOutput.decision.behavior`.
     */
    suspend fun claudeCodePost(path: String, body: String): String =
        http.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

    /**
     * Mirrors the Copilot CLI shim under `~/.agent-belay/copilot-*.sh`:
     *
     * ```
     * curl -fsS --data-binary @- "$URL"
     * if [ $? -ne 0 ] && [ "$failClosed" = "1" ]; then
     *   printf '%s' '{"behavior":"deny","message":"agent-belay unreachable"}'
     * fi
     * ```
     *
     * Returns whatever stdout the bridge would emit so route tests can
     * assert the harness-visible contract end-to-end.
     */
    suspend fun copilotShim(path: String, stdin: String, failClosed: Boolean): String = try {
        http.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            setBody(stdin)
        }.bodyAsText()
    } catch (_: Exception) {
        // curl exited non-zero. The bridge picks "" or a synthetic deny
        // depending on failClosed.
        if (failClosed) """{"behavior":"deny","message":"agent-belay unreachable"}"""
        else ""
    }

    /**
     * Mirrors an OpenCode plugin's `tool.execute.before` callback:
     *
     * ```ts
     * const r = await fetch(URL, { method: "POST", body: JSON.stringify(payload) });
     * if (!r.ok) return;                                       // fail-open on 5xx
     * const out = await r.json();
     * if (out.behavior === "deny") throw new Error(out.message);
     * ```
     *
     * Throws [OpenCodeDenied] if the response says deny — exactly what the
     * plugin does to abort a tool call. Silent return means "proceed."
     */
    suspend fun openCodePlugin(path: String, body: String) {
        val response = runCatching {
            http.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
        }.getOrNull() ?: return       // fail-open on any IO error

        val parsed: JsonObject = runCatching {
            Json.parseToJsonElement(response).jsonObject
        }.getOrNull() ?: return       // fail-open on garbage payload

        if (parsed["behavior"]?.jsonPrimitive?.content == "deny") {
            throw OpenCodeDenied(parsed["message"]?.jsonPrimitive?.content ?: "Denied")
        }
    }

    class OpenCodeDenied(message: String) : RuntimeException(message)
}
