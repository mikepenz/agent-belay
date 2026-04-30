package com.mikepenz.agentbelay.harness.pi

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Pi extensions run in-process and POST to Belay's Ktor server before a tool
 * executes. A single approval route is enough because Pi's `tool_call` event
 * is both the permission surface and the pre-execution interception point.
 */
class PiTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-pi",
    )
}
