package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SavingsMath
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Each Claude Code Task subagent loads its own ~20k-token bootstrap, so
 * sessions that fan out into 3+ subagents pay the bootstrap cost N times.
 * Common antipattern: spawning a subagent for a 1-shot Grep that would have
 * cost a few hundred tokens inline.
 *
 * We can't directly attribute subagent token cost from `usage_records`
 * alone (the JSONL doesn't tag turns by spawn-id), so the detector keys off
 * the count of `Task` invocations in `history` — easy to compute, hard to
 * over-trigger.
 */
class SubagentOverspawnDetector(
    private val taskCallThreshold: Int = 3,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        val taskCalls = session.calls("Task")
        if (taskCalls.size < taskCallThreshold) return emptyList()

        // Each Task call carries ~20k bootstrap. That's the ballpark estimate.
        val bootstrapTokens = 20_000L * taskCalls.size
        val savedUsd = SavingsMath.tokensToUsd(bootstrapTokens, session)

        return listOf(
            Insight(
                kind = InsightKind.SUBAGENT_OVERSPAWN,
                severity = if (taskCalls.size >= 6) InsightSeverity.WARNING else InsightSeverity.INFO,
                title = "${taskCalls.size} Task subagents spawned",
                description = "Each Task call bootstraps a fresh ~20k-token context. For short lookups " +
                    "(<50 lines of search output) inline `Grep` / `Read` is dramatically cheaper. Reserve " +
                    "Task for genuinely parallel research that returns a summary you'd otherwise have to " +
                    "compose by hand.",
                evidence = listOf(
                    "Task invocations: ${taskCalls.size}",
                    "Approx. bootstrap overhead: ~${bootstrapTokens / 1000}k tokens",
                ),
                savings = EstimatedSavings(
                    tokens = bootstrapTokens,
                    usd = savedUsd?.takeIf { it > 0.01 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }
}
