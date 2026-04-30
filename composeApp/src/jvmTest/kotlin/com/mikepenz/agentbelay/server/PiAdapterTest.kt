package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.harness.pi.PiAdapter
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

class PiAdapterTest {

    private val adapter = PiAdapter()

    @Test
    fun parseBashRequest() {
        val json = """{"toolName":"bash","toolInput":{"command":"npm test"},"cwd":"/tmp","sessionId":"s1","timestamp":1704614600000}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals("/tmp", result.hookInput.cwd)
        assertEquals(Source.PI, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("s1", result.hookInput.sessionId)
        assertEquals("PermissionRequest", result.hookInput.hookEventName)
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parseEditRequest() {
        val json = """{"toolName":"edit","toolInput":{"path":"src/main.kt","content":"hello"},"cwd":"/project","sessionId":"s2"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Edit", result.hookInput.toolName)
        assertEquals(JsonPrimitive("src/main.kt"), result.hookInput.toolInput["path"])
    }

    @Test
    fun parseReadRequest() {
        val json = """{"toolName":"read","toolInput":{"path":"README.md"},"cwd":"/home"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Read", result.hookInput.toolName)
    }

    @Test
    fun unknownToolNamePassedThrough() {
        val json = """{"toolName":"custom_tool","toolInput":{"arg":"val"},"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("custom_tool", result.hookInput.toolName)
    }

    @Test
    fun missingToolNameReturnsNull() {
        val json = """{"toolInput":{"command":"test"},"cwd":"/tmp"}"""
        assertNull(adapter.parse(json))
    }

    @Test
    fun missingSessionIdGeneratesUUID() {
        val json = """{"toolName":"bash","toolInput":{"command":"ls"},"cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertTrue(result.hookInput.sessionId.isNotBlank())
    }

    @Test
    fun snakeCaseFieldsAlsoWork() {
        val json = """{"tool_name":"bash","tool_input":{"command":"echo hi"},"cwd":"/x","session_id":"abc"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals("abc", result.hookInput.sessionId)
        assertEquals(JsonPrimitive("echo hi"), result.hookInput.toolInput["command"])
    }

    @Test
    fun denyResponseCarriesMessage() {
        val request = adapter.parse("""{"toolName":"bash","toolInput":{"command":"rm -rf ."}}""")
        assertNotNull(request)

        val response = adapter.buildPermissionDenyResponse(request, "blocked")
        val obj = Json.parseToJsonElement(response.body).jsonObject

        assertEquals("deny", obj["behavior"]!!.jsonPrimitive.content)
        assertEquals("blocked", obj["message"]!!.jsonPrimitive.content)
    }
}
