package com.mikepenz.agentbelay.harness.antigravity

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source

class AntigravityHarness(
    override val adapter: HarnessAdapter = AntigravityAdapter(),
    override val registrar: HarnessRegistrar = AntigravityRegistrar(),
    override val transport: HarnessTransport = AntigravityTransport(),
) : Harness {
    override val source: Source = Source.ANTIGRAVITY

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        supportsArgRewriting = false,
        supportsAlwaysAllowWriteThrough = false,
        supportsOutputRedaction = false,
        supportsDefer = false,
        supportsInterruptOnDeny = true,
        supportsAdditionalContextInjection = false,
    )
}
