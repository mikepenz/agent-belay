package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SavingsMath
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * Long fetch chains where the agent grabs N web pages and pulls them all
 * into context — typical when researching APIs or library docs without
 * landing the findings in a scratch file. We can detect the chain easily;
 * we can't see fetch payload sizes, so the savings number is heuristic.
 */
class WebFetchRunawayDetector(
    private val chainThreshold: Int = 4,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        // Look for ≥ chainThreshold consecutive WebFetch / WebSearch calls
        // with no Edit/Write between them.
        var run = 0
        var maxRun = 0
        var totalChained = 0
        for (entry in session.history) {
            val tool = entry.request.hookInput.toolName
            when (tool) {
                "WebFetch", "WebSearch" -> {
                    run++
                    if (run >= chainThreshold) totalChained++
                    if (run > maxRun) maxRun = run
                }
                "Edit", "Write", "MultiEdit" -> run = 0
            }
        }
        if (maxRun < chainThreshold) return emptyList()

        // ~3.5k tokens per fetched page is a defensible mean for docs sites.
        val approxFetchTokens = (totalChained * 3_500L).coerceAtLeast(0)
        val savedTokens = approxFetchTokens / 2 // half is recoverable via a scratch-file consolidation
        val savedUsd = SavingsMath.tokensToUsd(savedTokens, session)

        return listOf(
            Insight(
                kind = InsightKind.WEB_FETCH_RUNAWAY,
                severity = InsightSeverity.INFO,
                title = "Long fetch chain ($maxRun consecutive WebFetch/Search calls)",
                description = "Each WebFetch dumps the page into the next turn's input. When the chain " +
                    "doesn't land in a file, every subsequent turn re-pays for the full payload. Write " +
                    "intermediate findings to a scratch file so later turns can re-Read just the relevant " +
                    "section, or summarize via a single WebSearch turn before fetching.",
                evidence = listOf(
                    "Longest chain: $maxRun calls",
                    "Estimated chain payload: ${approxFetchTokens / 1000}k tokens",
                ),
                savings = EstimatedSavings(
                    tokens = savedTokens,
                    usd = savedUsd?.takeIf { it > 0.005 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = true,
            )
        )
    }
}
