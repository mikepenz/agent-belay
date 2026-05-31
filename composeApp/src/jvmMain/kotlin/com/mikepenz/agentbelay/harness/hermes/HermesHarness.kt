package com.mikepenz.agentbelay.harness.hermes

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source

class HermesHarness(
    override val adapter: HarnessAdapter = HermesAdapter(),
    override val registrar: HarnessRegistrar = HermesRegistrar(),
    override val transport: HarnessTransport = HermesTransport(),
) : Harness {
    override val source: Source = Source.HERMES

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        supportsArgRewriting = false,
        supportsAlwaysAllowWriteThrough = false,
        supportsOutputRedaction = false,
        supportsDefer = false,
        // A `pre_tool_call` block only short-circuits that single tool call
        // (the `message` is returned to the model as the tool's error) — the
        // agent loop keeps running. It is NOT an agent interrupt.
        supportsInterruptOnDeny = false,
        // Hermes' `pre_llm_call` hook injects context via `{"context": "..."}`.
        // (`on_session_start`'s return value is ignored by Hermes, so that
        // path is not used — see HermesBridgeInstaller.)
        supportsAdditionalContextInjection = true,
    )
}
