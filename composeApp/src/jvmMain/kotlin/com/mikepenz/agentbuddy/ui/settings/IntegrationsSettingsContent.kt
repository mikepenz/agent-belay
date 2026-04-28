package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.ui.components.ColoredIconTile
import com.mikepenz.agentbuddy.ui.components.DecisionStatus
import com.mikepenz.agentbuddy.ui.components.DesignToggle
import com.mikepenz.agentbuddy.ui.components.HorizontalHairline
import com.mikepenz.agentbuddy.ui.components.OutlineButton
import com.mikepenz.agentbuddy.ui.components.PrimaryButton
import com.mikepenz.agentbuddy.ui.components.StatusPill
import com.mikepenz.agentbuddy.ui.components.TagSize
import com.mikepenz.agentbuddy.ui.icons.LucidePlug
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import com.mikepenz.agentbuddy.ui.theme.VioletPurple

@Composable
fun IntegrationsSettingsContent(
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotRegistered: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onRegisterCopilot: () -> Unit,
    onUnregisterCopilot: () -> Unit,
) {
    SettingSection(
        title = "Integrations",
        desc = "Agents that route approvals through Agent Buddy.",
    ) {
        val items = listOf(
            IntegrationItemData(
                id = "claude",
                name = "Claude Code",
                desc = "Hook in ~/.claude/settings.json",
                color = Color(0xFFD97757),
                registered = isHookRegistered,
                onRegister = onRegisterHook,
                onUnregister = onUnregisterHook,
            ),
            IntegrationItemData(
                id = "copilot",
                name = "GitHub Copilot",
                desc = "User-scoped hook in ~/.copilot/hooks/agent-buddy.json " +
                    "(PreToolUse + PermissionRequest, requires Copilot CLI \u2265 v1.0.21)",
                color = VioletPurple,
                registered = isCopilotRegistered,
                onRegister = onRegisterCopilot,
                onUnregister = onUnregisterCopilot,
                extra = {
                    CopilotFailClosedCard(
                        failClosed = settings.copilotFailClosed,
                        onChange = { onSettingsChange(settings.copy(copilotFailClosed = it)) },
                    )
                },
            ),
        )
        items.forEachIndexed { idx, it -> IntegrationRow(item = it, first = idx == 0) }
    }
}

private data class IntegrationItemData(
    val id: String,
    val name: String,
    val desc: String,
    val color: Color,
    val registered: Boolean,
    val onRegister: () -> Unit,
    val onUnregister: () -> Unit,
    val extra: (@Composable () -> Unit)? = null,
)

@Composable
private fun IntegrationRow(item: IntegrationItemData, first: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalHairline()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ColoredIconTile(
                icon = LucidePlug,
                tint = item.color,
                contentDescription = null,
            )
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
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(
                        status = if (item.registered) DecisionStatus.APPROVED
                        else DecisionStatus.TIMEOUT,
                        size = TagSize.SMALL,
                        text = if (item.registered) "Registered" else DecisionStatus.TIMEOUT.label,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.desc,
                    color = AgentBuddyColors.inkTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (item.extra != null && item.registered) {
                    Spacer(Modifier.height(12.dp))
                    item.extra.invoke()
                }
            }
            if (item.registered) {
                OutlineButton(text = "Unregister", onClick = item.onUnregister)
            } else {
                PrimaryButton(text = "Register", onClick = item.onRegister)
            }
        }
    }
}

@Composable
private fun CopilotFailClosedCard(failClosed: Boolean, onChange: (Boolean) -> Unit) {
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
        DesignToggle(checked = failClosed, onCheckedChange = onChange)
    }
}

