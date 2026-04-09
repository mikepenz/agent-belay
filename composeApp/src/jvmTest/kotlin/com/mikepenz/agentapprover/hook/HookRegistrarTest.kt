package com.mikepenz.agentapprover.hook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookRegistrarTest {

    private lateinit var tempDir: File
    private lateinit var settingsFile: File
    private lateinit var originalHome: String
    private val port = 19532

    @BeforeTest
    fun setUp() {
        originalHome = System.getProperty("user.home")
        tempDir = File(System.getProperty("java.io.tmpdir"), "hook-registrar-test-${System.nanoTime()}")
        tempDir.mkdirs()

        // Redirect HookRegistrar to use our temp settings file
        // Since HookRegistrar reads from ~/.claude/settings.json, we set up that structure
        val claudeDir = File(tempDir, ".claude")
        claudeDir.mkdirs()
        settingsFile = File(claudeDir, "settings.json")

        // Override system property for test isolation
        System.setProperty("user.home", tempDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        // Restore original user.home before cleanup
        System.setProperty("user.home", originalHome)
        tempDir.deleteRecursively()
    }

    @Test
    fun isRegisteredReturnsFalseWhenNoFile() {
        assertFalse(HookRegistrar.isRegistered(port))
    }

    @Test
    fun registerCreatesSettingsWithAllThreeHooks() {
        HookRegistrar.register(port)

        assertTrue(settingsFile.exists())
        val root = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject

        // PermissionRequest hook should exist
        val permEntries = hooks["PermissionRequest"]!!.jsonArray
        assertTrue(permEntries.isNotEmpty())
        val permHook = permEntries[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject
        assertTrue(permHook["url"].toString().contains("/approve"))

        // PreToolUse hook should exist
        val ptuEntries = hooks["PreToolUse"]!!.jsonArray
        assertTrue(ptuEntries.isNotEmpty())
        val ptuHook = ptuEntries[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject
        assertTrue(ptuHook["url"].toString().contains("/pre-tool-use"))

        // PostToolUse hook should exist (correlation channel for stale entries)
        val postEntries = hooks["PostToolUse"]!!.jsonArray
        assertTrue(postEntries.isNotEmpty())
        val postHook = postEntries[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject
        assertTrue(postHook["url"].toString().contains("/post-tool-use"))
    }

    @Test
    fun isRegisteredReturnsTrueAfterRegister() {
        HookRegistrar.register(port)
        assertTrue(HookRegistrar.isRegistered(port))
    }

    @Test
    fun registerIsIdempotent() {
        HookRegistrar.register(port)
        HookRegistrar.register(port)

        val root = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject
        val permEntries = hooks["PermissionRequest"]!!.jsonArray
        // Should still be exactly 1 entry, not duplicated
        assertTrue(permEntries.size == 1)
    }

    @Test
    fun unregisterRemovesHooks() {
        HookRegistrar.register(port)
        assertTrue(HookRegistrar.isRegistered(port))

        HookRegistrar.unregister(port)
        assertFalse(HookRegistrar.isRegistered(port))
    }

    @Test
    fun unregisterOnEmptyFileIsNoOp() {
        // Should not throw
        HookRegistrar.unregister(port)
        assertFalse(HookRegistrar.isRegistered(port))
    }

    @Test
    fun registerPreservesExistingSettings() {
        // Write a settings file with some existing content
        settingsFile.parentFile.mkdirs()
        settingsFile.writeText("""{"env":{"KEY":"value"}}""")

        HookRegistrar.register(port)

        val root = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        // Existing keys should be preserved
        assertTrue(root.containsKey("env"))
        assertTrue(root.containsKey("hooks"))
    }

    @Test
    fun registerPreservesExistingHooks() {
        // Write settings with existing hooks for a different event
        settingsFile.parentFile.mkdirs()
        settingsFile.writeText(
            """
            {
                "hooks": {
                    "PostToolUse": [{"matcher": "", "hooks": [{"type": "command", "command": "echo done"}]}]
                }
            }
            """.trimIndent()
        )

        HookRegistrar.register(port)

        val root = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject
        // Existing hook event should be preserved
        assertTrue(hooks.containsKey("PostToolUse"))
        // Our hooks should also exist
        assertTrue(hooks.containsKey("PermissionRequest"))
        assertTrue(hooks.containsKey("PreToolUse"))
    }

    @Test
    fun unregisterPreservesOtherHookEvents() {
        // Register, then add another hook event, then unregister
        settingsFile.parentFile.mkdirs()
        settingsFile.writeText(
            """
            {
                "hooks": {
                    "PostToolUse": [{"matcher": "", "hooks": [{"type": "command", "command": "echo done"}]}]
                }
            }
            """.trimIndent()
        )

        HookRegistrar.register(port)
        HookRegistrar.unregister(port)

        val root = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject
        // Other events should remain
        assertTrue(hooks.containsKey("PostToolUse"))
    }
}
