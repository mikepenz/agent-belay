package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.ThemeMode
import com.mikepenz.agentbuddy.platform.StartupManager
import com.mikepenz.agentbuddy.ui.components.DesignToggle
import com.mikepenz.agentbuddy.ui.components.PillSegmented
import com.mikepenz.agentbuddy.ui.theme.DangerRed

@Composable
fun GeneralSettingsContent(
    settings: AppSettings,
    historyCount: Int,
    onSettingsChange: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently remove all history entries.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }

    SettingSection(title = "Appearance", desc = "How Agent Buddy looks across your desktop.") {
        SettingItem(label = "Theme", first = true, right = {
            PillSegmented(
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.LIGHT to "Light",
                ),
                selected = settings.themeMode,
                onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
            )
        })
    }

    SettingSection(title = "Behavior") {
        SettingItem(
            label = "Always on top",
            desc = "Keep the window above other apps when an approval arrives.",
            first = true,
            right = {
                DesignToggle(
                    checked = settings.alwaysOnTop,
                    onCheckedChange = { onSettingsChange(settings.copy(alwaysOnTop = it)) },
                )
            },
        )
        SettingItem(
            label = "Start on boot",
            right = {
                DesignToggle(
                    checked = settings.startOnBoot,
                    onCheckedChange = {
                        StartupManager.setStartOnBoot(it)
                        onSettingsChange(settings.copy(startOnBoot = it))
                    },
                )
            },
        )
        SettingItem(
            label = "Away mode",
            desc = "Disable timeouts while you're away. Approvals wait indefinitely.",
            right = {
                DesignToggle(
                    checked = settings.awayMode,
                    onCheckedChange = { onSettingsChange(settings.copy(awayMode = it)) },
                )
            },
        )
        SettingItem(
            label = "Prominent \"always allow\" button",
            desc = "Surface the sticky-approve option in the approval footer.",
            right = {
                DesignToggle(
                    checked = settings.prominentAlwaysAllow,
                    onCheckedChange = { onSettingsChange(settings.copy(prominentAlwaysAllow = it)) },
                )
            },
        )
    }

    SettingSection(title = "Server", desc = "Local approval endpoint used by your agents.") {
        SettingItem(label = "Listening port", first = true, right = {
            SettingsTextInput(
                value = settings.serverPort.toString(),
                onChange = { raw ->
                    raw.toIntOrNull()?.let { port ->
                        onSettingsChange(settings.copy(serverPort = port))
                    }
                },
                mono = true,
            )
        })
        SettingItem(
            label = "Bind address",
            desc = "\"All interfaces\" exposes the approval server to your LAN. " +
                "No authentication \u2014 only enable on trusted networks.",
            right = {
                PillSegmented(
                    options = listOf(
                        "127.0.0.1" to "Loopback",
                        "0.0.0.0" to "All interfaces",
                    ),
                    selected = settings.serverHost,
                    onSelect = { onSettingsChange(settings.copy(serverHost = it)) },
                )
            },
        )
        SettingItem(
            label = "Default timeout",
            desc = "How long an approval waits before the agent falls back to a default.",
            right = {
                SettingsTextInput(
                    value = settings.defaultTimeoutSeconds.toString(),
                    onChange = { raw ->
                        raw.toIntOrNull()?.let { t ->
                            onSettingsChange(settings.copy(defaultTimeoutSeconds = t))
                        }
                    },
                    suffix = "sec",
                    mono = true,
                    width = 140.dp,
                )
            },
        )
    }

    SettingSection(title = "Data", desc = "Stored locally; nothing leaves your machine.") {
        SettingItem(
            label = "History",
            desc = "$historyCount of ${settings.maxHistoryEntries} entries used.",
            first = true,
            right = { SettingsOutlineBtn(text = "Export JSON", onClick = {}) },
        )
        SettingItem(
            label = "Clear history",
            desc = "Permanently removes all recorded decisions and protections.",
            right = {
                SettingsGhostBtn(
                    text = "Clear\u2026",
                    color = DangerRed,
                    onClick = { showClearDialog = true },
                )
            },
        )
    }

    SettingSection(title = "Diagnostics") {
        SettingItem(
            label = "Verbose logging",
            desc = "Include commands, file paths, and request/response details in logs. " +
                "Keep off unless diagnosing an issue \u2014 sensitive content may appear in log files.",
            first = true,
            right = {
                DesignToggle(
                    checked = settings.verboseLogging,
                    onCheckedChange = { onSettingsChange(settings.copy(verboseLogging = it)) },
                )
            },
        )
    }

    SettingSection(title = "About", desc = "Agent Buddy v${com.mikepenz.agentbuddy.VERSION}") {
        SettingItem(
            label = "Open source libraries",
            desc = "View third-party licenses bundled with Agent Buddy.",
            first = true,
            right = { SettingsOutlineBtn(text = "View\u2026", onClick = onShowLicenses) },
        )
    }
}
