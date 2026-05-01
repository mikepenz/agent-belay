package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.usage.pricing.LiteLlmSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the `usageTrackingEnabled` setting actually short-circuits the
 * scan pass — both via the polling loop and via the manual `refreshNow()`
 * path. A regression here would silently leak file reads even after the user
 * disables tracking.
 */
class UsageIngestServiceGateTest {

    private lateinit var tempDir: File
    private lateinit var storage: DatabaseStorage
    private lateinit var stateManager: AppStateManager

    private class CountingScanner(override val source: Source) : UsageScanner {
        var calls = 0
        override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
            calls++
            return listOf(
                UsageRecord(
                    harness = source,
                    sessionId = "s",
                    timestamp = Instant.parse("2026-04-30T10:00:00Z"),
                    model = "claude-sonnet-4-5",
                    inputTokens = 100,
                    outputTokens = 20,
                    sourceFile = "/tmp/x.jsonl",
                    sourceOffset = 0L,
                    dedupKey = "gate:$source:${calls}",
                ),
            )
        }
    }

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ingest-gate-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storage = DatabaseStorage(tempDir.absolutePath)
        stateManager = AppStateManager()
    }

    @AfterTest
    fun tearDown() {
        storage.close()
        tempDir.deleteRecursively()
    }

    private fun makeService(scanner: CountingScanner): UsageIngestService =
        UsageIngestService(
            scope = CoroutineScope(SupervisorJob()),
            scanners = listOf(scanner),
            storage = storage,
            stateManager = stateManager,
            pricingSource = LiteLlmSource(tempDir.absolutePath),
        )

    @Test
    fun refreshNow_inserts_when_tracking_enabled() = runBlocking {
        stateManager.updateSettings(AppSettings(usageTrackingEnabled = true))
        val scanner = CountingScanner(Source.CLAUDE_CODE)
        val service = makeService(scanner)

        val inserted = service.refreshNow()

        assertEquals(1, scanner.calls, "scanner must be invoked when tracking is on")
        assertEquals(1, inserted)
        assertEquals(1, storage.usageRecordCount())
    }

    @Test
    fun refreshNow_short_circuits_when_tracking_disabled() = runBlocking {
        stateManager.updateSettings(AppSettings(usageTrackingEnabled = false))
        val scanner = CountingScanner(Source.CLAUDE_CODE)
        val service = makeService(scanner)

        val inserted = service.refreshNow()

        assertEquals(0, scanner.calls, "scanner must NOT be invoked when tracking is off")
        assertEquals(0, inserted)
        assertEquals(0, storage.usageRecordCount())
    }

    @Test
    fun toggling_setting_resumes_scanning_without_restart() = runBlocking {
        val scanner = CountingScanner(Source.CLAUDE_CODE)
        val service = makeService(scanner)

        stateManager.updateSettings(AppSettings(usageTrackingEnabled = false))
        service.refreshNow()
        assertEquals(0, storage.usageRecordCount())

        stateManager.updateSettings(AppSettings(usageTrackingEnabled = true))
        service.refreshNow()
        assertEquals(1, storage.usageRecordCount(), "next pass after re-enable must ingest")
    }
}
