package com.mikepenz.agentbuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.mikepenz.agentbuddy.model.ThemeMode
import com.mikepenz.agentbuddy.model.ToolType
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Brand palette
val SeaTurtle = Color(0xFF339470)
val Dandelion = Color(0xFFDEEF33)
val PaleRose = Color(0xFFFFC2BA)
val Carmine = Color(0xFFE63845)
val WindowsBlue = Color(0xFF3082DB)

// ── Surface scale (graphite, very slight cool tilt; oklch spec in tokens.css) ──
val GraphiteBg = Color(0xFF1B1B1F)        // oklch(0.155 0.004 260) — app canvas
val GraphiteSurface = Color(0xFF212126)   // oklch(0.185 0.004 260) — cards, sidebar
val GraphiteSurface2 = Color(0xFF27272C)  // oklch(0.215 0.004 260) — inputs, hover
val GraphiteSurface3 = Color(0xFF2F2F35)  // oklch(0.250 0.005 260) — pressed, selected
val GraphiteChrome = Color(0xFF17171B)    // oklch(0.135 0.004 260) — titlebar

// ── Foreground ink scale (oklch spec) ──
val InkPrimary = Color(0xFFF6F6F8)    // oklch(0.97 0.002 260)
val InkSecondary = Color(0xFFBDBDC3)  // oklch(0.78 0.004 260)
val InkTertiary = Color(0xFF83838C)   // oklch(0.58 0.006 260)
val InkMuted = Color(0xFF595961)      // oklch(0.42 0.006 260)
val InkSubtle = Color(0xFF404048)     // oklch(0.32 0.005 260)

// ── Line triple — thin dividers at low alpha over dark surfaces ──
val Line1 = Color(0x0FFFFFFF)  // 6% white — primary divider
val Line2 = Color(0x1AFFFFFF)  // 10% white — hover
val Line3 = Color(0x24FFFFFF)  // 14% white — emphasis

// ── Accent — restrained emerald (oklch(0.78 0.14 162)) ──
val AccentEmerald = Color(0xFF4BC48B)      // accent
val AccentEmeraldInk = Color(0xFF0C2D1D)   // oklch(0.20 0.05 162) — ink on accent
val AccentEmeraldTint = Color(0x1F4BC48B)  // 12% tint

// ── Semantic (matched lightness & chroma per oklch spec) ──
val OkGreen = AccentEmerald
val WarnYellow = Color(0xFFD6B64C)     // oklch(0.82 0.13 82)
val DangerRed = Color(0xFFE3634B)      // oklch(0.72 0.17 22)
val InfoBlue = Color(0xFF6FA0D6)       // oklch(0.76 0.12 238)
val VioletPurple = Color(0xFFAA92DC)   // oklch(0.74 0.14 290)

// ── Tool colors (restrained, same L per oklch spec) ──
val ToolBash = Color(0xFFD5B84F)    // oklch(0.82 0.12 88) — yellow-gold
val ToolWeb = Color(0xFFD3884B)     // oklch(0.78 0.13 48) — warm orange
val ToolAsk = Color(0xFF6FA0D6)     // oklch(0.76 0.12 238) — info blue
val ToolWrite = Color(0xFF55B4B4)   // oklch(0.76 0.10 200) — teal
val ToolRead = Color(0xFF4EB98B)    // oklch(0.76 0.11 162) — emerald

// Risk colors
val RiskSafe = Color(0xFF339470) // Sea Turtle
val RiskLow = Color(0xFF5BB88E)
val RiskMedium = Color(0xFFDEEF33) // Dandelion
val RiskHigh = Color(0xFFE6A233)
val RiskCritical = Color(0xFFE63845) // Carmine

// Tool badge colors
val ToolBashColor = Color(0xFFDEEF33) // Dandelion
val ToolAskColor = Color(0xFF3082DB) // Windows Blue
val ToolPlanColor = Color(0xFF339470) // Sea Turtle
val ToolDefaultColor = Color(0xFF78909C)
val ToolFileColor = Color(0xFFC792EA)   // Soft purple for file ops
val ToolSearchColor = Color(0xFF82AAFF) // Soft blue for search
val ToolWebColor = Color(0xFFFF9E64)    // Warm orange for web

// Source badge colors
val SourceClaudeColor = Color(0xFFD97757)   // Claude orange/terracotta
val SourceCopilotColor = Color(0xFF6E40C9)  // GitHub Copilot purple

fun sourceColor(source: com.mikepenz.agentbuddy.model.Source): Color = when (source) {
    com.mikepenz.agentbuddy.model.Source.CLAUDE_CODE -> SourceClaudeColor
    com.mikepenz.agentbuddy.model.Source.COPILOT -> SourceCopilotColor
}

fun sourceLabel(source: com.mikepenz.agentbuddy.model.Source): String = when (source) {
    com.mikepenz.agentbuddy.model.Source.CLAUDE_CODE -> "Claude Code"
    com.mikepenz.agentbuddy.model.Source.COPILOT -> "Copilot"
}

fun riskColor(risk: Int): Color = when (risk) {
    1 -> AccentEmerald
    2 -> ToolRead
    3 -> WarnYellow
    4 -> Color(0xFFD08030)
    5 -> DangerRed
    else -> WarnYellow
}

fun riskLabel(risk: Int): String = when (risk) {
    1 -> "Safe"
    2 -> "Low"
    3 -> "Medium"
    4 -> "High"
    5 -> "Critical"
    else -> "Medium"
}

fun toolColor(toolName: String, toolType: ToolType): Color = when {
    toolName.equals("Bash", ignoreCase = true) -> ToolBash
    toolName.equals("Read", ignoreCase = true) -> ToolRead
    toolName.equals("Edit", ignoreCase = true) ||
            toolName.equals("Write", ignoreCase = true) -> ToolWrite
    toolName.equals("Grep", ignoreCase = true) ||
            toolName.equals("Glob", ignoreCase = true) -> ToolRead
    toolName.equals("WebFetch", ignoreCase = true) ||
            toolName.equals("WebSearch", ignoreCase = true) -> ToolWeb
    toolType == ToolType.ASK_USER_QUESTION -> ToolAsk
    toolType == ToolType.PLAN -> AccentEmerald
    else -> InkTertiary
}

// Dark theme — graphite dark palette (design system)
private val DarkColorScheme = darkColorScheme(
    primary = AccentEmerald,
    onPrimary = AccentEmeraldInk,
    primaryContainer = Color(0xFF1A4D38),
    onPrimaryContainer = Color(0xFFB8E6D4),
    secondary = WindowsBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A3D5C),
    onSecondaryContainer = Color(0xFFB8D4F0),
    tertiary = Dandelion,
    onTertiary = Color(0xFF2A2A00),
    tertiaryContainer = Color(0xFF3A3A00),
    onTertiaryContainer = Dandelion,
    background = GraphiteBg,
    onBackground = InkPrimary,
    surface = GraphiteSurface,
    onSurface = InkPrimary,
    surfaceVariant = GraphiteSurface2,
    onSurfaceVariant = InkTertiary,
    surfaceContainerLowest = GraphiteChrome,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteSurface2,
    surfaceContainerHigh = GraphiteSurface3,
    surfaceContainerHighest = GraphiteSurface3,
    // Dividers: Line1 for default outlines, Line2 for emphasized
    outline = Line1,
    outlineVariant = Line2,
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF161B22),
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFF4D0F14),
    onErrorContainer = Color(0xFFFFC2BA), // Pale Rose
)

// Light theme — clean warm surfaces, Sea Turtle primary
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A7D5F), // Slightly deeper Sea Turtle for contrast
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F0E4),
    onPrimaryContainer = Color(0xFF0D3322),
    secondary = Color(0xFF2872C2), // Deeper Windows Blue for contrast
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E8F8),
    onSecondaryContainer = Color(0xFF0D2D4D),
    tertiary = Color(0xFF8B9900), // Deeper Dandelion for readability
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F4CC),
    onTertiaryContainer = Color(0xFF2A2D00),
    background = Color(0xFFFBFCFD),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFF0F3F6),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FB),
    surfaceContainer = Color(0xFFF0F3F6),
    surfaceContainerHigh = Color(0xFFE8ECF0),
    surfaceContainerHighest = Color(0xFFDDE3E8),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1A1D21),
    inverseOnSurface = Color(0xFFF0F3F6),
    error = Color(0xFFCC2E38), // Deeper Carmine for light bg
    onError = Color.White,
    errorContainer = Color(0xFFFFC2BA), // Pale Rose
    onErrorContainer = Color(0xFF4D0F14),
)

// ─────────────────────────────────────────────────────────────────────────────
// Theme-aware token palette (Iter 4): holds the semantic color tokens the UI
// reads. The top-level `GraphiteBg`/`InkPrimary`/`Line1`/... vals above remain
// as the dark-theme raw constants (backward compatibility + single source of
// truth for `AgentBuddyColorsDark`). Call sites should reference
// `AgentBuddyColors.background` etc. so the palette swaps with `ThemeMode`.
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class AgentBuddyColorsPalette(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val chrome: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val line1: Color,
    val line2: Color,
    val line3: Color,
    val accentEmerald: Color,
    val accentEmeraldInk: Color,
    val accentEmeraldTint: Color,
    val isDark: Boolean,
)

private val AgentBuddyColorsDark = AgentBuddyColorsPalette(
    background = GraphiteBg,
    surface = GraphiteSurface,
    surface2 = GraphiteSurface2,
    surface3 = GraphiteSurface3,
    chrome = GraphiteChrome,
    inkPrimary = InkPrimary,
    inkSecondary = InkSecondary,
    inkTertiary = InkTertiary,
    inkMuted = InkMuted,
    inkSubtle = InkSubtle,
    line1 = Line1,
    line2 = Line2,
    line3 = Line3,
    accentEmerald = AccentEmerald,
    accentEmeraldInk = AccentEmeraldInk,
    accentEmeraldTint = AccentEmeraldTint,
    isDark = true,
)

// Light-theme mirrors: warm neutrals, dark ink, low-alpha black hairlines.
private val AgentBuddyColorsLight = AgentBuddyColorsPalette(
    background = Color(0xFFFBFCFD),       // app canvas
    surface = Color(0xFFFFFFFF),          // cards, sidebar
    surface2 = Color(0xFFF3F5F8),         // inputs, hover
    surface3 = Color(0xFFE8ECF1),         // pressed, selected
    chrome = Color(0xFFF0F3F6),           // titlebar
    inkPrimary = Color(0xFF1A1D21),       // 97% black-equiv ink
    inkSecondary = Color(0xFF3D434B),
    inkTertiary = Color(0xFF5B626C),
    inkMuted = Color(0xFF7F8690),
    inkSubtle = Color(0xFFA4ABB5),
    line1 = Color(0x14000000),            // 8% black — primary divider
    line2 = Color(0x24000000),            // 14% black — hover
    line3 = Color(0x36000000),            // 21% black — emphasis
    accentEmerald = Color(0xFF2A7D5F),    // matches lightColorScheme.primary
    accentEmeraldInk = Color.White,
    accentEmeraldTint = Color(0x1F2A7D5F),
    isDark = false,
)

val LocalAgentBuddyColors = staticCompositionLocalOf { AgentBuddyColorsDark }

/**
 * Theme-aware color tokens. Prefer `AgentBuddyColors.surface` / `.inkPrimary`
 * / `.line1` etc. over referencing `GraphiteSurface` / `InkPrimary` / `Line1`
 * directly — the raw vals are dark-only.
 */
object AgentBuddyColors {
    val current: AgentBuddyColorsPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAgentBuddyColors.current

    val background: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.background
    val surface: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.surface
    val surface2: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.surface2
    val surface3: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.surface3
    val chrome: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.chrome
    val inkPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.inkPrimary
    val inkSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.inkSecondary
    val inkTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.inkTertiary
    val inkMuted: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.inkMuted
    val inkSubtle: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.inkSubtle
    val line1: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.line1
    val line2: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.line2
    val line3: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.line3
    val accentEmerald: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.accentEmerald
    val accentEmeraldInk: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.accentEmeraldInk
    val accentEmeraldTint: Color
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.accentEmeraldTint
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalAgentBuddyColors.current.isDark
}

/**
 * Shared iconography + density tokens (Iter 4). Keeps Lucide/Material icon
 * sizes and list-row densities aligned across Approvals / History /
 * Statistics / Settings / Shell / Slim.
 */
object AgentBuddyDimens {
    // Icon sizing scale
    val IconXSmall: Dp = 12.dp
    val IconSmall: Dp = 14.dp
    val IconMedium: Dp = 16.dp
    val IconLarge: Dp = 20.dp
    val IconXLarge: Dp = 24.dp

    // Card / list density
    val CardCornerRadius: Dp = 10.dp
    val CardPaddingHorizontal: Dp = 12.dp
    val CardPaddingVertical: Dp = 10.dp
    val ListRowHeight: Dp = 44.dp
    val ListRowGutter: Dp = 12.dp
    val SectionSpacing: Dp = 16.dp
}

@Composable
fun AgentBuddyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkMode()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val buddyColors = if (isDark) AgentBuddyColorsDark else AgentBuddyColorsLight
    CompositionLocalProvider(LocalAgentBuddyColors provides buddyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * Simple file log writer that appends log entries to a file in the app data directory.
 */
class FileLogWriter(
    private val logDir: File = File(System.getProperty("user.home"), ".agent-buddy/logs"),
) : LogWriter() {
    private val logFile: File by lazy {
        logDir.mkdirs()
        File(logDir, "agent-buddy.log")
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            val line = buildString {
                append("$timestamp [$severity] $tag: $message")
                if (throwable != null) {
                    append("\n${throwable.stackTraceToString()}")
                }
                append("\n")
            }
            logFile.appendText(line)
        } catch (_: Exception) {
            // Silently ignore file write failures to avoid recursive logging
        }
    }
}

/**
 * Configures Kermit with the file log writer.
 */
fun configureLogging() {
    Logger.addLogWriter(FileLogWriter())
}
