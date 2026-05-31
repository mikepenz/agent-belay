package com.mikepenz.agentbelay.harness.codex

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source

/**
 * Composition root for the OpenAI Codex CLI integration. Codex's hooks
 * crate is deliberately Claude-Code-compatible (matching event names,
 * matcher group config, `apply_patch` ↔ `Write`/`Edit` aliasing), so
 * this harness reuses Claude's envelope shape almost verbatim. The
 * delta lives in the registrar (TOML config under `~/.codex/config.toml`
 * via a managed-block read-modify-write) and the route namespace.
 *
 * Shipped as **experimental** — see Settings → Integrations. The
 * adapter is unverified against a live Codex install. PostToolUse events
 * are received for cleanup/observability, but output redaction stays off
 * until Codex output mutation is wired through.
 */
class CodexHarness(
    override val adapter: HarnessAdapter = CodexAdapter(),
    override val registrar: HarnessRegistrar = CodexRegistrar(),
    override val transport: HarnessTransport = CodexTransport(),
) : Harness {
    override val source: Source = Source.CODEX

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        // Codex DOES honor `updatedInput` — but only on a `PreToolUse` response
        // with `permissionDecision: "allow"` (string `command` for Bash/
        // apply_patch; args object for MCP tools). On `PermissionRequest` it is
        // reserved and *fails closed* today. Belay routes interactive arg-
        // rewriting through the PermissionRequest allow path
        // (HarnessRoutes.harnessApprovalRoute → buildPermissionAllowResponse),
        // so emitting `updatedInput` there would make Codex block the call.
        // Belay's PreToolUse path is Protection-Engine-only and never surfaces
        // user-edited args. Until arg-rewriting is plumbed onto the PreToolUse
        // allow response, this stays off — flipping it on would silently deny.
        supportsArgRewriting = false,
        // Codex doesn't surface a write-through "always-allow" persistence
        // primitive in its hooks crate today.
        supportsAlwaysAllowWriteThrough = false,
        // PostToolUse is mounted, but redaction output mutation is not.
        // Flip to true once the Codex output schema is wired through.
        supportsOutputRedaction = false,
        // Empty PermissionRequest output defers to Codex's native approval flow.
        supportsDefer = true,
        // Deny on PreToolUse halts the tool call.
        supportsInterruptOnDeny = true,
        // SessionStart / UserPromptSubmit can inject `additionalContext`.
        supportsAdditionalContextInjection = true,
    )
}
