package com.mikepenz.agentbelay.harness.antigravity

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.AntigravityBridgeInstaller
import java.io.File

class AntigravityRegistrar : HarnessRegistrar {
    override val displayName: String = "Antigravity"

    override fun isRegistered(port: Int): Boolean = AntigravityBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = AntigravityBridgeInstaller.register(port)
    override fun unregister(port: Int) = AntigravityBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val configFile = File(home, ".gemini/antigravity-cli/hooks.json")
        return listOf(
            OutboardArtifact.JsonFile(
                path = configFile.toPath(),
                contents = "(PreToolUse command hook in ${configFile.absolutePath} pointing to bridge script)",
            ),
        )
    }
}
