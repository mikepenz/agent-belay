package com.mikepenz.agentbelay.update

import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the throttle / opt-out / unsupported-platform short-circuits and the
 * timestamp-recording side effect that AutoUpdateChecker delegates to
 * [AppStateManager.updateSettings]. Doesn't talk to GitHub — uses a
 * [FakeUpdateManager] that drives the state flow directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoUpdateCheckerTest {

    @Test
    fun `unsupported platform skips check and does not record timestamp`() = runTest {
        val (checker, manager, state) = setup(
            settings = AppSettings(autoCheckForUpdates = true, lastUpdateCheckEpochMillis = 0),
            isSupported = false,
            now = 1_000L,
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(0, manager.checkInvocations)
        assertEquals(0L, state.state.value.settings.lastUpdateCheckEpochMillis)
    }

    @Test
    fun `disabled in settings skips check`() = runTest {
        val (checker, manager, state) = setup(
            settings = AppSettings(autoCheckForUpdates = false, lastUpdateCheckEpochMillis = 0),
            now = 1_000L,
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(0, manager.checkInvocations)
        assertEquals(0L, state.state.value.settings.lastUpdateCheckEpochMillis)
    }

    @Test
    fun `inside throttle window skips check`() = runTest {
        val twelveHoursAgo = 1_000_000_000_000L
        val now = twelveHoursAgo + 12L * 60L * 60L * 1000L
        val (checker, manager, _) = setup(
            settings = AppSettings(
                autoCheckForUpdates = true,
                lastUpdateCheckEpochMillis = twelveHoursAgo,
            ),
            now = now,
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(0, manager.checkInvocations)
    }

    @Test
    fun `outside throttle window fires check and records timestamp`() = runTest {
        val twoDaysAgo = 1_000_000_000_000L
        val now = twoDaysAgo + 2L * 24L * 60L * 60L * 1000L
        val (checker, manager, state) = setup(
            settings = AppSettings(
                autoCheckForUpdates = true,
                lastUpdateCheckEpochMillis = twoDaysAgo,
            ),
            now = now,
            terminalState = UpdateUiState.UpToDate,
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(1, manager.checkInvocations)
        assertEquals(now, state.state.value.settings.lastUpdateCheckEpochMillis)
    }

    @Test
    fun `first ever check fires regardless of throttle`() = runTest {
        val (checker, manager, state) = setup(
            settings = AppSettings(
                autoCheckForUpdates = true,
                lastUpdateCheckEpochMillis = 0,
            ),
            now = 1_000_000L,
            terminalState = UpdateUiState.UpToDate,
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(1, manager.checkInvocations)
        assertEquals(1_000_000L, state.state.value.settings.lastUpdateCheckEpochMillis)
    }

    @Test
    fun `failed check does not advance throttle timestamp`() = runTest {
        val (checker, manager, state) = setup(
            settings = AppSettings(
                autoCheckForUpdates = true,
                lastUpdateCheckEpochMillis = 0,
            ),
            now = 1_000_000L,
            terminalState = UpdateUiState.Failed("network down"),
        )
        checker.runIfDue()
        advanceUntilIdle()
        assertEquals(1, manager.checkInvocations)
        assertEquals(0L, state.state.value.settings.lastUpdateCheckEpochMillis)
    }

    // ── Test fixtures ─────────────────────────────────────────────────────

    private fun TestScope.setup(
        settings: AppSettings,
        now: Long,
        isSupported: Boolean = true,
        terminalState: UpdateUiState = UpdateUiState.UpToDate,
    ): Triple<AutoUpdateChecker, FakeUpdateManager, AppStateManager> {
        val state = AppStateManager()
        // initialize() reads from disk; here we set settings directly via the
        // exposed updateSettings entrypoint.
        state.updateSettings(settings)
        val manager = FakeUpdateManager(isSupported = isSupported, terminalState = terminalState)
        val checker = AutoUpdateChecker(
            updateManager = manager.proxy(this),
            stateManager = state,
            scope = this,
            now = { now },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        return Triple(checker, manager, state)
    }
}

/**
 * Test-only stand-in that records `check()` invocations and drives the state
 * flow into the configured terminal state synchronously. Exposes a
 * [UpdateManager] proxy so AutoUpdateChecker sees the public surface.
 */
private class FakeUpdateManager(
    private val isSupported: Boolean = true,
    private val terminalState: UpdateUiState = UpdateUiState.UpToDate,
) {
    var checkInvocations = 0
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun proxy(scope: CoroutineScope): UpdateManager =
        UpdateManagerSpy(scope = scope, fake = this, isSupportedOverride = isSupported)

    fun fireCheck() {
        checkInvocations++
        _state.value = terminalState
    }
}

/**
 * Subclass-like wrapper around UpdateManager that delegates check() to the
 * fake. Exposes the fake's state flow as `UpdateManager.state` via its parent
 * constructor — but UpdateManager is a final `class`, so we need a
 * lookalike. Since AutoUpdateChecker only depends on three members
 * (`isSupported`, `state`, `check()`), we hand-roll a minimal stand-in.
 */
private class UpdateManagerSpy(
    scope: CoroutineScope,
    private val fake: FakeUpdateManager,
    private val isSupportedOverride: Boolean,
) : UpdateManager(scope) {

    override val state: StateFlow<UpdateUiState> = fake.state
    override val isSupported: Boolean get() = isSupportedOverride

    override fun check() {
        fake.fireCheck()
    }
}
