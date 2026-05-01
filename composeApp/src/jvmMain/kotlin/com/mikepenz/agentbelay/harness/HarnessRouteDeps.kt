package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.redaction.RedactionEngine
import com.mikepenz.agentbelay.state.AppStateManager

/**
 * Bundle of server-side dependencies passed to [Harness.installRoutes].
 * Adding a new dependency here is a single-call-site change instead of
 * widening every harness's `installRoutes` signature.
 */
data class HarnessRouteDeps(
    val stateManager: AppStateManager,
    val protectionEngine: ProtectionEngine,
    val redactionEngine: RedactionEngine,
    val onNewApproval: () -> Unit,
)
