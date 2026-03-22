package com.mikepenz.agentapprover.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalResult(
    val request: ApprovalRequest,
    val decision: Decision,
    val feedback: String? = null,
    val riskAnalysis: RiskAnalysis? = null,
    val rawResponseJson: String? = null,
    val decidedAt: Instant,
)

@Serializable
enum class Decision {
    APPROVED, DENIED, TIMEOUT, AUTO_APPROVED, AUTO_DENIED, ALWAYS_ALLOWED, CANCELLED_BY_CLIENT, RESOLVED_EXTERNALLY
}

@Serializable
data class RiskAnalysis(
    val risk: Int,
    val label: String = "",
    val message: String,
)
