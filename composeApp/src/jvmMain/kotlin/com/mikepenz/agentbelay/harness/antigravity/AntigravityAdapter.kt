package com.mikepenz.agentbelay.harness.antigravity

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessResponse
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import java.util.UUID
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val TOOL_NAME_MAP = mapOf(
    "bash" to "Bash",
    "edit" to "Edit",
    "view" to "Read",
    "create" to "Write",
)

class AntigravityAdapter : HarnessAdapter {
    private val logger = Logger.withTag("AntigravityAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    private fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val payload = json.decodeFromString<AntigravityPayload>(rawJson)
            if (payload.toolName.isBlank()) {
                logger.w { "Missing tool_name" }
                return null
            }

            val sessionId = payload.sessionId.takeIf { it.isNotBlank() }
                ?: System.getenv("ANTIGRAVITY_SESSION_ID")?.takeIf { it.isNotBlank() }
                ?: System.getenv("GEMINI_SESSION_ID")?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()

            val canonicalToolName = TOOL_NAME_MAP[payload.toolName] ?: payload.toolName

            val toolType = when (canonicalToolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan", "ExitPlanMode" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            val hookInput = HookInput(
                sessionId = sessionId,
                toolName = canonicalToolName,
                toolInput = payload.toolInput,
                hookEventName = payload.hookEventName,
            )

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.ANTIGRAVITY,
                toolType = toolType,
                hookInput = hookInput,
                timestamp = Clock.System.now(),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse Antigravity hook JSON", e)
            null
        }
    }

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): HarnessResponse = HarnessResponse(
        buildJsonObject {
            put("decision", "allow")
        }.toString()
    )

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): HarnessResponse = buildPermissionAllowResponse(request, updatedInput = null)

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): HarnessResponse = HarnessResponse(
        buildJsonObject {
            put("decision", "deny")
            put("reason", message)
        }.toString()
    )

    override fun buildPreToolUseAllowResponse(): HarnessResponse = HarnessResponse(
        buildJsonObject {
            put("decision", "allow")
        }.toString()
    )

    override fun buildPreToolUseDenyResponse(reason: String): HarnessResponse = HarnessResponse(
        buildJsonObject {
            put("decision", "deny")
            put("reason", reason)
        }.toString()
    )

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse? = null
}

@Serializable
private data class AntigravityPayload(
    @SerialName("hook_event_name") val hookEventName: String = "",
    @SerialName("tool_name") val toolName: String = "",
    @SerialName("tool_input") val toolInput: Map<String, JsonElement> = emptyMap(),
    @SerialName("session_id") val sessionId: String = "",
    val timestamp: String? = null,
)
