package com.mikepenz.agentbelay.insights.detectors

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightDetector
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.SessionMetrics
import com.mikepenz.agentbelay.model.Source
import java.io.File

/**
 * Flags sessions whose first-turn input is ≥ 8k above the harness baseline,
 * AND whose project `CLAUDE.md` (or `AGENTS.md` for Codex) is heavyweight
 * on disk. Most of that bloat lands in every cached prefix, so a one-time
 * trim pays back across every future session in that project.
 *
 * The on-disk lookup is best-effort and happens in JVM, not in tests — the
 * `cwdFileResolver` seam lets tests inject a fake.
 */
class ClaudeMdBloatDetector(
    private val excessThreshold: Long = 8_000L,
    private val maxLines: Int = 200,
    private val cwdFileResolver: (cwd: String, name: String) -> File? = ::resolveOnDisk,
) : InsightDetector {
    override fun detect(session: SessionMetrics): List<Insight> {
        val baseline = baselineFor(session.harness)
        val firstTurn = session.turns.firstOrNull() ?: return emptyList()
        val firstInput = firstTurn.inputTokens + firstTurn.cacheReadTokens + firstTurn.cacheWriteTokens
        val excess = firstInput - baseline
        if (excess < excessThreshold) return emptyList()

        val cwd = session.cwd ?: return emptyList()
        val targetName = if (session.harness == Source.CODEX) "AGENTS.md" else "CLAUDE.md"
        val file = cwdFileResolver(cwd, targetName) ?: return emptyList()
        val lineCount = runCatching { file.readLines().size }.getOrNull() ?: return emptyList()
        if (lineCount < maxLines) return emptyList()

        // Each session over the next month presumably reuses this prefix; a
        // 50% trim recovers half of `excess` per session.
        val savedPerSession = excess / 2
        return listOf(
            Insight(
                kind = InsightKind.CLAUDE_MD_BLOAT,
                severity = InsightSeverity.INFO,
                title = "$targetName is $lineCount lines — likely bloated",
                description = "$targetName loads into every session's system prompt. Trim to under " +
                    "$maxLines lines: move build/test how-tos into Skills (loaded on demand), and split " +
                    "code samples or full style guides into referenced docs.",
                evidence = listOf(
                    "First-turn input: ${firstInput / 1000}k tokens",
                    "Baseline (harness): ${baseline / 1000}k tokens",
                    "Path: ${file.absolutePath}",
                    "Lines: $lineCount",
                ),
                savings = EstimatedSavings(tokens = savedPerSession),
                harness = session.harness,
                sessionId = session.sessionId,
                aiEligible = true, // LLM can produce a section-by-section diff
            )
        )
    }

    private fun baselineFor(harness: Source): Long = when (harness) {
        Source.CLAUDE_CODE -> 6_000
        Source.CODEX -> 4_000
        Source.COPILOT -> 5_000
        Source.OPENCODE -> 5_000
        Source.PI -> 4_000
        Source.ANTIGRAVITY -> 5_000
    }

    companion object {
        fun resolveOnDisk(cwd: String, name: String): File? {
            val f = File(cwd, name)
            return if (f.exists() && f.isFile) f else null
        }
    }
}
