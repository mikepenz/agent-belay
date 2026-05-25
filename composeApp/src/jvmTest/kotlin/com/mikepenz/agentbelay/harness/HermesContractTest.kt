package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.hermes.HermesHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

class HermesContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = HermesHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("hermes/permission_request_bash.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("hermes/permission_request_edit.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("hermes/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("hermes/permission_request_bash.json")

    override fun assertPermissionAllowEnvelope(responseJson: String) {
        val obj = parseFlat(responseJson)
        assertEquals("allow", obj["action"]!!.jsonPrimitive.content)
    }

    override fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String) {
        val obj = parseFlat(responseJson)
        assertEquals("block", obj["action"]!!.jsonPrimitive.content)
        assertEquals(expectedMessage, obj["message"]!!.jsonPrimitive.content)
    }

    override fun assertContainsUpdatedInput(responseJson: String, key: String, value: String) {
        error("Hermes does not support arg rewriting")
    }

    private fun parseFlat(responseJson: String): JsonObject =
        Json.parseToJsonElement(responseJson).jsonObject
}
