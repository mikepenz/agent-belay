package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.harness.antigravity.AntigravityAdapter
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AntigravityAdapterTest {

    private val adapter = AntigravityAdapter()

    @Test
    fun parsesNativePreToolUseShape() {
        // Native jetski PreToolHookArgs JSON (no tool_name/tool_input/session_id).
        val json = """{
            "conversation_id":"sess1",
            "cwd":"/repo",
            "step_idx":2,
            "tool_call":{"name":"run_command","args":{"command":"npm test"}}
        }"""
        val result = adapter.parsePreToolUse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals(Source.ANTIGRAVITY, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("sess1", result.hookInput.sessionId)
        assertEquals("/repo", result.hookInput.cwd)
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parsesNativeShapeWithToolCallJsonStringArgs() {
        // Some agy paths serialize HookToolCall.args as an escaped JSON string.
        val json = """{"conversation_id":"sess","tool_call":{"name":"edit_file","args":"{\"path\":\"a.kt\",\"content\":\"x\"}"}}"""
        val result = adapter.parsePreToolUse(json)
        assertNotNull(result)
        assertEquals("Edit", result.hookInput.toolName)
        assertEquals(JsonPrimitive("a.kt"), result.hookInput.toolInput["path"])
    }

    @Test
    fun parsesLegacyClaudeShapeForTransitionalCompatibility() {
        // The bridge script injects session_id via sed for back-compat;
        // also covers older fixtures that used Claude Code field names.
        val json = """{"hook_event_name":"PreToolUse","tool_name":"bash","tool_input":{"command":"npm test"},"session_id":"sess1"}"""
        val result = adapter.parsePreToolUse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals("sess1", result.hookInput.sessionId)
    }

    @Test
    fun parseMalformedRequestReturnsNull() {
        val json = """{"conversation_id":"sess","tool_call":{}}"""
        assertNull(adapter.parsePreToolUse(json))
    }

    @Test
    fun parsePayloadWithoutSessionIdSucceeds() {
        val json = """{"tool_call":{"name":"run_command","args":{"command":"echo hi"}}}"""
        val result = adapter.parsePreToolUse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertTrue(result.hookInput.sessionId.isNotEmpty())
    }

    @Test
    fun buildPreToolUseAllowResponseShape() {
        val response = adapter.buildPreToolUseAllowResponse()
        val obj = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("allow", obj["decision"]!!.jsonPrimitive.content)
        assertEquals("true", obj["allow_tool"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildPreToolUseDenyResponseShape() {
        val response = adapter.buildPreToolUseDenyResponse("blocked by rule")
        val obj = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("deny", obj["decision"]!!.jsonPrimitive.content)
        assertEquals("blocked by rule", obj["reason"]!!.jsonPrimitive.content)
        assertEquals("blocked by rule", obj["deny_reason"]!!.jsonPrimitive.content)
    }
}
