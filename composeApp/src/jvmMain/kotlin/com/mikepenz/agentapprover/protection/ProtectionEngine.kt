package com.mikepenz.agentapprover.protection

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.model.ProtectionSettings

class ProtectionEngine(
    val modules: List<ProtectionModule>,
    private val settingsProvider: () -> ProtectionSettings,
) {
    fun evaluate(hookInput: HookInput): List<ProtectionHit> {
        val settings = settingsProvider()
        val hits = mutableListOf<ProtectionHit>()
        for (module in modules) {
            val moduleSettings = settings.modules[module.id]
            val mode = moduleSettings?.mode ?: module.defaultMode
            if (mode == ProtectionMode.DISABLED) continue
            if (hookInput.toolName !in module.applicableTools) continue
            val disabledRules = moduleSettings?.disabledRules ?: emptySet()
            for (rule in module.rules) {
                if (rule.id in disabledRules) continue
                val hit = rule.evaluate(hookInput)
                if (hit != null) {
                    hits.add(hit.copy(mode = mode))
                }
            }
        }
        return hits
    }

    fun highestSeverity(hits: List<ProtectionHit>): ProtectionMode {
        return hits.minByOrNull { it.mode.ordinal }?.mode ?: ProtectionMode.DISABLED
    }
}
