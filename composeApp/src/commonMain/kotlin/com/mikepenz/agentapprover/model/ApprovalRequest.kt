package com.mikepenz.agentapprover.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApprovalRequest(
    val id: String,
    val source: Source,
    val toolName: String,
    val toolType: ToolType,
    val toolInput: JsonObject,
    val sessionId: String,
    val cwd: String,
    val timestamp: Instant,
    val rawRequestJson: String,
)

@Serializable
enum class ToolType { DEFAULT, ASK_USER_QUESTION, PLAN }

@Serializable
enum class Source { CLAUDE_CODE }
