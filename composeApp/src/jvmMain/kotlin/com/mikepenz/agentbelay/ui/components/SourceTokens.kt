package com.mikepenz.agentbelay.ui.components

import androidx.compose.ui.graphics.Color
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.SourceClaudeColor
import com.mikepenz.agentbelay.ui.theme.SourceCopilotColor
import com.mikepenz.agentbelay.ui.theme.VioletPurple
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.ui.theme.SourceHermesColor

/**
 * Canonical, screen-agnostic mappings from [Source] to its display label
 * and accent color. Pulled out here so History, Usage, and Insights all
 * agree on the same Claude orange / Copilot purple / Codex yellow / etc.
 *
 * Anywhere the harness needs to be presented to the user, these are the
 * functions to use — they're the "source tokens" of the design system.
 */
fun sourceDisplayName(source: Source): String = when (source) {
    Source.CLAUDE_CODE -> "Claude Code"
    Source.COPILOT -> "GitHub Copilot"
    Source.OPENCODE -> "OpenCode"
    Source.PI -> "Pi"
    Source.CODEX -> "Codex"
    Source.HERMES -> "Hermes"
    Source.ANTIGRAVITY -> "Antigravity"
}

fun sourceAccentColor(source: Source): Color = when (source) {
    Source.CLAUDE_CODE -> SourceClaudeColor
    Source.COPILOT -> SourceCopilotColor
    Source.OPENCODE -> InfoBlue
    Source.PI -> VioletPurple
    Source.CODEX -> WarnYellow
    Source.HERMES -> SourceHermesColor
    Source.ANTIGRAVITY -> Color(0xFF9061F9)
}
