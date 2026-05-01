package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SavingsMath
import com.mikepenz.agentbelay.insights.SessionMetrics
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Surfaces Bash invocations whose output is likely to flood context (npm
 * install, cargo build, full log tails, unfiltered find). We don't have the
 * actual output size in `usage_records`; this detector triggers on
 * *commands* that empirically produce massive output, then recommends
 * head/tail/grep retrofits. The size estimate is heuristic.
 */
class BashFloodingDetector(
    private val minMatches: Int = 3,
) : InsightDetector {
    private data class Pattern(val regex: Regex, val rewriteHint: String, val approxTokens: Long)

    private val patterns = listOf(
        Pattern(Regex("""(?i)\bnpm\s+(install|i|ci)\b"""), "redirect to a log file then Read with offset", 4_000),
        Pattern(Regex("""(?i)\bcargo\s+(build|test)\b"""), "pipe through `2>&1 | tail -n 100`", 3_500),
        Pattern(Regex("""(?i)\bgradle\s+\S*build\S*"""), "pipe through `2>&1 | tail -n 100`", 5_000),
        Pattern(Regex("""(?i)\bfind\s+\S+(?!\s.*-quit\b)"""), "scope to a subtree and `| head -n 50`", 2_500),
        Pattern(Regex("""(?i)\bgit\s+log\b(?!\s+-n\b|\s+--oneline)"""), "add `-n 30 --oneline`", 1_500),
        Pattern(Regex("""(?i)\b(?:cat|less)\s+\S+\.log"""), "pipe through `| tail -n 200` or grep for errors", 3_000),
        Pattern(Regex("""(?i)\b(curl|wget)\s+(?:-s\s+)?https?://"""), "pipe through `| jq '<path>'` or save to file", 2_000),
    )

    override fun detect(session: SessionMetrics): List<Insight> {
        val matches = mutableListOf<Pair<String, Pattern>>()
        for (h in session.calls("Bash")) {
            val cmd = (h.request.hookInput.toolInput["command"] as? JsonPrimitive)?.contentOrNull
                ?: continue
            patterns.firstOrNull { it.regex.containsMatchIn(cmd) }?.let { p ->
                matches.add(cmd to p)
            }
        }
        if (matches.size < minMatches) return emptyList()

        val savedTokens = matches.sumOf { (_, p) -> p.approxTokens }
        val savedUsd = SavingsMath.tokensToUsd(savedTokens, session)

        val examples = matches.take(5).map { (cmd, p) ->
            val truncated = if (cmd.length > 80) cmd.substring(0, 77) + "…" else cmd
            "$truncated  →  ${p.rewriteHint}"
        }

        return listOf(
            Insight(
                kind = InsightKind.BASH_OUTPUT_FLOODING,
                severity = if (matches.size >= 8) InsightSeverity.WARNING else InsightSeverity.INFO,
                title = "${matches.size} Bash commands likely to flood context",
                description = "Long Bash output goes straight back into the agent's input on the next " +
                    "turn. Filtering at the shell — `tail`, `grep`, `jq`, redirect-to-file — keeps the " +
                    "useful bits and drops the rest. A repo-wide Bash output limit hook can enforce this.",
                evidence = examples,
                savings = EstimatedSavings(
                    tokens = savedTokens,
                    usd = savedUsd?.takeIf { it > 0.005 },
                ),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = true, // LLM can rewrite the *exact* command
            )
        )
    }
}
