package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.harness.hermes.HermesAdapter
import com.mikepenz.agentbelay.harness.hermes.HermesHarness
import com.mikepenz.agentbelay.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Hermes Agent wire-shape tests pinned to the **decompiled** Hermes source
 * (`hermes-agent 2026.5.29.2`, pkg `0.15.2`):
 *
 *  - `agent/shell_hooks.py` `_serialize_payload`: stdin carries
 *    `hook_event_name`, `tool_name`, `tool_input` (from `args`), `session_id`,
 *    `cwd`, `extra`.
 *  - Built-in tools (`toolsets.py`): the shell tool is `terminal` (NOT
 *    `bash`); others include `write_file`, `patch`, `read_file`.
 *  - `_parse_response`: block via `{"action":"block","message"}` OR
 *    `{"decision":"block","reason"}`; allow = any non-block / `None`.
 */
class HermesAdapterTest {

    private val adapter = HermesAdapter()
    private val json = Json { ignoreUnknownKeys = true }

    private fun req(toolName: String) =
        """{"hook_event_name":"pre_tool_call","tool_name":"$toolName","tool_input":{"command":"echo hi"},"session_id":"h-1","cwd":"/repo"}"""

    @Test
    fun `terminal canonicalises to Bash`() {
        val r = adapter.parsePermissionRequest(req("terminal"))
        assertNotNull(r)
        assertEquals("Bash", r.hookInput.toolName)
        assertEquals(Source.HERMES, r.source)
        assertEquals("h-1", r.hookInput.sessionId)
        assertEquals("/repo", r.hookInput.cwd)
    }

    @Test
    fun `write_file and patch canonicalise to Write`() {
        assertEquals("Write", adapter.parsePermissionRequest(req("write_file"))!!.hookInput.toolName)
        assertEquals("Write", adapter.parsePermissionRequest(req("patch"))!!.hookInput.toolName)
    }

    @Test
    fun `read_file canonicalises to Read`() {
        assertEquals("Read", adapter.parsePermissionRequest(req("read_file"))!!.hookInput.toolName)
    }

    @Test
    fun `bash is NOT a Hermes tool and passes through unmapped`() {
        // The shell tool is `terminal`; a literal `bash` would never be sent,
        // and must not be silently treated as Bash.
        assertEquals("bash", adapter.parsePermissionRequest(req("bash"))!!.hookInput.toolName)
    }

    @Test
    fun `malformed payload returns null`() {
        assertNull(adapter.parsePermissionRequest("""{"hook_event_name":"pre_tool_call"}"""))
    }

    @Test
    fun `deny emits Hermes-canonical action block plus message`() {
        val r = adapter.parsePermissionRequest(req("terminal"))!!
        val obj = json.parseToJsonElement(adapter.buildPermissionDenyResponse(r, "forbidden").body).jsonObject
        assertEquals("block", obj["action"]!!.jsonPrimitive.content)
        assertEquals("forbidden", obj["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `allow is a non-block envelope`() {
        val r = adapter.parsePermissionRequest(req("terminal"))!!
        val obj = json.parseToJsonElement(adapter.buildPermissionAllowResponse(r, updatedInput = null).body).jsonObject
        // Hermes only recognises action=="block"; anything else allows.
        assertFalse(obj["action"]?.jsonPrimitive?.content == "block")
    }

    @Test
    fun `preToolUse deny short-circuits the call only (not an agent interrupt)`() {
        // Decompiled: a pre_tool_call block returns `message` as the tool's
        // error; the agent loop continues. So interrupt-on-deny is false.
        assertFalse(HermesHarness().capabilities.supportsInterruptOnDeny)
    }
}
