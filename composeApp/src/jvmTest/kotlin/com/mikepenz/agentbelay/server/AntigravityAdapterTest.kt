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
    fun parsePreToolUseRequest() {
        val json = """{"hook_event_name":"PreToolUse","tool_name":"bash","tool_input":{"command":"npm test"},"session_id":"sess1"}"""
        val result = adapter.parsePreToolUse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals(Source.ANTIGRAVITY, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("sess1", result.hookInput.sessionId)
        assertEquals("PreToolUse", result.hookInput.hookEventName)
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parseMalformedRequestReturnsNull() {
        val json = """{"hook_event_name":"PreToolUse","tool_input":{"command":"ls"}}"""
        assertNull(adapter.parsePreToolUse(json))
    }

    @Test
    fun buildPreToolUseAllowResponseShape() {
        val response = adapter.buildPreToolUseAllowResponse()
        val obj = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("allow", obj["decision"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildPreToolUseDenyResponseShape() {
        val response = adapter.buildPreToolUseDenyResponse("blocked by rule")
        val obj = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("deny", obj["decision"]!!.jsonPrimitive.content)
        assertEquals("blocked by rule", obj["reason"]!!.jsonPrimitive.content)
    }
}
