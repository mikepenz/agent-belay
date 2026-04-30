package com.mikepenz.agentbelay.testutil

import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.model.RedactionSettings
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.redaction.RedactionEngine
import com.mikepenz.agentbelay.server.ApprovalServer
import com.mikepenz.agentbelay.state.AppStateManager
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Demonstrates [FakeHarnessClient] against three live targets:
 *
 *   1. A real [ApprovalServer] — Claude Code's "POST and read response"
 *      path. Asserts the parsed allow envelope.
 *   2. An unreachable port — exercises Copilot's `failClosed` synthesis
 *      of a deny payload at the bridge layer (no server contact).
 *   3. A stub that returns a flat `{"behavior":"deny"}` — exercises the
 *      OpenCode plugin's "throw on deny" wrapper logic.
 *
 * These three cases together cover the bridge-layer behaviour that
 * adapter-only and route-only tests can't reach: the wrapper that sits
 * between HTTP and the harness's user-visible action.
 */
class FakeHarnessClientTest {

    private lateinit var stateManager: AppStateManager
    private lateinit var server: ApprovalServer
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        stateManager = AppStateManager()
        server = ApprovalServer(
            stateManager = stateManager,
            protectionEngine = ProtectionEngine(emptyList()) { ProtectionSettings() },
            capabilityEngine = CapabilityEngine(emptyList()) { CapabilitySettings() },
            redactionEngine = RedactionEngine(emptyList()) { RedactionSettings(enabled = false) },
            databaseStorage = null,
            onNewApproval = {},
        )
        port = freePort()
        server.start(port = port, host = "127.0.0.1")
        Thread.sleep(200)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `claudeCodePost gets a Claude-shaped allow envelope back`() = runBlocking {
        FakeHarnessClient("http://127.0.0.1:$port").use { client ->
            // Resolve the parked request to APPROVED as soon as it appears.
            // Polling beats a fixed delay — Netty schedules addPending on its
            // worker pool and a 150ms guess sometimes fires before that.
            val resolver = launch {
                val deadline = System.nanoTime() + 4_000_000_000L
                while (stateManager.state.value.pendingApprovals.isEmpty() &&
                    System.nanoTime() < deadline) {
                    delay(20)
                }
                stateManager.state.value.pendingApprovals.firstOrNull()?.let { pending ->
                    stateManager.resolve(
                        requestId = pending.id,
                        decision = Decision.APPROVED,
                        feedback = null,
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                }
            }

            val payload = GoldenPayloads.read("claudecode/permission_request_bash_ls.json")
            val response = client.claudeCodePost("/approve", payload)
            resolver.join()

            val obj = Json.parseToJsonElement(response).jsonObject
            val decision = obj["hookSpecificOutput"]!!.jsonObject["decision"]!!.jsonObject
            assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `copilotShim with failClosed synthesizes deny when server is unreachable`() = runBlocking {
        // Point at a port we know nothing listens on.
        val deadPort = freePort()  // ephemeral and immediately released
        FakeHarnessClient("http://127.0.0.1:$deadPort").use { client ->
            val response = client.copilotShim("/approve-copilot", "{}", failClosed = true)
            assertContains(response, "deny", message = "fail-closed bridge must synthesize a deny")
            assertContains(response, "unreachable")
        }
    }

    @Test
    fun `copilotShim without failClosed returns empty on unreachable server`() = runBlocking {
        val deadPort = freePort()
        FakeHarnessClient("http://127.0.0.1:$deadPort").use { client ->
            val response = client.copilotShim("/approve-copilot", "{}", failClosed = false)
            assertEquals("", response, "fail-open bridge must emit nothing — Copilot CLI proceeds")
        }
    }

    @Test
    fun `openCodePlugin throws when stub returns deny`() = runBlocking {
        val stubPort = freePort()
        val stub = embeddedServer(Netty, port = stubPort, host = "127.0.0.1") {
            install(ContentNegotiation) { json(Json) }
            routing {
                post("/approve-opencode") {
                    call.respondText(
                        """{"behavior":"deny","message":"blocked by policy"}""",
                        io.ktor.http.ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)
        Thread.sleep(150)
        try {
            FakeHarnessClient("http://127.0.0.1:$stubPort").use { client ->
                val ex = assertFailsWith<FakeHarnessClient.OpenCodeDenied> {
                    client.openCodePlugin("/approve-opencode", "{}")
                }
                assertEquals("blocked by policy", ex.message)
            }
        } finally {
            stub.stop(100, 500)
        }
    }

    @Test
    fun `openCodePlugin returns silently on unreachable server (fail-open)`() = runBlocking {
        val deadPort = freePort()
        FakeHarnessClient("http://127.0.0.1:$deadPort").use { client ->
            // No exception expected.
            client.openCodePlugin("/approve-opencode", "{}")
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
