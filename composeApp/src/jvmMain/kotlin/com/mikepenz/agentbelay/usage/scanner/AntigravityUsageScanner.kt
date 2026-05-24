package com.mikepenz.agentbelay.usage.scanner

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.usage.ScanCursor
import com.mikepenz.agentbelay.usage.UsageScanner

class AntigravityUsageScanner : UsageScanner {
    override val source: Source = Source.ANTIGRAVITY

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        return emptyList()
    }
}
