package com.mikepenz.agentbelay.hook

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodexBridgeInstallerTest {

    private lateinit var fakeHome: File
    private lateinit var originalHome: String

    @BeforeTest
    fun setUp() {
        originalHome = System.getProperty("user.home")
        fakeHome = File(System.getProperty("java.io.tmpdir"), "codex-bridge-test-${System.nanoTime()}")
        fakeHome.mkdirs()
        System.setProperty("user.home", fakeHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalHome)
        fakeHome.deleteRecursively()
    }

    private fun configFile() = File(fakeHome, ".codex/config.toml")

    @Test
    fun `managed block round-trips through strip`() {
        val original = """
            |# user keys above
            |model = "gpt-5"
            |
            |[some.user.section]
            |key = "value"
        """.trimMargin()

        val block = CodexBridgeInstaller.buildManagedBlock(port = 19532)
        val combined = original + "\n\n" + block + "\n"

        val extracted = CodexBridgeInstaller.extractManagedBlock(combined)
        assertNotNull(extracted, "marker block must be extractable")
        assertTrue(extracted.contains("[[hooks.PermissionRequest.hooks]]"))
        assertTrue(extracted.contains("[[hooks.PreToolUse.hooks]]"))
        assertTrue(extracted.contains("[[hooks.PostToolUse.hooks]]"))
        assertTrue(extracted.contains("codex-approve.sh"))
        assertTrue(extracted.contains("codex-pre-tool-use.sh"))
        assertTrue(extracted.contains("codex-post-tool-use.sh"))

        val stripped = CodexBridgeInstaller.stripManagedBlock(combined)
        assertFalse(stripped.contains(">>> agent-belay >>>"), "begin marker must be gone")
        assertFalse(stripped.contains("<<< agent-belay <<<"), "end marker must be gone")
        assertTrue(stripped.contains("model = \"gpt-5\""), "user content must be preserved")
        assertTrue(stripped.contains("[some.user.section]"), "user sections must be preserved")
    }

    @Test
    fun `strip is a no-op when no managed block is present`() {
        val text = """
            |[hooks]
            |custom = "thing"
        """.trimMargin()
        assertEquals(text, CodexBridgeInstaller.stripManagedBlock(text))
    }

    @Test
    fun `managed block uses Codex command hook shape`() {
        val block = CodexBridgeInstaller.buildManagedBlock(port = 24680)
        assertFalse(block.contains("type    = \"http\""))
        assertFalse(block.contains("url     = "))
        assertTrue(block.contains("type = \"command\""))
        assertTrue(block.contains("timeout = 300"))
    }

    @Test
    fun `managed block surfaces the Codex hooks-trust requirement`() {
        // Codex 0.135.0 (decompiled): non-managed command hooks don't run until
        // reviewed/trusted via the /hooks command (binary strings: `/hooks`,
        // `trust_hook`, `review hook`). The block must tell the user that.
        val block = CodexBridgeInstaller.buildManagedBlock(port = 19532)
        assertTrue(block.contains("/hooks"), "must point the user at the /hooks trust browser")
        assertTrue(block.contains("trust", ignoreCase = true))
    }

    @Test
    fun `register enables the features hooks flag`() {
        CodexBridgeInstaller.register(19532)
        val text = configFile().readText()
        // Codex gates hooks behind [features] hooks = true.
        assertTrue(Regex("""(?m)^\s*\[features]\s*$""").containsMatchIn(text), "must write [features] table: $text")
        assertTrue(Regex("""(?m)^\s*hooks\s*=\s*true\s*$""").containsMatchIn(text), "must set hooks = true: $text")
    }

    @Test
    fun `register migrates the deprecated codex_hooks alias to hooks`() {
        // Pre-seed a config using the deprecated `codex_hooks` feature flag.
        val cfg = configFile()
        cfg.parentFile.mkdirs()
        cfg.writeText("[features]\ncodex_hooks = true\n")

        CodexBridgeInstaller.register(19532)

        val text = cfg.readText()
        assertFalse(text.contains("codex_hooks"), "deprecated codex_hooks alias must be removed: $text")
        assertTrue(Regex("""(?m)^\s*hooks\s*=\s*true\s*$""").containsMatchIn(text))
    }

    @Test
    fun `managed block can include Codex capability hooks`() {
        val block = CodexBridgeInstaller.buildManagedBlock(
            port = 24680,
            includePermissionHooks = false,
            includeUserPromptSubmit = true,
            includeSessionStart = true,
        )

        assertFalse(block.contains("[[hooks.PermissionRequest]]"))
        assertTrue(block.contains("[[hooks.UserPromptSubmit]]"))
        assertTrue(block.contains("[[hooks.SessionStart]]"))
        assertTrue(block.contains("codex-user-prompt-submit.sh"))
        assertTrue(block.contains("codex-session-start.sh"))
    }
}
