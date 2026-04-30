package com.mikepenz.agentbelay.harness

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shared contract every [Harness] implementation must satisfy.
 *
 * The [HarnessAdapter] / [HarnessTransport] / [HarnessCapabilities] surface
 * is small but invariants leak between layers — e.g. "if
 * `supportsArgRewriting` is true, [HarnessAdapter.buildPermissionAllowResponse]
 * must surface `updatedInput`." A concrete subclass plugs in [harness] plus
 * a small set of envelope-shape assertions and gets ~15 round-trip cases
 * for free.
 *
 * To add a new harness:
 *  1. Drop golden payload fixtures into
 *     `composeApp/src/jvmTest/resources/harnesses/<name>/`.
 *  2. Subclass [AbstractHarnessContractTest], override [harness], the four
 *     payload accessors, and the three envelope assertions.
 *  3. Run — failures point at exactly which contract bit drifted.
 *
 * Phase-2 harnesses (Codex / Cline / Cursor / Gemini / OpenCode) get full
 * coverage by adding ~30 lines of subclass plus a fixture directory.
 */
abstract class AbstractHarnessContractTest {

    protected abstract val harness: Harness

    // ─── Subclass-supplied payload accessors ────────────────────────────────

    /** A well-formed permissionRequest payload for a typical Bash command. */
    protected abstract fun goldenBashPermissionRequest(): String

    /** A well-formed permissionRequest payload for an Edit/Write tool — must
     *  carry a writable `tool_input` so arg-rewriting checks have a field to
     *  rewrite. */
    protected abstract fun goldenEditPermissionRequest(): String

    /** A payload missing required fields — adapter must return null. */
    protected abstract fun goldenMalformedPermissionRequest(): String

    /** A well-formed preToolUse payload (may share schema with permissionRequest
     *  on harnesses that use one canonical format). */
    protected abstract fun goldenPreToolUsePayload(): String

    // ─── Subclass-supplied envelope shape checks ────────────────────────────

    /** Assert that [responseJson] is a valid "allow" envelope for this
     *  harness. Implementations check whatever flat or nested shape the
     *  harness expects. */
    protected abstract fun assertPermissionAllowEnvelope(responseJson: String)

    /** Assert that [responseJson] is a valid "deny" envelope and that the
     *  user-facing message is preserved as [expectedMessage]. */
    protected abstract fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String)

    /** Assert that [responseJson] carries the rewritten args under whatever
     *  key this harness uses (`updatedInput`, `modifiedArgs`, …). Only called
     *  when [HarnessCapabilities.supportsArgRewriting] is `true`. */
    protected abstract fun assertContainsUpdatedInput(responseJson: String, key: String, value: String)

    // ─── Parse contract ─────────────────────────────────────────────────────

    @Test
    fun `parsePermissionRequest extracts canonical fields from a Bash payload`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())
        assertNotNull(req, "well-formed Bash payload must parse")
        assertEquals(harness.source, req.source, "parsed source must match the harness's declared source")
        assertEquals("Bash", req.hookInput.toolName, "Bash should be the canonical name regardless of native casing")
        assertTrue(req.hookInput.sessionId.isNotBlank(), "sessionId must be populated (real or generated)")
        assertEquals(goldenBashPermissionRequest(), req.rawRequestJson, "rawRequestJson must round-trip verbatim")
    }

    @Test
    fun `parsePermissionRequest preserves toolInput payload`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())
        assertNotNull(req)
        // Different harnesses spell the field differently in raw JSON, but the
        // canonical hookInput.toolInput must contain *something*.
        assertTrue(req.hookInput.toolInput.isNotEmpty(), "toolInput must be populated")
    }

    @Test
    fun `parsePermissionRequest returns null on malformed payload`() {
        assertNull(
            harness.adapter.parsePermissionRequest(goldenMalformedPermissionRequest()),
            "missing required fields must produce a null parse result, not an exception",
        )
    }

    @Test
    fun `parsePreToolUse handles its golden payload`() {
        val req = harness.adapter.parsePreToolUse(goldenPreToolUsePayload())
        assertNotNull(req, "well-formed preToolUse payload must parse")
        assertEquals(harness.source, req.source)
    }

    // ─── Build contract ─────────────────────────────────────────────────────

    @Test
    fun `buildPermissionAllowResponse produces a valid envelope`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())!!
        val response = harness.adapter.buildPermissionAllowResponse(req, updatedInput = null)
        assertPermissionAllowEnvelope(response)
    }

    @Test
    fun `buildPermissionDenyResponse carries the user-facing message`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())!!
        val message = "blocked by policy: dangerous rm"
        val response = harness.adapter.buildPermissionDenyResponse(req, message)
        assertPermissionDenyEnvelope(response, message)
    }

    @Test
    fun `buildPermissionAlwaysAllowResponse always parses as allow`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())!!
        val response = harness.adapter.buildPermissionAlwaysAllowResponse(req, suggestions = emptyList())
        // Whether or not the harness honors `updatedPermissions`, the always-
        // allow path must always be a valid allow at the envelope level.
        assertPermissionAllowEnvelope(response)
    }

    @Test
    fun `buildPreToolUseAllowResponse and buildPreToolUseDenyResponse don't throw`() {
        // We don't assert envelope shape here (it's harness-specific and
        // already checked by HarnessCapabilitiesTest); the contract only
        // guarantees these methods produce *some* JSON without crashing.
        val allow = harness.adapter.buildPreToolUseAllowResponse()
        val deny = harness.adapter.buildPreToolUseDenyResponse("nope")
        assertTrue(allow.isNotBlank())
        assertTrue(deny.isNotBlank())
        assertContains(deny, "nope", message = "deny payload should preserve the reason text somewhere")
    }

    // ─── Capability flag contract ───────────────────────────────────────────

    @Test
    fun `supportsArgRewriting matches buildPermissionAllowResponse behavior`() {
        val req = harness.adapter.parsePermissionRequest(goldenEditPermissionRequest())!!
        val rewritten = mapOf("file_path" to JsonPrimitive("/safe/rewritten/path.kt"))
        val response = harness.adapter.buildPermissionAllowResponse(req, rewritten)

        if (harness.capabilities.supportsArgRewriting) {
            assertContainsUpdatedInput(response, "file_path", "/safe/rewritten/path.kt")
        } else {
            assertFalse(
                response.contains("/safe/rewritten/path.kt"),
                "harness without supportsArgRewriting must drop updatedInput silently, not embed it",
            )
        }
    }

    @Test
    fun `supportsOutputRedaction matches buildPostToolUseRedactedResponse behavior`() {
        val redacted = buildJsonObject {
            put("stdout", JsonPrimitive("[REDACTED:test]"))
        }
        val response = harness.adapter.buildPostToolUseRedactedResponse(redacted)

        if (harness.capabilities.supportsOutputRedaction) {
            assertNotNull(response, "harness with supportsOutputRedaction=true must build a non-null response")
            assertContains(response, "[REDACTED:test]", message = "redacted payload must be embedded in the response")
        } else {
            assertNull(
                response,
                "harness without supportsOutputRedaction must return null sentinel — callers pass the original through",
            )
        }
    }

    // Note: `supportsInterruptOnDeny` is intentionally NOT asserted here.
    // The flag is semantic — it means "deny aborts the tool call" — but the
    // wire-level expression varies: Copilot uses an explicit
    // `interrupt: true`, Claude Code's `behavior: "deny"` is *implicitly* an
    // interrupt with no extra field. HarnessCapabilitiesTest covers each
    // harness's explicit-vs-implicit shape; folding that check into the
    // contract would force every harness to lie one way or the other.

    // ─── Transport contract ────────────────────────────────────────────────

    @Test
    fun `transport endpoints all start with a slash`() {
        harness.transport.endpoints().values.forEach { path ->
            assertTrue(path.startsWith("/"), "endpoint '$path' must start with /")
        }
    }

    @Test
    fun `transport endpoints are unique within the harness`() {
        val paths = harness.transport.endpoints().values.toList()
        assertEquals(
            paths.size, paths.toSet().size,
            "endpoint paths must be unique within a harness (got $paths)",
        )
    }

    @Test
    fun `transport declares at least one PERMISSION_REQUEST endpoint`() {
        assertNotNull(
            harness.transport.endpoints()[HookEvent.PERMISSION_REQUEST],
            "every harness must terminate PERMISSION_REQUEST somewhere",
        )
    }

    // ─── Harness identity ──────────────────────────────────────────────────

    @Test
    fun `harness source matches what the adapter stamps onto parsed requests`() {
        val req = harness.adapter.parsePermissionRequest(goldenBashPermissionRequest())!!
        assertEquals(
            harness.source, req.source,
            "Harness.source and ApprovalRequest.source must agree — drift here breaks history filtering",
        )
    }
}
