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

        // Verify config exists and contains the correct hook
        val config = File(tempHome, ".antigravitycli/settings.json")
        assertTrue(config.exists())
        val configText = config.readText()
        assertTrue(configText.contains("antigravity-pre-tool-use.sh"))

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
        
        // Setup legacy Gemini config
        val legacyConfigDir = File(tempHome, ".gemini")
        legacyConfigDir.mkdirs()
        val legacyConfig = File(legacyConfigDir, "settings.json")
        legacyConfig.writeText("""
            {
              "BeforeTool": [
                {
                  "matcher": ".*",
                  "command": "${tempHome.absolutePath}/.agent-belay/gemini-pre-tool-use.sh"
                }
              ]
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

        // Verify Antigravity is registered
        assertTrue(AntigravityBridgeInstaller.isRegistered(port))

        // Verify legacy Gemini files are cleaned up!
        assertFalse(legacyShim1.exists())
        assertFalse(legacyShim2.exists())
        assertFalse(legacyConfig.exists())
        assertFalse(legacyConfigDir.exists()) // Directory deleted if empty
    }
}
