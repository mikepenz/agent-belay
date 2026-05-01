package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.insights.InsightFixtures.bash
import com.mikepenz.agentbelay.insights.InsightFixtures.session
import com.mikepenz.agentbelay.insights.InsightFixtures.turn
import com.mikepenz.agentbelay.insights.detectors.BashFloodingDetector
import com.mikepenz.agentbelay.insights.detectors.LoopWithoutExitDetector
import com.mikepenz.agentbelay.insights.detectors.RepeatedReadsDetector
import com.mikepenz.agentbelay.insights.detectors.SubagentOverspawnDetector
import com.mikepenz.agentbelay.insights.detectors.WebFetchRunawayDetector
import com.mikepenz.agentbelay.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the savings math.
 *
 * Bug witnessed in the wild: a $131 Claude Code session with heavy cache
 * reads but small uncached input had the BashFloodingDetector report
 * ~$2,865 of estimated savings — far more than the session cost. Root
 * cause: detectors used `totalCost / totalInput` as a per-token rate,
 * which inflates wildly when most tokens are cache reads (i.e. cheap)
 * but cost is still concentrated on a few input tokens.
 *
 * Lock-in: every USD claim must stay <= 50% of the session's total cost,
 * regardless of token mix.
 */
class SavingsMathTest {

    /**
     * Reproduces the user-reported case: long Claude Code session, $131
     * total, 35 Bash commands hitting flooding patterns, and a token mix
     * dominated by cache reads. Verifies the savings claim is bounded.
     */
    @Test
    fun bashFlooding_savings_never_exceed_half_total_cost_on_heavy_cache_session() {
        // Synthesize 80 turns: 5k input each + 200k cache_read each + 200 output.
        // Total input = 400k, cache_read = 16M, cost = $131.
        // Old formula: 35 commands × ~3000 tokens × ($131 / 400k) = 35 × 3000 × 3.275e-4 ≈ $34, but on a real session with even smaller `totalInput` it climbs into thousands.
        // We model the worst case: most input is cached, very small uncached input.
        val turns = (0 until 80).map {
            turn(input = 200, cacheRead = 200_000, cacheWrite = 0, output = 200, cost = 131.0 / 80)
        }
        val history = (0 until 35).map { i ->
            bash("npm install pkg-$i", offsetMillis = i * 1_000L)
        }
        val s = session(turns = turns, history = history)
        assertTrue(s.totalCost > 130.0 && s.totalCost < 132.0, "fixture session must be ~\$131")
        // OLD-formula sanity: totalInput is tiny, so the broken rate would be huge.
        val oldRate = s.totalCost / (s.totalInput.toDouble() + 1)
        assertTrue(oldRate > 0.005, "broken rate must be high in this fixture")

        val insights = BashFloodingDetector().detect(s)
        assertEquals(1, insights.size)
        val insight = insights.first()
        val claimedUsd = insight.savings.usd
        assertNotNull(claimedUsd)
        assertTrue(
            claimedUsd <= s.totalCost * 0.5 + 0.001,
            "savings must be capped at 50% of \$${s.totalCost}; was \$$claimedUsd",
        )
    }

    @Test
    fun savingsMath_returns_null_when_session_has_no_cost() {
        val turns = listOf(turn(input = 1_000, output = 100, cost = 0.0))
        val s = session(turns)
        assertEquals(null, SavingsMath.tokensToUsd(50_000, s))
    }

    @Test
    fun savingsMath_clamps_per_token_rate_to_5e6() {
        // 1k tokens for a $50 session would be $0.05/token if computed naively.
        // With the clamp we should land at 1000 * 5e-6 = $0.005, then capped
        // at 50% of $50 = $25 — but the clamp wins here.
        val turns = listOf(turn(input = 500, output = 500, cost = 50.0))
        val s = session(turns)
        val saved = SavingsMath.tokensToUsd(1_000L, s) ?: error("expected non-null")
        assertTrue(saved <= 0.006, "per-token rate clamp should cap at ~\$0.005 for 1k tokens; was $saved")
    }

    @Test
    fun savingsMath_caps_at_half_session_cost() {
        // If the rate × tokens product is huge, we still cap at 50% of cost.
        val turns = listOf(turn(input = 1_000_000, output = 1_000_000, cost = 10.0))
        val s = session(turns)
        // 1M tokens at $5e-6/token ≈ $5 — under the cap.
        val small = SavingsMath.tokensToUsd(1_000_000L, s) ?: error("non-null")
        assertTrue(small <= 5.01)
        // 100M tokens hits the cap at 50% of $10 = $5.
        val capped = SavingsMath.tokensToUsd(100_000_000L, s) ?: error("non-null")
        assertEquals(5.0, capped, 0.001)
    }

    /**
     * Smoke-test the other four migrated detectors: each in a fixture with a
     * heavy cache-read session, and assert their USD claim stays bounded.
     */
    @Test
    fun all_migrated_detectors_respect_the_cap() {
        // Build a session that triggers RepeatedReads / Subagent / WebFetch /
        // Loop simultaneously, with a heavy-cache mix.
        val baseCost = 100.0
        val turns = (0 until 80).map { i ->
            val cacheBoost = if (i < 60) 50_000L else 500L // ratio drops for the loop detector
            turn(
                input = 100, cacheRead = cacheBoost, output = 200,
                cost = baseCost / 80, offsetMillis = i * 1_000L,
            )
        }
        val history = buildList {
            // RepeatedReads
            repeat(5) { add(InsightFixtures.read("/x.kt", offsetMillis = it * 1_000L)) }
            // Subagent
            repeat(5) { add(InsightFixtures.task(offsetMillis = 10_000L + it * 1_000L)) }
            // WebFetch chain
            repeat(6) { add(InsightFixtures.approval("WebFetch", offsetMillis = 20_000L + it * 1_000L)) }
        }
        val s = session(turns = turns, history = history, harness = Source.CLAUDE_CODE)

        val cap = s.totalCost * 0.5 + 0.001
        for (detector in listOf(
            RepeatedReadsDetector(),
            SubagentOverspawnDetector(),
            WebFetchRunawayDetector(),
            LoopWithoutExitDetector(),
        )) {
            for (insight in detector.detect(s)) {
                val claim = insight.savings.usd ?: continue
                assertTrue(
                    claim <= cap,
                    "${detector::class.simpleName} claimed \$$claim on a \$${s.totalCost} session",
                )
            }
        }
    }
}
