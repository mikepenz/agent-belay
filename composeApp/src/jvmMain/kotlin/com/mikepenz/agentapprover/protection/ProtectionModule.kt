package com.mikepenz.agentapprover.protection

import com.mikepenz.agentapprover.model.ProtectionMode

interface ProtectionModule {
    val id: String
    val name: String
    val description: String
    val corrective: Boolean
    val defaultMode: ProtectionMode
    val applicableTools: Set<String>
    val rules: List<ProtectionRule>
}
