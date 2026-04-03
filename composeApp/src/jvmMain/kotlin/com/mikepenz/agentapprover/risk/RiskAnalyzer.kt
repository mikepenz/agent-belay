package com.mikepenz.agentapprover.risk

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.RiskAnalysis

interface RiskAnalyzer {
    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis>
    fun shutdown() {}
}
