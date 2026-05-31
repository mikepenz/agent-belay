package com.mikepenz.agentbelay.harness.hermes

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.HermesBridgeInstaller
import java.io.File

class HermesRegistrar : HarnessRegistrar {
    override val displayName: String = "Hermes"

    override fun isRegistered(port: Int): Boolean = HermesBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = HermesBridgeInstaller.register(port)
    override fun unregister(port: Int) = HermesBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val configFile = File(home, ".hermes/config.yaml")
        return listOf(
            OutboardArtifact.GenericFile(
                path = configFile.toPath(),
                contents = "(managed block in ${configFile.absolutePath} pointing at /pre-tool-use-hermes and /post-tool-use-hermes on port $port; first run prompts for allowlist consent — set HERMES_ACCEPT_HOOKS=1 for non-TTY/gateway sessions)",
                format = "yaml",
            ),
        )
    }
}
