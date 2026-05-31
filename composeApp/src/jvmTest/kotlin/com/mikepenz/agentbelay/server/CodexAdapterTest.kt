package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.harness.codex.CodexAdapter
import com.mikepenz.agentbelay.harness.codex.CodexHarness
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Codex CLI wire-shape tests pinned to the **decompiled** Codex 0.135.0 binary
 * (`codex-aarch64-apple-darwin`), not just the published docs. Verified
 * literals from the binary:
 *
 *  - PreToolUse output `permissionDecision` accepts `allow`/`deny`;
 *    `permissionDecision:ask` is rejected (`PreToolUse hook returned
 *    unsupported permissionDecision:ask`).
 *  - PreToolUse honors `updatedInput` only with `permissionDecision:allow`.
 *  - PermissionRequest **rejects** `updatedInput`/`updatedPermissions`
 *    (`PermissionRequest hook returned unsupported updatedInput`), so the
 *    adapter must never emit them on the PermissionRequest path.
 *  - `apply_patch` is the inbound tool name (aliased to Write/Edit), so the
 *    adapter canonicalises it to `Write`.
 */
class CodexAdapterTest {

    private val adapter = CodexAdapter()
    private val json = Json { ignoreUnknownKeys = true }

    private fun bashPermissionRequest() = """
        {"hook_event_name":"PermissionRequest","tool_name":"shell","tool_input":{"command":"ls -la"},"session_id":"codex-1","cwd":"/repo"}
    """.trimIndent()

    @Test
    fun `parses apply_patch and canonicalises to Write`() {
        val req = adapter.parsePermissionRequest(
            """{"hook_event_name":"PermissionRequest","tool_name":"apply_patch","tool_input":{"command":"*** Begin Patch"},"session_id":"codex-1"}""",
        )
        assertNotNull(req)
        assertEquals("Write", req.hookInput.toolName)
        assertEquals(Source.CODEX, req.source)
    }

    @Test
    fun `parses ExitPlanMode as PLAN tool type`() {
        val req = adapter.parsePermissionRequest(
            """{"hook_event_name":"PermissionRequest","tool_name":"ExitPlanMode","tool_input":{},"session_id":"codex-1"}""",
        )
        assertNotNull(req)
        assertEquals(ToolType.PLAN, req.toolType)
    }

    @Test
    fun `malformed payload returns null`() {
        assertNull(adapter.parsePermissionRequest("""{"hook_event_name":"PermissionRequest"}"""))
    }

    @Test
    fun `PermissionRequest allow uses hookSpecificOutput decision behavior allow`() {
        val req = adapter.parsePermissionRequest(bashPermissionRequest())!!
        val obj = json.parseToJsonElement(adapter.buildPermissionAllowResponse(req, updatedInput = null).body).jsonObject
        val hso = obj["hookSpecificOutput"]!!.jsonObject
        assertEquals("PermissionRequest", hso["hookEventName"]!!.jsonPrimitive.content)
        assertEquals("allow", hso["decision"]!!.jsonObject["behavior"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PermissionRequest allow never emits updatedInput (Codex rejects it on this event)`() {
        val req = adapter.parsePermissionRequest(bashPermissionRequest())!!
        val rewritten = mapOf("command" to JsonPrimitive("rm -rf /"))
        val body = adapter.buildPermissionAllowResponse(req, rewritten).body
        // Decompiled binary: "PermissionRequest hook returned unsupported
        // updatedInput" — emitting it would fail closed. Adapter must drop it.
        assertFalse(body.contains("updatedInput"), "PermissionRequest must not carry updatedInput")
        assertFalse(body.contains("rm -rf /"))
    }

    @Test
    fun `PermissionRequest deny carries behavior deny and message`() {
        val req = adapter.parsePermissionRequest(bashPermissionRequest())!!
        val obj = json.parseToJsonElement(adapter.buildPermissionDenyResponse(req, "nope").body).jsonObject
        val decision = obj["hookSpecificOutput"]!!.jsonObject["decision"]!!.jsonObject
        assertEquals("deny", decision["behavior"]!!.jsonPrimitive.content)
        assertEquals("nope", decision["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PreToolUse deny uses permissionDecision deny and reason`() {
        val obj = json.parseToJsonElement(adapter.buildPreToolUseDenyResponse("blocked").body).jsonObject
        val hso = obj["hookSpecificOutput"]!!.jsonObject
        assertEquals("PreToolUse", hso["hookEventName"]!!.jsonPrimitive.content)
        assertEquals("deny", hso["permissionDecision"]!!.jsonPrimitive.content)
        assertEquals("blocked", hso["permissionDecisionReason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `capabilities match the decompiled limits`() {
        val caps = CodexHarness().capabilities
        // Codex rejects updatedInput on PermissionRequest (the path Belay uses),
        // and updatedMCPToolOutput/suppressOutput on PostToolUse.
        assertFalse(caps.supportsArgRewriting)
        assertFalse(caps.supportsOutputRedaction)
    }
}
