package com.mikepenz.agentbelay.hook

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntigravityBridgeInstallerTest {

    private lateinit var originalUserHome: String
    private lateinit var tempHome: File

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("antigravity-bridge-test-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalUserHome)
        tempHome.deleteRecursively()
    }

    @Test
    fun testRegisterAndUnregister() {
        val port = 19532
        assertFalse(AntigravityBridgeInstaller.isRegistered(port))

        // Register
        AntigravityBridgeInstaller.register(port)
        assertTrue(AntigravityBridgeInstaller.isRegistered(port))

        // Verify config exists and contains the correct hook nested under "PreToolUse" -> "hooks"
        val config = File(tempHome, ".gemini/antigravity-cli/hooks.json")
        assertTrue(config.exists())
        val configText = config.readText()
        println("DEBUG CONFIG TEXT:\n$configText")
        assertTrue(configText.contains("antigravity-pre-tool-use.sh"))
        assertTrue(configText.contains("\"PreToolUse\""))
        assertTrue(configText.contains("\"hooks\""))
        assertTrue(configText.contains("\"type\": \"command\""))

        // Verify bridge script exists and is executable
        val script = File(tempHome, ".agent-belay/antigravity-pre-tool-use.sh")
        assertTrue(script.exists())
        assertTrue(script.canExecute())

        // Unregister
        AntigravityBridgeInstaller.unregister(port)
        assertFalse(AntigravityBridgeInstaller.isRegistered(port))
        assertFalse(script.exists())
        assertFalse(config.exists()) // Deleted because it was empty
    }

    @Test
    fun testMigrationFromLegacyGemini() {
        val port = 19532
        
        // Setup legacy config (previously written to wrong path .gemini/config/hooks.json)
        val legacyConfigDir = File(tempHome, ".gemini/config")
        legacyConfigDir.mkdirs()
        val legacyConfig = File(legacyConfigDir, "hooks.json")
        legacyConfig.writeText("""
            {
              "agent-belay": {
                "enabled": true,
                "PreToolUse": [
                  {
                    "matcher": ".*",
                    "hooks": [
                      {
                        "type": "command",
                        "command": "${tempHome.absolutePath}/.agent-belay/antigravity-pre-tool-use.sh"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent())

        val legacyShim1 = File(tempHome, ".agent-belay/gemini-pre-tool-use.sh")
        legacyShim1.parentFile.mkdirs()
        legacyShim1.writeText("echo legacy shim")

        val legacyShim2 = File(tempHome, ".agent-approver/gemini-pre-tool-use.sh")
        legacyShim2.parentFile.mkdirs()
        legacyShim2.writeText("echo legacy shim")

        assertTrue(legacyConfig.exists())
        assertTrue(legacyShim1.exists())
        assertTrue(legacyShim2.exists())

        // Register Antigravity
        AntigravityBridgeInstaller.register(port)

        // Verify Antigravity is registered at the correct new location
        assertTrue(AntigravityBridgeInstaller.isRegistered(port))

        // Verify legacy files are cleaned up
        assertFalse(legacyShim1.exists())
        assertFalse(legacyShim2.exists())
        assertFalse(legacyConfig.exists()) // Deleted because it was the wrong path
        assertFalse(legacyConfigDir.exists()) // .gemini/config dir deleted (now empty)
    }
}
