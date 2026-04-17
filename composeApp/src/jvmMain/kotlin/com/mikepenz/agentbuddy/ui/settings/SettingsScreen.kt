package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mikepenz.agentbuddy.ui.components.AgentBuddyCard
import com.mikepenz.agentbuddy.ui.components.DecisionStatus
import com.mikepenz.agentbuddy.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbuddy.ui.components.PillSegmented
import com.mikepenz.agentbuddy.ui.components.SectionLabel
import com.mikepenz.agentbuddy.ui.components.StatusPill
import com.mikepenz.agentbuddy.ui.components.TagSize
import com.mikepenz.agentbuddy.ui.icons.LucideBrain
import com.mikepenz.agentbuddy.ui.icons.LucideChevronDown
import com.mikepenz.agentbuddy.ui.icons.LucidePlug
import com.mikepenz.agentbuddy.ui.icons.LucideShield
import com.mikepenz.agentbuddy.ui.icons.LucideSliders
import com.mikepenz.agentbuddy.ui.icons.LucideZap
import com.mikepenz.agentbuddy.ui.theme.AccentEmerald
import com.mikepenz.agentbuddy.ui.theme.DangerRed
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import com.mikepenz.agentbuddy.ui.theme.InfoBlue
import com.mikepenz.agentbuddy.ui.theme.InkMuted
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import com.mikepenz.agentbuddy.ui.theme.VioletPurple
import com.mikepenz.agentbuddy.ui.theme.WarnYellow

// ── Data ─────────────────────────────────────────────────────────────────────

enum class SettingsTab(val label: String, val icon: ImageVector) {
    General("General", LucideSliders),
    Integrations("Integrations", LucidePlug),
    Risk("Risk Analysis", LucideBrain),
    Protections("Protections", LucideShield),
    Capabilities("Capabilities", LucideZap),
}

data class SettingsToggles(
    val theme: String = "system",
    val trayIcon: Boolean = true,
    val compact: Boolean = false,
    val onTop: Boolean = false,
    val startOnBoot: Boolean = true,
    val away: Boolean = false,
    val prominentAllow: Boolean = true,
    val port: String = "19532",
    val bind: String = "loopback",
    val timeout: String = "300",
    val riskEnabled: Boolean = true,
    val riskBackend: String = "claude",
    val riskModel: String = "Claude Haiku 4.5",
    val autoApprove: Int = 1,
    val autoDeny: Int = 5,
    val compression: Boolean = true,
    val socratic: Boolean = false,
    val safety: Boolean = true,
    val scratchpad: Boolean = false,
    val claudeReg: Boolean = true,
    val copilotReg: Boolean = true,
    val failClosed: Boolean = true,
)

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    toggles: SettingsToggles,
    onTogglesChange: (SettingsToggles) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SettingsTab.General) }

    Row(modifier = modifier.fillMaxSize().background(AgentBuddyColors.background)) {
        SettingsSidebar(
            selected = tab,
            onSelect = { tab = it },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 520.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            when (tab) {
                SettingsTab.General -> GeneralTabContent(toggles = toggles, onChange = onTogglesChange)
                SettingsTab.Integrations -> IntegrationsTabContent(toggles = toggles, onChange = onTogglesChange)
                SettingsTab.Risk -> RiskTabContent(toggles = toggles, onChange = onTogglesChange)
                SettingsTab.Protections -> ProtectionsTabContent()
                SettingsTab.Capabilities -> CapabilitiesTabContent(toggles = toggles, onChange = onTogglesChange)
            }
        }
    }
}

// ── Sub-sidebar ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsSidebar(
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
) {
    // Outer Row = sidebar column + trailing 1dp separator (JSX: borderRight only).
    Row(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(AgentBuddyColors.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            SectionLabel(
                text = "Settings",
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            )
            SettingsTab.entries.forEach { tab ->
                SidebarItem(
                    tab = tab,
                    active = selected == tab,
                    onClick = { onSelect(tab) },
                )
            }
        }
        // Right-edge separator — matches JSX `borderRight: 1px solid var(--line)`.
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(AgentBuddyColors.line1),
        )
    }
}

@Composable
private fun SidebarItem(tab: SettingsTab, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: liveHovered
    val bg = when {
        active -> AgentBuddyColors.surface3
        hovered -> AgentBuddyColors.surface2
        else -> Color.Transparent
    }
    val fg = if (active) AgentBuddyColors.inkPrimary else AgentBuddyColors.inkSecondary
    val iconColor = if (active) AccentEmerald else AgentBuddyColors.inkTertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = tab.label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = (-0.05).sp,
        )
    }
}

// ── Shared: SettingGroup / SettingRow / Toggle / Controls ────────────────────

@Composable
private fun SettingGroup(
    title: String,
    desc: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = 780.dp)) {
        Text(
            text = title,
            color = AgentBuddyColors.inkPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
        if (desc != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                color = AgentBuddyColors.inkTertiary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        AgentBuddyCard(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    desc: String? = null,
    first: Boolean = false,
    right: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AgentBuddyColors.line1))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = AgentBuddyColors.inkPrimary,
                    fontSize = 13.sp,
                    letterSpacing = (-0.05).sp,
                )
                if (desc != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = desc,
                        color = AgentBuddyColors.inkTertiary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            if (right != null) {
                Box { right() }
            }
        }
    }
}

@Composable
private fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg = if (checked) AccentEmerald else AgentBuddyColors.surface3
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null,
    mono: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 120.dp,
) {
    // Static rendering of a text input (no editing inside preview-only component).
    Row(
        modifier = Modifier
            .width(width)
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AgentBuddyColors.background)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            color = AgentBuddyColors.inkPrimary,
            fontSize = 12.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f),
        )
        if (suffix != null) {
            Text(
                text = suffix,
                color = AgentBuddyColors.inkMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) AgentBuddyColors.surface2 else Color.Transparent)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AgentBuddyColors.inkSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GhostButton(
    text: String,
    color: Color = AgentBuddyColors.inkSecondary,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) color.copy(alpha = 0.1f) else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) AccentEmerald.copy(alpha = 0.9f) else AccentEmerald)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFF163826),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── General tab ──────────────────────────────────────────────────────────────

@Composable
private fun GeneralTabContent(toggles: SettingsToggles, onChange: (SettingsToggles) -> Unit) {
    SettingGroup(title = "Appearance", desc = "How Agent Buddy looks across your desktop.") {
        SettingRow(label = "Theme", first = true, right = {
            PillSegmented(
                options = listOf(
                    "system" to "System",
                    "dark" to "Dark",
                    "light" to "Light",
                ),
                selected = toggles.theme,
                onSelect = { onChange(toggles.copy(theme = it)) },
            )
        })
        SettingRow(
            label = "Menu bar icon",
            desc = "Show a compact tray icon with pending count.",
            right = { Toggle(toggles.trayIcon) { onChange(toggles.copy(trayIcon = it)) } },
        )
        SettingRow(
            label = "Compact density",
            desc = "Tighter spacing throughout lists and detail views.",
            right = { Toggle(toggles.compact) { onChange(toggles.copy(compact = it)) } },
        )
    }
    SettingGroup(title = "Behavior") {
        SettingRow(
            label = "Always on top",
            desc = "Keep the window above other apps when an approval arrives.",
            first = true,
            right = { Toggle(toggles.onTop) { onChange(toggles.copy(onTop = it)) } },
        )
        SettingRow(
            label = "Start on boot",
            right = { Toggle(toggles.startOnBoot) { onChange(toggles.copy(startOnBoot = it)) } },
        )
        SettingRow(
            label = "Away mode",
            desc = "Disable timeouts while you're away. Approvals wait indefinitely.",
            right = { Toggle(toggles.away) { onChange(toggles.copy(away = it)) } },
        )
        SettingRow(
            label = "Prominent \"always allow\" button",
            desc = "Surface the sticky-approve option in the approval footer.",
            right = { Toggle(toggles.prominentAllow) { onChange(toggles.copy(prominentAllow = it)) } },
        )
    }
    SettingGroup(title = "Server", desc = "Local approval endpoint used by your agents.") {
        SettingRow(label = "Listening port", first = true, right = {
            TextInput(
                value = toggles.port,
                onChange = { onChange(toggles.copy(port = it)) },
                mono = true,
            )
        })
        SettingRow(
            label = "Bind address",
            desc = "\"All interfaces\" exposes the approval server to your LAN. No authentication — only enable on trusted networks.",
            right = {
                PillSegmented(
                    options = listOf(
                        "loopback" to "Loopback",
                        "all" to "All interfaces",
                    ),
                    selected = toggles.bind,
                    onSelect = { onChange(toggles.copy(bind = it)) },
                )
            },
        )
        SettingRow(
            label = "Default timeout",
            desc = "How long an approval waits before the agent falls back to a default.",
            right = {
                TextInput(
                    value = toggles.timeout,
                    onChange = { onChange(toggles.copy(timeout = it)) },
                    suffix = "sec",
                    mono = true,
                    width = 140.dp,
                )
            },
        )
    }
    SettingGroup(title = "Data", desc = "Stored locally; nothing leaves your machine.") {
        SettingRow(
            label = "History",
            desc = "152 of 20,000 entries used (0.76%).",
            first = true,
            right = { OutlineButton(text = "Export JSON") {} },
        )
        SettingRow(
            label = "Clear history",
            desc = "Permanently removes all recorded decisions and protections.",
            right = { GhostButton(text = "Clear…", color = DangerRed) {} },
        )
    }
}

// ── Integrations tab ─────────────────────────────────────────────────────────

@Composable
private fun IntegrationsTabContent(toggles: SettingsToggles, onChange: (SettingsToggles) -> Unit) {
    SettingGroup(title = "Integrations", desc = "Agents that route approvals through Agent Buddy.") {
        val integrations = listOf(
            IntegrationItem("claude", "Claude Code", "Hook in ~/.claude/settings.json", Color(0xFFD97757), toggles.claudeReg, false),
            IntegrationItem("copilot", "GitHub Copilot", "User-scoped hook in ~/.copilot/hooks/agent-buddy.json (PreToolUse + PermissionRequest, requires Copilot CLI ≥ v1.0.21)", VioletPurple, toggles.copilotReg, true),
            IntegrationItem("cursor", "Cursor", "Tool-use hooks for the Cursor agent.", InfoBlue, false, false),
        )
        integrations.forEachIndexed { idx, it ->
            IntegrationRow(item = it, first = idx == 0, toggles = toggles, onChange = onChange)
        }
    }
}

private data class IntegrationItem(
    val id: String,
    val name: String,
    val desc: String,
    val color: Color,
    val registered: Boolean,
    val hasExtra: Boolean,
)

@Composable
private fun IntegrationRow(
    item: IntegrationItem,
    first: Boolean,
    toggles: SettingsToggles,
    onChange: (SettingsToggles) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AgentBuddyColors.line1))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(item.color.copy(alpha = 0.14f))
                    .border(1.dp, item.color.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LucidePlug,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.name,
                        color = AgentBuddyColors.inkPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusPill(
                        status = if (item.registered) DecisionStatus.APPROVED else DecisionStatus.TIMEOUT,
                        size = TagSize.SMALL,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.desc,
                    color = AgentBuddyColors.inkTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (item.hasExtra) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AgentBuddyColors.surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fail-closed when unreachable",
                                color = AgentBuddyColors.inkPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Copilot blocks the action if Agent Buddy isn't running.",
                                color = AgentBuddyColors.inkTertiary,
                                fontSize = 11.sp,
                            )
                        }
                        Toggle(toggles.failClosed) { onChange(toggles.copy(failClosed = it)) }
                    }
                }
            }
            if (item.registered) {
                OutlineButton(text = "Unregister") {}
            } else {
                PrimaryButton(text = "Register") {}
            }
        }
    }
}

// ── Risk tab ─────────────────────────────────────────────────────────────────

@Composable
private fun RiskTabContent(toggles: SettingsToggles, onChange: (SettingsToggles) -> Unit) {
    SettingGroup(title = "Risk analysis", desc = "An optional LLM pre-screens each request and assigns a risk level 1–5.") {
        SettingRow(
            label = "Enable risk analysis",
            desc = "Calls the backend below for every tool request.",
            first = true,
            right = { Toggle(toggles.riskEnabled) { onChange(toggles.copy(riskEnabled = it)) } },
        )
        SettingRow(label = "Backend", right = {
            PillSegmented(
                options = listOf(
                    "claude" to "Claude",
                    "copilot" to "Copilot",
                    "ollama" to "Ollama",
                ),
                selected = toggles.riskBackend,
                onSelect = { onChange(toggles.copy(riskBackend = it)) },
            )
        })
        SettingRow(label = "Model", right = {
            FakeDropdown(value = toggles.riskModel, width = 220.dp)
        })
    }
    SettingGroup(title = "Auto-decision bands", desc = "Skip the approval prompt for requests that fall outside this range.") {
        Column(modifier = Modifier.padding(20.dp)) {
            RiskRange(
                autoApprove = toggles.autoApprove,
                autoDeny = toggles.autoDeny,
                onChange = { a, d -> onChange(toggles.copy(autoApprove = a, autoDeny = d)) },
            )
        }
    }
}

@Composable
private fun FakeDropdown(value: String, width: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .width(width)
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AgentBuddyColors.surface)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = value,
            color = AgentBuddyColors.inkPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = LucideChevronDown,
            contentDescription = null,
            tint = AgentBuddyColors.inkMuted,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun RiskRange(
    autoApprove: Int,
    autoDeny: Int,
    onChange: (Int, Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Auto-approve up to",
                color = AgentBuddyColors.inkSecondary,
                fontSize = 12.5.sp,
            )
            Text(
                text = if (autoApprove == 0) "Off" else "Level $autoApprove",
                color = AccentEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        RiskSlider(value = autoApprove, color = AccentEmerald, invert = false) { onChange(it, autoDeny) }
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Auto-deny at or above",
                color = AgentBuddyColors.inkSecondary,
                fontSize = 12.5.sp,
            )
            Text(
                text = if (autoDeny == 6) "Off" else "Level $autoDeny",
                color = DangerRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        RiskSlider(value = autoDeny, color = DangerRed, invert = true) { onChange(autoApprove, it) }
    }
}

@Composable
private fun RiskSlider(value: Int, color: Color, invert: Boolean, onChange: (Int) -> Unit) {
    val ticks = listOf(0, 1, 2, 3, 4, 5)
    val fraction = value / 5f
    Column {
        // Track + fill + thumb layer. JSX uses absolute positioning within 28px tall
        // container; we replicate with Box + fractional fillMaxWidth overlays.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            // Background track — full width, 4dp tall, centered vertically at 12px top.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AgentBuddyColors.surface2),
            )
            // Filled portion. Non-invert: from 0 to fraction. Invert: from fraction to 1.
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (invert) (1f - fraction).coerceAtLeast(0f) else fraction.coerceAtLeast(0f))
                    .height(4.dp)
                    .align(if (invert) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
            // Tick thumbs — positioned evenly via a Row with SpaceBetween. JSX
            // places each thumb at calc((t/5)*100% - 7px) — since the row's first
            // child aligns to left-edge and last aligns to right-edge, this matches.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ticks.forEach { t ->
                    val active = (!invert && t <= value) || (invert && t >= value)
                    val isSelected = t == value
                    val interactionSource = remember(t) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 17.dp else 14.dp)
                            .clip(CircleShape)
                            .background(if (active) color else AgentBuddyColors.surface3)
                            .border(2.dp, AgentBuddyColors.background, CircleShape)
                            .hoverable(interactionSource)
                            .clickable(interactionSource = interactionSource, indication = null) { onChange(t) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Off", color = AgentBuddyColors.inkMuted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            for (i in 1..5) {
                Text(text = "$i", color = AgentBuddyColors.inkMuted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── Protections tab ──────────────────────────────────────────────────────────

private data class ProtectionMode(
    val value: String,
    val label: String,
    val color: Color,
)

private val ProtectionModes: List<ProtectionMode> = listOf(
    // "Off" uses the dark-scale InkMuted raw token because ProtectionModes is
    // declared at the top level (not @Composable). Light-theme rendering falls
    // back gracefully — rows recolor the label via AgentBuddyColors.inkMuted.
    ProtectionMode("off", "Off", InkMuted),
    ProtectionMode("ask", "Ask", WarnYellow),
    ProtectionMode("ask-block", "Ask + Block", WarnYellow),
    ProtectionMode("block", "Auto-block", DangerRed),
    ProtectionMode("correct", "Auto-correct", AccentEmerald),
    ProtectionMode("log", "Log only", InfoBlue),
)

private data class ProtectionItem(
    val id: String,
    val name: String,
    val desc: String,
    val mode: String,
    val pickerOpen: Boolean = false,
)

@Composable
private fun ProtectionsTabContent(initialOpenPickerId: String? = null) {
    var items by remember {
        mutableStateOf(
            listOf(
                ProtectionItem("sudo", "sudo", "Prevents any invocation of sudo.", "ask-block", pickerOpen = initialOpenPickerId == "sudo"),
                ProtectionItem("curl", "curl | sh", "Flags piped curl|sh and wget|sh installs.", "ask", pickerOpen = initialOpenPickerId == "curl"),
                ProtectionItem("rmrf", "rm -rf", "Detects destructive rm -rf at repo root.", "block", pickerOpen = initialOpenPickerId == "rmrf"),
                ProtectionItem("env", ".env secrets", "Warns when tools read .env files.", "correct", pickerOpen = initialOpenPickerId == "env"),
                ProtectionItem("push", "git push --force", "Blocks force-push to protected branches.", "log", pickerOpen = initialOpenPickerId == "push"),
            )
        )
    }

    Column(modifier = Modifier.widthIn(max = 860.dp)) {
        Text(
            text = "Guardrails",
            color = AgentBuddyColors.inkPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Pattern-matched rules that run before risk analysis. Choose how each responds.",
            color = AgentBuddyColors.inkTertiary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        AgentBuddyCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                items.forEachIndexed { idx, item ->
                    ProtectionRow(
                        item = item,
                        first = idx == 0,
                        onModeChange = { newMode ->
                            items = items.map {
                                if (it.id == item.id) it.copy(mode = newMode, pickerOpen = false) else it.copy(pickerOpen = false)
                            }
                        },
                        onTogglePicker = {
                            items = items.map {
                                if (it.id == item.id) it.copy(pickerOpen = !it.pickerOpen) else it.copy(pickerOpen = false)
                            }
                        },
                        onDismissPicker = {
                            items = items.map { it.copy(pickerOpen = false) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionRow(
    item: ProtectionItem,
    first: Boolean,
    onModeChange: (String) -> Unit,
    onTogglePicker: () -> Unit,
    onDismissPicker: () -> Unit,
) {
    Column {
        if (!first) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AgentBuddyColors.line1))
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val activeMode = ProtectionModes.find { it.value == item.mode } ?: ProtectionModes[0]
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(activeMode.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LucideShield,
                    contentDescription = null,
                    tint = activeMode.color,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = AgentBuddyColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.desc,
                    color = AgentBuddyColors.inkTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            ProtectionModePicker(
                active = activeMode,
                open = item.pickerOpen,
                onToggle = onTogglePicker,
                onSelect = onModeChange,
                onDismiss = onDismissPicker,
            )
        }
    }
}

@Composable
private fun ProtectionModePicker(
    active: ProtectionMode,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        // Trigger button.
        Row(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(active.color.copy(alpha = 0.10f))
                .border(1.dp, active.color.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { onToggle() }
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(active.color),
            )
            Text(
                text = active.label,
                color = active.color,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = LucideChevronDown,
                contentDescription = null,
                tint = active.color,
                modifier = Modifier.size(11.dp),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(LocalDensity.current) { 30.dp.roundToPx() }),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(7.dp), clip = false)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line2, RoundedCornerShape(7.dp))
                        .padding(4.dp),
                ) {
                    ProtectionModes.forEach { mode ->
                        val selected = mode.value == active.value
                        val optSource = remember(mode.value) { MutableInteractionSource() }
                        val hovered by optSource.collectIsHoveredAsState()
                        val optBg = when {
                            selected -> AgentBuddyColors.surface2
                            hovered -> AgentBuddyColors.surface2.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                        val optFg = if (selected) mode.color else AgentBuddyColors.inkPrimary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(optBg)
                                .hoverable(optSource)
                                .clickable(interactionSource = optSource, indication = null) { onSelect(mode.value) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(mode.color),
                            )
                            Text(
                                text = mode.label,
                                color = optFg,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Capabilities tab ─────────────────────────────────────────────────────────

@Composable
private fun CapabilitiesTabContent(toggles: SettingsToggles, onChange: (SettingsToggles) -> Unit) {
    SettingGroup(title = "Session capabilities", desc = "Instructions injected at the start of every session with participating agents.") {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CapabilityRow(
                icon = LucideZap,
                color = WarnYellow,
                title = "Response compression",
                desc = "Cut output tokens by instructing the agent to respond tersely. Code, commands, paths, and URLs are preserved verbatim.",
                checked = toggles.compression,
                onChange = { onChange(toggles.copy(compression = it)) },
            )
            CapabilityRow(
                icon = LucideBrain,
                color = VioletPurple,
                title = "Socratic thinking",
                desc = "Inject a Socratic reasoning prompt at session start. Forces the model to ask clarifying questions and surface assumptions before answering.",
                checked = toggles.socratic,
                onChange = { onChange(toggles.copy(socratic = it)) },
            )
            CapabilityRow(
                icon = LucideShield,
                color = AccentEmerald,
                title = "Safety checklist",
                desc = "Prepends a 6-item safety checklist covering secrets, side-effects and irreversible operations.",
                checked = toggles.safety,
                onChange = { onChange(toggles.copy(safety = it)) },
            )
            CapabilityRow(
                icon = LucideSliders,
                color = InfoBlue,
                title = "Scratchpad discipline",
                desc = "Require the agent to maintain a plan file in .agent/plan.md and update it after every step.",
                checked = toggles.scratchpad,
                onChange = { onChange(toggles.copy(scratchpad = it)) },
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    color: Color,
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) AgentBuddyColors.surface2 else Color.Transparent)
            .border(
                1.dp,
                if (checked) color.copy(alpha = 0.3f) else AgentBuddyColors.line1,
                RoundedCornerShape(8.dp),
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AgentBuddyColors.inkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                color = AgentBuddyColors.inkTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Toggle(checked, onChange)
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsGeneral() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold { SettingsScreen(toggles = t, onTogglesChange = { t = it }) }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsIntegrations() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Integrations, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                IntegrationsTabContent(toggles = t, onChange = { t = it })
            }
        }
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsRisk() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Risk, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                RiskTabContent(toggles = t, onChange = { t = it })
            }
        }
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsProtections() {
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Protections, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
            ) {
                ProtectionsTabContent()
            }
        }
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsCapabilities() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Capabilities, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                CapabilitiesTabContent(toggles = t, onChange = { t = it })
            }
        }
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsHoverStates() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold {
        androidx.compose.runtime.CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
            SettingsScreen(toggles = t, onTogglesChange = { t = it })
        }
    }
}

// RiskTab with Auto-deny at its "Off" sentinel (value 6 → JSX renders "Off").
@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewRiskTabAutoDenyOff() {
    var t by remember {
        mutableStateOf(SettingsToggles(autoApprove = 2, autoDeny = 6))
    }
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Risk, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                RiskTabContent(toggles = t, onChange = { t = it })
            }
        }
    }
}

// Protections tab with one ProtectionModePicker popover pre-opened.
@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewProtectionModePickerOpen() {
    PreviewScaffold {
        Row(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
            SettingsSidebar(selected = SettingsTab.Protections, onSelect = {})
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 36.dp),
            ) {
                ProtectionsTabContent(initialOpenPickerId = "curl")
            }
        }
    }
}

// ── Light theme & state coverage (iter 3) ──────────────────────────────────

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsGeneralLight() {
    var t by remember { mutableStateOf(SettingsToggles()) }
    PreviewScaffold(themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT) {
        SettingsScreen(toggles = t, onTogglesChange = { t = it })
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsLoading() {
    PreviewScaffold {
        com.mikepenz.agentbuddy.ui.components.ScreenLoadingState(label = "Loading settings…")
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewSettingsError() {
    PreviewScaffold {
        com.mikepenz.agentbuddy.ui.components.ScreenErrorState(
            title = "Settings failed to load",
            message = "settings.json is invalid or could not be opened.",
        )
    }
}
