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
 * The agent re-reads the same file ≥ 3 times without an intervening
 * Edit/Write. Every re-Read returns the same content into context, paying
 * the input price each time. The fix is the read-once hook (or simply
 * remembering what you've already loaded).
 */
class RepeatedReadsDetector(
    private val repeatThreshold: Int = 3,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        // Walk history in order; for each Read, count consecutive reads on
        // the same path with no Edit/Write to that path between them.
        data class State(var reads: Int = 0)
        val states = mutableMapOf<String, State>()
        val flagged = mutableListOf<Pair<String, Int>>()

        for (entry in session.history) {
            val tool = entry.request.hookInput.toolName
            val path = pathFromToolInput(entry.request.hookInput.toolInput) ?: continue
            when (tool) {
                "Read" -> {
                    val state = states.getOrPut(path) { State() }
                    state.reads++
                    if (state.reads == repeatThreshold) {
                        flagged.add(path to state.reads)
                    } else if (state.reads > repeatThreshold) {
                        // Update the count in `flagged`.
                        val idx = flagged.indexOfFirst { it.first == path }
                        if (idx >= 0) flagged[idx] = path to state.reads
                    }
                }
                "Edit", "Write" -> {
                    states.remove(path) // an edit resets the dedup window
                }
            }
        }
        if (flagged.isEmpty()) return emptyList()

        val savedTokens = flagged.sumOf { (_, count) -> (count - 1).toLong() * 1_500L } // ~1.5k per file as a rough mean
        val savedUsd = SavingsMath.tokensToUsd(savedTokens, session)

        return listOf(
            Insight(
                kind = InsightKind.REPEATED_FILE_READS,
                severity = if (flagged.size >= 5) InsightSeverity.WARNING else InsightSeverity.INFO,
                title = "${flagged.size} file(s) re-read without intervening edits",
                description = "Re-Reading a file pulls its full contents back into context every time. " +
                    "Install the read-once hook to dedupe Reads automatically, or use `offset`/`limit` " +
                    "Reads when only a region is needed.",
                evidence = flagged.take(8).map { (path, count) -> "$path · ${count}× Read" },
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

    private fun pathFromToolInput(input: Map<String, *>): String? {
        val v = input["file_path"] ?: input["path"] ?: input["filePath"]
        return (v as? JsonPrimitive)?.contentOrNull
    }
}
