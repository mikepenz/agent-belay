package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.copilot.CopilotHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contract-test plug-in for [CopilotHarness].
 *
 * Copilot CLI emits a flat `{behavior, message, modifiedArgs?, interrupt?}`
 * single-line JSON for permissionRequest responses (per
 * `@github/copilot` v1.0.16+ SDK type definitions). The preToolUse response
 * is the separate `{permissionDecision, permissionDecisionReason}` shape;
 * we don't assert that here — [HarnessCapabilitiesTest] already covers it.
 */
class CopilotContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = CopilotHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("copilot/permission_request_v1_0_22_snake_case.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("copilot/permission_request_v1_0_16_camelcase.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("copilot/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("copilot/pre_tool_use_legacy_toolargs_string.json")

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
        val obj = parseFlat(responseJson)
        val modified = obj["modifiedArgs"]?.jsonObject
        assertNotNull(modified, "Copilot allow-with-rewrite must surface modifiedArgs")
        assertEquals(value, modified[key]!!.jsonPrimitive.content)
    }

    private fun parseFlat(responseJson: String): JsonObject =
        Json.parseToJsonElement(responseJson).jsonObject
}
