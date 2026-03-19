package com.mikepenz.agentapprover.storage

import com.mikepenz.agentapprover.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryStorageTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun makeResult(index: Int): ApprovalResult = ApprovalResult(
        request = ApprovalRequest(
            id = "req-$index",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = "session-1",
                toolName = "tool-$index",
                cwd = "/tmp",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        ),
        decision = Decision.APPROVED,
        decidedAt = Clock.System.now(),
    )

    @Test
    fun `load returns empty list when file missing`() {
        val dir = "/tmp/test-history-${System.currentTimeMillis()}"
        val storage = HistoryStorage(dir, scope)
        assertEquals(emptyList(), storage.load())
    }

    @Test
    fun `save and reload preserves results`() {
        val dir = "/tmp/test-history-${System.currentTimeMillis()}"
        val storage = HistoryStorage(dir, scope)
        val results = (1..5).map { makeResult(it) }
        storage.save(results)
        storage.shutdown()
        val loaded = storage.load()
        assertEquals(results, loaded)
    }

    @Test
    fun `save caps at 250 keeping newest`() {
        val dir = "/tmp/test-history-${System.currentTimeMillis()}"
        val storage = HistoryStorage(dir, scope)
        val results = (1..260).map { makeResult(it) }
        storage.save(results)
        storage.shutdown()
        val loaded = storage.load()
        assertEquals(250, loaded.size)
        // Should keep last 250 (indexes 11..260)
        assertEquals("req-11", loaded.first().request.id)
        assertEquals("req-260", loaded.last().request.id)
    }
}
