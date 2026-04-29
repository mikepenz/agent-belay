package com.mikepenz.agentbelay

import androidx.compose.ui.window.application
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppGraph
import com.mikepenz.agentbelay.storage.LegacyDataMigration
import com.mikepenz.agentbelay.ui.AgentBelayShell
import com.mikepenz.agentbelay.ui.theme.configureLogging
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun getAppDataDir(): String = appDataDirFor("AgentBelay")

private fun getAgentBuddyAppDataDir(): String = appDataDirFor("AgentBuddy")

private fun getAgentApproverAppDataDir(): String = appDataDirFor("AgentApprover")

private fun appDataDirFor(brand: String): String {
    val osName = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        osName.contains("mac") -> "$home/Library/Application Support/$brand"
        osName.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/$brand"
        else -> "$home/.local/share/$brand"
    }
}

fun main(args: Array<String>) {
    // Enable macOS template images so the tray icon adapts to menu bar background colour.
    System.setProperty("apple.awt.enableTemplateImages", "true")
    configureLogging()

    val devMode = "--dev" in args || System.getProperty("agentbelay.devmode") == "true"

    // One-shot migrations from previous brands. The app has been renamed twice
    // — Agent Approver → Agent Buddy → Agent Belay — so we run one step per
    // legacy brand in chronological order. Idempotent: each step no-ops on
    // fresh installs and after first migration.
    LegacyDataMigration.run(
        LegacyDataMigration.Step(
            legacyDataDir = getAgentApproverAppDataDir(),
            newDataDir = getAgentBuddyAppDataDir(),
            legacyDbFile = "agent-approver.db",
            newDbFile = "agent-buddy.db",
            legacyHookDirName = ".agent-approver",
            newHookDirName = ".agent-buddy",
            legacyCopilotHookFile = "agent-approver.json",
            newCopilotHookFile = "agent-buddy.json",
        ),
    )
    LegacyDataMigration.run(
        LegacyDataMigration.Step(
            legacyDataDir = getAgentBuddyAppDataDir(),
            newDataDir = getAppDataDir(),
            legacyDbFile = "agent-buddy.db",
            newDbFile = "agent-belay.db",
            legacyHookDirName = ".agent-buddy",
            newHookDirName = ".agent-belay",
            legacyCopilotHookFile = "agent-buddy.json",
            newCopilotHookFile = "agent-belay.json",
        ),
    )

    val dataDir = getAppDataDir().also { File(it).mkdirs() }

    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val graph: AppGraph = createGraphFactory<AppGraph.Factory>().create(
        AppEnvironment(dataDir = dataDir, devMode = devMode, appScope = appScope),
    )

    application {
        AgentBelayShell(graph = graph, devMode = devMode, exitApplication = ::exitApplication)
    }
}
