package com.mikepenz.agentbelay.harness.pi

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source

/**
 * Composition root for the Pi integration. Pi loads a TypeScript extension
 * from `~/.pi/agent/extensions/`; that extension POSTs tool calls to Belay
 * and blocks denied calls with Pi's `{ block: true, reason }` response.
 */
class PiHarness(
    override val adapter: HarnessAdapter = PiAdapter(),
    override val registrar: HarnessRegistrar = PiRegistrar(),
    override val transport: HarnessTransport = PiTransport(),
) : Harness {
    override val source: Source = Source.PI

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        supportsArgRewriting = false,
        supportsAlwaysAllowWriteThrough = false,
        supportsOutputRedaction = false,
        supportsDefer = false,
        supportsInterruptOnDeny = true,
        supportsAdditionalContextInjection = false,
    )
}
