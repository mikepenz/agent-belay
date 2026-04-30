package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeHarness
import com.mikepenz.agentbelay.harness.copilot.CopilotHarness
import com.mikepenz.agentbelay.harness.pi.PiHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * For every JSON fixture under `resources/harnesses/<harness>/`, asserts the
 * harness's adapter parses it without throwing — non-malformed payloads must
 * yield a non-null [com.mikepenz.agentbelay.model.ApprovalRequest], malformed
 * payloads (filename contains `malformed`) must yield null.
 *
 * Adding a new captured payload to the fixtures dir extends test coverage
 * automatically — no test code edits required. Failures aggregate into one
 * line per offender so a regression in one fixture doesn't hide regressions
 * in the rest.
 */
class GoldenRoundTripTest {

    @Test
    fun `every claudecode fixture parses cleanly`() {
        roundTripAll("claudecode") { ClaudeCodeHarness().adapter.parsePermissionRequest(it) }
    }

    @Test
    fun `every copilot fixture parses cleanly`() {
        roundTripAll("copilot") { CopilotHarness().adapter.parsePermissionRequest(it) }
    }

    @Test
    fun `every pi fixture parses cleanly`() {
        roundTripAll("pi") { PiHarness().adapter.parsePermissionRequest(it) }
    }

    private fun roundTripAll(harness: String, parse: (String) -> Any?) {
        val failures = mutableListOf<String>()
        for (path in GoldenPayloads.listFor(harness)) {
            val payload = GoldenPayloads.read(path)
            val result = runCatching { parse(payload) }
                .getOrElse { failures += "$path: threw ${it::class.simpleName}: ${it.message}"; continue }

            val expectMalformed = "malformed" in path
            try {
                if (expectMalformed) assertNull(result, path)
                else assertNotNull(result, path)
            } catch (e: AssertionError) {
                failures += "$path: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) fail("Fixture round-trip failures:\n" + failures.joinToString("\n"))
    }
}
