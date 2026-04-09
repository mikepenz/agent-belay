package com.mikepenz.agentapprover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.hook.CopilotBridgeInstaller
import com.mikepenz.agentapprover.hook.HookRegistrar
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.ui.approvals.ApprovalsTab
import com.mikepenz.agentapprover.ui.approvals.ApprovalsViewModel
import com.mikepenz.agentapprover.ui.history.HistoryTab
import com.mikepenz.agentapprover.ui.protectionlog.ProtectionLogTab
import com.mikepenz.agentapprover.ui.settings.SettingsTab
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun App(
    stateManager: AppStateManager,
    hookRegistrar: HookRegistrar,
    copilotModels: List<Pair<String, String>> = emptyList(),
    copilotInitState: CopilotInitState = CopilotInitState.IDLE,
    devMode: Boolean = false,
    onPopOut: ((title: String, content: String) -> Unit)? = null,
    onShowLicenses: () -> Unit = {},
    protectionModules: List<ProtectionModule> = emptyList(),
    protectionEngine: ProtectionEngine? = null,
) {
    val state by stateManager.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isCopilotInstalled by remember { mutableStateOf(CopilotBridgeInstaller.isInstalled()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.settings.awayMode) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Away Mode — Timeouts disabled",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        val tabLabels = buildList {
            add("Approvals")
            add("History")
            if (devMode) add("Protection Log")
            add("Settings")
        }
        val protectionLogIndex = if (devMode) 2 else -1
        val settingsIndex = if (devMode) 3 else 2

        TabRow(selectedTabIndex = selectedTab) {
            tabLabels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        when (label) {
                            "Approvals" -> {
                                if (state.pendingApprovals.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Approvals")
                                        Badge { Text("${state.pendingApprovals.size}") }
                                    }
                                } else {
                                    Text("Approvals")
                                }
                            }
                            "Protection Log" -> {
                                if (state.preToolUseLog.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Protection Log", fontSize = 12.sp)
                                        Badge { Text("${state.preToolUseLog.size}") }
                                    }
                                } else {
                                    Text("Protection Log", fontSize = 12.sp)
                                }
                            }
                            else -> Text(label)
                        }
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> {
                val approvalsViewModel: ApprovalsViewModel = metroViewModel()
                val approvalsUi by approvalsViewModel.uiState.collectAsState()
                val pendingApprovals by approvalsViewModel.pendingApprovals.collectAsState()
                val approvalsSettings by approvalsViewModel.settings.collectAsState()
                val approvalsRiskResults by approvalsViewModel.riskResults.collectAsState()
                ApprovalsTab(
                    pendingApprovals = pendingApprovals,
                    riskResults = approvalsRiskResults,
                    riskStatuses = approvalsUi.riskStatuses,
                    riskErrors = approvalsUi.riskErrors,
                    settings = approvalsSettings,
                    onApprove = approvalsViewModel::onApprove,
                    onAlwaysAllow = approvalsViewModel::onAlwaysAllow,
                    onDeny = approvalsViewModel::onDeny,
                    onApproveWithInput = approvalsViewModel::onApproveWithInput,
                    onDismiss = approvalsViewModel::onDismiss,
                    autoDenyRequests = approvalsUi.autoDenyRequests,
                    onCancelAutoDeny = approvalsViewModel::onCancelAutoDeny,
                    onUserInteraction = approvalsViewModel::onUserInteraction,
                    onPopOut = onPopOut,
                    onSettingsChange = approvalsViewModel::onSettingsChange,
                )
            }

            1 -> HistoryTab(
                history = state.history,
                onReplay = if (devMode) { result ->
                    val cloned = result.request.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = kotlinx.datetime.Clock.System.now(),
                    )
                    stateManager.addPending(cloned)
                    selectedTab = 0
                } else null,
            )

            protectionLogIndex -> {
                if (protectionEngine != null) {
                    ProtectionLogTab(
                        events = state.preToolUseLog,
                        protectionEngine = protectionEngine,
                        onClear = { stateManager.clearPreToolUseLog() },
                    )
                }
            }

            settingsIndex -> {
                var isHookRegistered by remember { mutableStateOf(hookRegistrar.isRegistered(state.settings.serverPort)) }

                // Re-check when port changes
                LaunchedEffect(state.settings.serverPort) {
                    isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                }

                SettingsTab(
                    settings = state.settings,
                    isHookRegistered = isHookRegistered,
                    isCopilotInstalled = isCopilotInstalled,
                    historyCount = state.history.size,
                    copilotModels = copilotModels,
                    copilotInitState = copilotInitState,
                    onSettingsChange = { stateManager.updateSettings(it) },
                    onRegisterHook = {
                        hookRegistrar.register(state.settings.serverPort)
                        isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                    },
                    onUnregisterHook = {
                        hookRegistrar.unregister(state.settings.serverPort)
                        isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                    },
                    onInstallCopilot = {
                        CopilotBridgeInstaller.install()
                        isCopilotInstalled = CopilotBridgeInstaller.isInstalled()
                    },
                    onUninstallCopilot = {
                        CopilotBridgeInstaller.uninstall()
                        isCopilotInstalled = CopilotBridgeInstaller.isInstalled()
                    },
                    onRegisterCopilotHook = { path -> CopilotBridgeInstaller.registerHook(path) },
                    onUnregisterCopilotHook = { path -> CopilotBridgeInstaller.unregisterHook(path) },
                    isCopilotHookRegistered = { path -> CopilotBridgeInstaller.isHookRegistered(path) },
                    onClearHistory = { stateManager.clearHistory() },
                    onShowLicenses = onShowLicenses,
                    protectionModules = protectionModules,
                    onProtectionSettingsChange = { newProtectionSettings ->
                        stateManager.updateSettings(state.settings.copy(protectionSettings = newProtectionSettings))
                    },
                )
            }
        }
    }
}

