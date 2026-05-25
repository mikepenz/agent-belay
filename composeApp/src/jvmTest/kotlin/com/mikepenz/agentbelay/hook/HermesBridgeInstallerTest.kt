package com.mikepenz.agentbelay.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HermesBridgeInstallerTest {

    @Test
    fun `managed block round-trips through strip`() {
        val original = """
            |# user keys above
            |model: "hermes-3"
            |
            |some_user_section:
            |  key: "value"
        """.trimMargin()

        val block = HermesBridgeInstaller.buildManagedBlock(
            port = 19532,
            includeMainHooks = true,
            includeUserPromptSubmit = true,
            includeSessionStart = true,
        )
        val combined = original + "\n\n" + block + "\n"

        val extracted = HermesBridgeInstaller.extractManagedBlock(combined)
        assertNotNull(extracted, "marker block must be extractable")
        assertTrue(extracted.contains("pre_tool_call:"))
        assertTrue(extracted.contains("post_tool_call:"))
        assertTrue(extracted.contains("pre_llm_call:"))
        assertTrue(extracted.contains("on_session_start:"))
        assertTrue(extracted.contains("hermes-pre-tool-call.sh"))
        assertTrue(extracted.contains("hermes-post-tool-call.sh"))
        assertTrue(extracted.contains("hermes-user-prompt-submit.sh"))
        assertTrue(extracted.contains("hermes-session-start.sh"))

        val stripped = HermesBridgeInstaller.stripManagedBlock(combined)
        assertFalse(stripped.contains(">>> agent-belay >>>"), "begin marker must be gone")
        assertFalse(stripped.contains("<<< agent-belay <<<"), "end marker must be gone")
        assertTrue(stripped.contains("model: \"hermes-3\""), "user content must be preserved")
        assertTrue(stripped.contains("some_user_section:"), "user sections must be preserved")
    }

    @Test
    fun `strip is a no-op when no managed block is present`() {
        val text = """
            |hooks:
            |  custom: "thing"
        """.trimMargin()
        assertEquals(text, HermesBridgeInstaller.stripManagedBlock(text))
    }

    @Test
    fun `managed block uses Hermes command hook shape`() {
        val block = HermesBridgeInstaller.buildManagedBlock(
            port = 24680,
            includeMainHooks = true,
            includeUserPromptSubmit = true,
        )
        assertTrue(block.contains("command: "))
        assertTrue(block.contains("timeout: 300"))
        assertTrue(block.contains("pre_llm_call:"))
    }
}
