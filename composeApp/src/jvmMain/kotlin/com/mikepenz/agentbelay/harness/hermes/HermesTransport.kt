package com.mikepenz.agentbelay.harness.hermes

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

class HermesTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-hermes",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-hermes",
        HookEvent.POST_TOOL_USE to "/post-tool-use-hermes",
    )
}
