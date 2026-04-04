package com.mikepenz.agentapprover.protection

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit

interface ProtectionRule {
    val id: String
    val name: String
    val description: String
    fun evaluate(hookInput: HookInput): ProtectionHit?
}
