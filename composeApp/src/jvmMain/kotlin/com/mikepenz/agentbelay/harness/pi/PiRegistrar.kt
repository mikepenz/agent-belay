package com.mikepenz.agentbelay.harness.pi

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.PiBridgeInstaller
import java.io.File

/**
 * Registers Belay by installing a global Pi extension under
 * `~/.pi/agent/extensions/`.
 */
class PiRegistrar : HarnessRegistrar {
    override val displayName: String = "Pi"

    override fun isRegistered(port: Int): Boolean = PiBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = PiBridgeInstaller.register(port)
    override fun unregister(port: Int) = PiBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val extensionFile = File(home, ".pi/agent/extensions/agent-belay.ts")
        return listOf(
            OutboardArtifact.JsonFile(
                path = extensionFile.toPath(),
                contents = "(installed by PiBridgeInstaller - TypeScript extension posting to /approve-pi)",
            ),
        )
    }
}
