package com.mikepenz.agentbelay.insights

/**
 * Shared math for converting "tokens saved" → "USD saved" inside detectors.
 *
 * The naive `session.totalCost / session.totalInput` can blow up: a long
 * Claude Code session often has cache_read >> input, so dividing total cost
 * by uncached input alone yields a fictitious per-token rate (an order of
 * magnitude or more above any real model's price). A detector multiplying
 * estimated savings by that rate then claims billions in fake savings on a
 * $130 session.
 *
 * Two safeguards:
 *  1. The blended rate uses *all* token types in the denominator, not just
 *     uncached input.
 *  2. The rate is clamped to a ceiling derived from current model pricing
 *     ($5 per million tokens — roughly Opus output, the most expensive tier
 *     we expect to encounter).
 *  3. Every detector caps its USD claim at half the session's total cost so
 *     no single insight can dominate aggregate optimization headroom.
 */
internal object SavingsMath {

    /**
     * Upper bound for the blended per-token rate. Picked so even if
     * `totalCost` reflects a near-pure-Opus-output session, we don't
     * overshoot reality. Equivalent to ~$5/Mtok.
     */
    private const val MAX_RATE_PER_TOKEN = 5e-6

    /**
     * USD saved if [savedTokens] no longer flowed through the session at the
     * session's blended rate. Returns `null` when there isn't enough signal
     * to compute a meaningful number — the UI hides the chip in that case.
     */
    fun tokensToUsd(savedTokens: Long, session: SessionMetrics): Double? {
        if (savedTokens <= 0 || session.totalCost <= 0) return null
        val totalTokens = session.totalInput +
            session.totalOutput +
            session.totalCacheRead +
            session.totalCacheWrite +
            session.totalReasoning
        if (totalTokens <= 0) return null
        val rate = (session.totalCost / totalTokens.toDouble()).coerceAtMost(MAX_RATE_PER_TOKEN)
        return (savedTokens.toDouble() * rate).let { capPerInsight(it, session) }
    }

    /**
     * Final ceiling applied to every USD savings claim: 50% of the session's
     * total cost. Keeps any single insight from claiming more than the user
     * could plausibly save by acting on it.
     */
    fun capPerInsight(usd: Double, session: SessionMetrics): Double =
        usd.coerceAtMost(session.totalCost * 0.5).coerceAtLeast(0.0)
}
