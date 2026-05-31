package com.mikepenz.agentbelay.harness.hermes

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessResponse
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class HermesAdapter : HarnessAdapter {

    private val logger = Logger.withTag("HermesAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    private fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val raw = json.decodeFromString<HookInput>(rawJson)

            if (raw.sessionId.isBlank()) {
                logger.w { "Missing session_id" }
                return null
            }
            if (raw.toolName.isBlank()) {
                logger.w { "Missing tool_name" }
                return null
            }

            // Hermes' built-in tool vocabulary (per the official hooks docs):
            // terminal, web_search, read_file, write_file, patch, send_message,
            // delegate_task. Map the ones with a canonical Belay equivalent so
            // the Protection Engine, UI tool cards, and history treat them like
            // their Claude-named counterparts. The shell tool is `terminal`
            // (NOT `bash`) — mapping `bash` was dead code that never matched.
            val normalisedToolName = when (raw.toolName) {
                "terminal" -> "Bash"
                "write_file", "patch" -> "Write"
                "read_file" -> "Read"
                else -> raw.toolName
            }
            val hookInput = if (normalisedToolName == raw.toolName) raw else raw.copy(toolName = normalisedToolName)

            val toolType = when (hookInput.toolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.HERMES,
                toolType = toolType,
                hookInput = hookInput,
                timestamp = Clock.System.now(),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse Hermes hook JSON", e)
            null
        }
    }

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("action", "allow")
    }.toString())

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): HarnessResponse = buildPermissionAllowResponse(request, updatedInput = null)

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("action", "block")
        put("message", message)
    }.toString())

    override fun buildPermissionDeferResponse(request: ApprovalRequest): HarnessResponse =
        HarnessResponse("{}")

    override fun buildPreToolUseAllowResponse(): HarnessResponse = HarnessResponse("{}")

    override fun buildPreToolUseDenyResponse(reason: String): HarnessResponse = HarnessResponse(buildJsonObject {
        put("action", "block")
        put("message", reason)
    }.toString())

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse? = null
}
