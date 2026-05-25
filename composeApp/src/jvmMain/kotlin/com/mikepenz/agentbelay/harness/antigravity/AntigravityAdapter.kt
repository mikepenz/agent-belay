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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// Antigravity / agy tool names (from jetski hooks_pb HookToolCall.Name)
// mapped to the canonical agent-belay names used by the protection engine
// and UI tool-card renderers.
private val TOOL_NAME_MAP = mapOf(
    "run_command" to "Bash",
    "bash" to "Bash",
    "edit_file" to "Edit",
    "edit" to "Edit",
    "view_file" to "Read",
    "view" to "Read",
    "read_file" to "Read",
    "create_file" to "Write",
    "create" to "Write",
    "write_file" to "Write",
)

class AntigravityAdapter : HarnessAdapter {
    private val logger = Logger.withTag("AntigravityAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    /**
     * Parses Antigravity (`agy`) JSON hook payloads.
     *
     * The native Antigravity wire format is derived from the jetski
     * `PreToolHookArgs` / `PostToolHookArgs` protos and uses:
     *   - `tool_call`: `{ "name": "...", "args": "<json-string-or-obj>" }`
     *   - top-level `conversation_id`, `cwd`, `workspace_paths`, `step_idx`,
     *     `artifact_directory_path`, `transcript_path`
     *
     * `agy` does NOT emit Claude-Code-style `tool_name` / `tool_input` /
     * `hook_event_name` / `session_id`. This parser accepts the native
     * shape and, as a transitional fallback, the legacy Claude-shaped
     * keys (used by older fixtures and the sed-injection in the bridge
     * script).
     */
    private fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject

            // Native Antigravity shape: tool_call: { name, args }
            val toolCall = root["tool_call"]?.jsonObject
            val nativeToolName = toolCall?.get("name")?.jsonPrimitive?.contentOrNull
            val nativeToolInput = toolCall?.let { extractToolArgs(it) }

            // Legacy / Claude-shaped shape (kept for fixtures + sed
            // injection in the bridge script before Antigravity parity).
            val legacyToolName = root["tool_name"]?.jsonPrimitive?.contentOrNull
            val legacyToolInput = root["tool_input"]?.jsonObject?.toMap() ?: emptyMap()

            val toolName = nativeToolName?.takeIf { it.isNotBlank() }
                ?: legacyToolName?.takeIf { it.isNotBlank() }
                ?: run {
                    logger.w { "Missing tool_call.name / tool_name in Antigravity payload" }
                    return null
                }
            val toolInput = nativeToolInput ?: legacyToolInput

            val sessionId =
                root["conversation_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: root["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: System.getenv("ANTIGRAVITY_SESSION_ID")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("GEMINI_SESSION_ID")?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString()

            val canonicalToolName = TOOL_NAME_MAP[toolName] ?: toolName

            val toolType = when (canonicalToolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan", "ExitPlanMode" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            val hookEventName = root["hook_event_name"]?.jsonPrimitive?.contentOrNull ?: "PreToolUse"
            val cwd = root["cwd"]?.jsonPrimitive?.contentOrNull ?: ""

            val hookInput = HookInput(
                sessionId = sessionId,
                toolName = canonicalToolName,
                toolInput = toolInput,
                hookEventName = hookEventName,
                cwd = cwd,
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

    // Antigravity may serialize HookToolCall.args either as a JSON object
    // (preferred for new tools) or as a serialized JSON string under
    // `tool_call_json` (legacy proto path). Accept both.
    private fun extractToolArgs(toolCall: JsonObject): Map<String, JsonElement> {
        toolCall["args"]?.let { argsEl ->
            (argsEl as? JsonObject)?.let { return it.toMap() }
            (argsEl.jsonPrimitive.contentOrNull)?.let { s ->
                runCatching { json.parseToJsonElement(s).jsonObject.toMap() }
                    .getOrNull()
                    ?.let { return it }
            }
        }
        toolCall["tool_call_json"]?.jsonPrimitive?.contentOrNull?.let { s ->
            runCatching { json.parseToJsonElement(s).jsonObject.toMap() }
                .getOrNull()
                ?.let { return it }
        }
        return emptyMap()
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
    ): HarnessResponse = HarnessResponse(buildDenyJson(message))

    override fun buildPreToolUseAllowResponse(): HarnessResponse = HarnessResponse(
        buildJsonObject {
            put("decision", "allow")
            put("allow_tool", true)
        }.toString()
    )

    override fun buildPreToolUseDenyResponse(reason: String): HarnessResponse =
        HarnessResponse(buildDenyJson(reason))

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse? = null

    // PreToolHookResult proto fields: decision (string), reason, deny_reason,
    // allow_tool (bool). agy's Decision enum strings include both "deny" and
    // "block"; emit "deny" (matches the proto's deny_reason naming) plus
    // deny_reason for parity with the upstream proto schema.
    private fun buildDenyJson(reason: String): String = buildJsonObject {
        put("decision", "deny")
        put("reason", reason)
        put("deny_reason", reason)
        put("allow_tool", false)
    }.toString()
}
