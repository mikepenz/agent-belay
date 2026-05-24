package com.mikepenz.agentbelay.harness.antigravity

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

class AntigravityTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-antigravity",
        HookEvent.PERMISSION_REQUEST to "/permission-request-antigravity",
    )
}
