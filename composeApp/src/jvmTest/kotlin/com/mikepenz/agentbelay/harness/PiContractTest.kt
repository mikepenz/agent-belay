package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.pi.PiHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

class PiContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = PiHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("pi/permission_request_bash.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("pi/permission_request_edit.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("pi/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("pi/permission_request_bash.json")

    override fun assertPermissionAllowEnvelope(responseJson: String) {
        val obj = parseFlat(responseJson)
        assertEquals("allow", obj["behavior"]!!.jsonPrimitive.content)
    }

    override fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String) {
        val obj = parseFlat(responseJson)
        assertEquals("deny", obj["behavior"]!!.jsonPrimitive.content)
        assertEquals(expectedMessage, obj["message"]!!.jsonPrimitive.content)
    }

    override fun assertContainsUpdatedInput(responseJson: String, key: String, value: String) {
        error("Pi does not support arg rewriting")
    }

    private fun parseFlat(responseJson: String): JsonObject =
        Json.parseToJsonElement(responseJson).jsonObject
}
