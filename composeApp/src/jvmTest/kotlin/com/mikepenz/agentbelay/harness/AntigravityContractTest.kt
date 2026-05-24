package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.antigravity.AntigravityHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

class AntigravityContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = AntigravityHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("antigravity/permission_request_bash.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("antigravity/permission_request_edit.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("antigravity/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("antigravity/permission_request_bash.json")

    override fun assertPermissionAllowEnvelope(responseJson: String) {
        val obj = parseFlat(responseJson)
        assertEquals("allow", obj["decision"]!!.jsonPrimitive.content)
    }

    override fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String) {
        val obj = parseFlat(responseJson)
        assertEquals("deny", obj["decision"]!!.jsonPrimitive.content)
        assertEquals(expectedMessage, obj["reason"]!!.jsonPrimitive.content)
    }

    override fun assertContainsUpdatedInput(responseJson: String, key: String, value: String) {
        error("Antigravity does not support arg rewriting")
    }

    private fun parseFlat(responseJson: String): JsonObject =
        Json.parseToJsonElement(responseJson).jsonObject
}
