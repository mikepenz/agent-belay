package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.PermissionSuggestion
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Owns one harness's wire envelope on both ends:
 *
 *  - Parses incoming request JSON into the canonical [ApprovalRequest].
 *  - Builds outgoing responses in the harness's native shape.
 *
 * Each harness has its own envelope conventions (Claude Code wraps under
 * `hookSpecificOutput.decision.behavior`, Copilot CLI emits a flat
 * `{permissionDecision, modifiedArgs}` single-line JSON, Cursor uses
 * `{decision, modifiedInput}`, …) — implementations encapsulate those
 * differences so callers (route handlers, redaction wiring) work in
 * envelope-agnostic terms.
 *
 * Response builders return [HarnessResponse] — a small wrapper around
 * the serialized body plus content type. Today every shipped harness
 * uses already-serialized JSON, but the wrapper reserves room for
 * future harnesses that need a different content type, structured deny
 * metadata, or extra headers (see [HarnessResponse]).
 */
interface HarnessAdapter {
    /** Parses a `PermissionRequest`-style payload (interactive approval). */
    fun parsePermissionRequest(rawJson: String): ApprovalRequest?

    /** Parses a `PreToolUse`-style payload (Protection Engine pre-check). */
    fun parsePreToolUse(rawJson: String): ApprovalRequest?

    /** Allow response with optional rewritten tool args. */
    fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): HarnessResponse

    /** Allow response that asks the harness to persist a list of permission suggestions. */
    fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): HarnessResponse

    /** Deny response with a user-facing reason. */
    fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): HarnessResponse

    /** Pre-tool-use allow (Protection Engine pass). */
    fun buildPreToolUseAllowResponse(): HarnessResponse

    /** Pre-tool-use deny (Protection Engine block). */
    fun buildPreToolUseDenyResponse(reason: String): HarnessResponse

    /**
     * Post-tool-use response that replaces the tool's output. The
     * [updatedOutput] shape must match the original tool's response
     * schema or the harness will reject it (e.g. Bash expects
     * `{stdout, stderr, interrupted, isImage}`).
     *
     * Returns null when the harness does not support output redaction
     * (capability flag off) — callers should pass-through the original
     * response in that case.
     */
    fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse?

    /**
     * Extracts the tool's response payload from a post-tool-use webhook
     * body so the redaction engine can scan it. Each harness sends a
     * different field name — Claude / Codex use `tool_response` (with
     * `tool_output` as a legacy alias); Copilot uses `toolResult`.
     *
     * The default implementation handles the Claude / Codex shape so
     * adapters don't need to override it unless their wire format
     * differs.
     */
    fun extractToolResponse(payload: JsonObject): JsonElement? =
        payload["tool_response"] ?: payload["tool_output"]
}
