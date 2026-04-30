package com.mikepenz.agentbelay.hook

import com.mikepenz.agentbelay.testutil.withTempHome
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiBridgeInstallerTest {

    private val port = 19532

    @Test
    fun `register writes global Pi extension`() = withTempHome { home ->
        assertFalse(PiBridgeInstaller.isRegistered(port))

        PiBridgeInstaller.register(port)

        val extension = home.resolve(".pi/agent/extensions/agent-belay.ts").toFile()
        assertTrue(extension.exists())
        assertTrue(PiBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `extension targets approve-pi endpoint with registered port`() = withTempHome { home ->
        PiBridgeInstaller.register(20001)

        val body = home.resolve(".pi/agent/extensions/agent-belay.ts").toFile().readText()
        assertTrue("localhost:20001" in body)
        assertTrue("/approve-pi" in body)
        assertTrue("pi.on(\"tool_call\"" in body)
        assertTrue("return { block: true, reason }" in body)
    }

    @Test
    fun `register is idempotent and refreshes port`() = withTempHome { home ->
        PiBridgeInstaller.register(19000)
        PiBridgeInstaller.register(19001)

        val body = home.resolve(".pi/agent/extensions/agent-belay.ts").toFile().readText()
        assertTrue("localhost:19001" in body)
        assertFalse("localhost:19000" in body)
    }

    @Test
    fun `unregister removes extension`() = withTempHome { home ->
        PiBridgeInstaller.register(port)
        assertTrue(PiBridgeInstaller.isRegistered(port))

        PiBridgeInstaller.unregister(port)

        assertFalse(home.resolve(".pi/agent/extensions/agent-belay.ts").toFile().exists())
        assertFalse(PiBridgeInstaller.isRegistered(port))
    }
}
