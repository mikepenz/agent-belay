package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SavingsMath
import com.mikepenz.agentbelay.insights.SessionMetrics

/**
 * /loop-style auto-runners that drift past 50 turns without converging are
 * a known source of runaway spend, and the longer they run the more likely
 * they straddle the 5-minute prompt-cache TTL — meaning each tick re-pays
 * for the full prefix rather than reading from cache.
 *
 * Direct signals: turn count > 50 plus a falling cache-read ratio across
 * the second half of the session. We compare the cache ratio of the first
 * half to the last quartile.
 */
class LoopWithoutExitDetector(
    private val turnFloor: Int = 50,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        if (session.turnCount < turnFloor) return emptyList()
        val turns = session.turns
        val firstHalf = turns.take(turns.size / 2)
        val lastQuartile = turns.takeLast(turns.size / 4).ifEmpty { return emptyList() }

        val r0 = ratio(firstHalf)
        val r1 = ratio(lastQuartile)
        if (r0 <= 0.0) return emptyList()
        // Cache ratio dropped meaningfully over the second half → TTL miss
        // pattern.
        if (r1 >= r0 - 0.10) return emptyList()

        // Half the cache-eligible input on the dropped tail is the ballpark
        // recoverable spend if the cadence stayed inside the TTL.
        val tailInput = lastQuartile.sumOf { it.inputTokens + it.cacheReadTokens }
        val savedTokens = (tailInput * 0.4).toLong().coerceAtLeast(0)
        val savedUsd = SavingsMath.tokensToUsd(savedTokens, session)

        return listOf(
            Insight(
                kind = InsightKind.LOOP_WITHOUT_EXIT,
                severity = InsightSeverity.INFO,
                title = "Long-running session — cache TTL likely missing",
                description = "Cache-read ratio dropped from ${(r0 * 100).toInt()}% in the first half " +
                    "of the session to ${(r1 * 100).toInt()}% in the last quartile. That pattern usually " +
                    "means the loop interval is past the 5-minute prompt-cache TTL, so each tick re-pays " +
                    "for the full prefix. Tighten the cadence to ≤270s or add a clear termination predicate " +
                    "to the loop prompt.",
                evidence = listOf(
                    "Total turns: ${session.turnCount}",
                    "Cache-read ratio · first half: ${(r0 * 100).toInt()}%",
                    "Cache-read ratio · last quartile: ${(r1 * 100).toInt()}%",
                ),
                savings = EstimatedSavings(
                    tokens = savedTokens,
                    usd = savedUsd?.takeIf { it > 0.005 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = false,
            )
        )
    }

    private fun ratio(turns: List<com.mikepenz.agentbelay.model.UsageRecord>): Double {
        val cache = turns.sumOf { it.cacheReadTokens }
        val input = turns.sumOf { it.inputTokens }
        val denom = cache + input
        if (denom <= 0) return 0.0
        return cache.toDouble() / denom.toDouble()
    }
}
