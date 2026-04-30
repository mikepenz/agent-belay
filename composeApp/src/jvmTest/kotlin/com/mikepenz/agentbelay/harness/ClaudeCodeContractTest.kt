package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contract-test plug-in for [ClaudeCodeHarness].
 *
 * Claude Code wraps responses under
 * `hookSpecificOutput.decision.{behavior, message, updatedInput, …}` per the
 * docs at https://docs.claude.com/en/docs/claude-code/hooks.
 */
class ClaudeCodeContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = ClaudeCodeHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("claudecode/permission_request_bash_ls.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("claudecode/permission_request_edit_file.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("claudecode/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("claudecode/pre_tool_use_bash.json")

    override fun assertPermissionAllowEnvelope(responseJson: String) {
        val decision = decisionObject(responseJson)
        assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
    }

    override fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String) {
        val decision = decisionObject(responseJson)
        assertEquals("deny", decision["behavior"]!!.jsonPrimitive.content)
        assertEquals(expectedMessage, decision["message"]!!.jsonPrimitive.content)
    }

    override fun assertContainsUpdatedInput(responseJson: String, key: String, value: String) {
        val decision = decisionObject(responseJson)
        val updatedInput = decision["updatedInput"]?.jsonObject
        assertNotNull(updatedInput, "Claude Code allow-with-rewrite must surface updatedInput")
        assertEquals(value, updatedInput[key]!!.jsonPrimitive.content)
    }

    private fun decisionObject(responseJson: String): JsonObject {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val hso = root["hookSpecificOutput"]!!.jsonObject
        assertContains(
            listOf("PermissionRequest"),
            hso["hookEventName"]!!.jsonPrimitive.content,
            "permission allow/deny envelopes must declare PermissionRequest event name",
        )
        return hso["decision"]!!.jsonObject
    }
}
