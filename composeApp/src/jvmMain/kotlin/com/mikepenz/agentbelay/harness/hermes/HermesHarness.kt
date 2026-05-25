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
        supportsInterruptOnDeny = true,
        supportsAdditionalContextInjection = false,
    )
}
