package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipedTailHeadModuleTest {

    private val module = PipedTailHeadModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/project",
    )

    private fun evaluateRule(ruleId: String, command: String) =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("piped_tail_head", module.id)
        assertTrue(module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(2, module.rules.size)
    }

    // --- piped_tail ---

    @Test
    fun pipedTailDetected() {
        assertNotNull(evaluateRule("piped_tail", "cat file.txt | tail -n 20"))
    }

    @Test
    fun pipedTailWithSpaces() {
        assertNotNull(evaluateRule("piped_tail", "grep foo bar.log |  tail -5"))
    }

    @Test
    fun pipedTailInChain() {
        assertNotNull(evaluateRule("piped_tail", "find . -name '*.kt' | sort | tail -20"))
    }

    @Test
    fun tailOnFileAllowed() {
        assertNull(evaluateRule("piped_tail", "tail -n 20 /tmp/output.log"))
    }

    @Test
    fun tailWithoutPipeAllowed() {
        assertNull(evaluateRule("piped_tail", "tail -f server.log"))
    }

    // --- piped_head ---

    @Test
    fun pipedHeadDetected() {
        assertNotNull(evaluateRule("piped_head", "cat file.txt | head -n 10"))
    }

    @Test
    fun pipedHeadWithSpaces() {
        assertNotNull(evaluateRule("piped_head", "ls -la |  head -5"))
    }

    @Test
    fun pipedHeadInChain() {
        assertNotNull(evaluateRule("piped_head", "find . -type f | sort | head -20"))
    }

    @Test
    fun headOnFileAllowed() {
        assertNull(evaluateRule("piped_head", "head -n 10 /tmp/output.log"))
    }

    @Test
    fun headWithoutPipeAllowed() {
        assertNull(evaluateRule("piped_head", "head -20 README.md"))
    }

    // --- Non-Bash tool ---

    @Test
    fun nonBashToolIgnored() {
        val input = HookInput(
            sessionId = "test-session",
            toolName = "Edit",
            toolInput = mapOf("command" to JsonPrimitive("cat file | tail")),
            cwd = "/project",
        )
        module.rules.forEach { rule ->
            assertNull(rule.evaluate(input))
        }
    }
}
