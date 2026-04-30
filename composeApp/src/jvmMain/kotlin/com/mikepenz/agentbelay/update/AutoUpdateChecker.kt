package com.mikepenz.agentbelay.update

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.state.AppStateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides whether to fire `UpdateManager.check()` automatically on app
 * startup, subject to:
 *
 *  1. `AppSettings.autoCheckForUpdates` (user preference, default true).
 *  2. `UpdateManager.isSupported` — non-installed builds (raw `jvmRun`) have
 *     no installer artifact to point at, so checking is meaningless.
 *  3. A 24h throttle keyed off `AppSettings.lastUpdateCheckEpochMillis` — we
 *     don't hammer GitHub's API on every relaunch.
 *
 * After a check terminates (Available, UpToDate, Failed) we record the
 * timestamp through [AppStateManager.updateSettings] so the throttle survives
 * across restarts. We do NOT record on cancellation — a restart mid-check
 * should retry rather than wait.
 *
 * The check is fire-and-forget on [scope]; the result lands in
 * [UpdateManager.state] and any UI surface observing it (banner, settings
 * row) updates reactively. Callers don't await this.
 */
class AutoUpdateChecker(
    private val updateManager: UpdateManager,
    private val stateManager: AppStateManager,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val logger = Logger.withTag("AutoUpdateChecker")

    /**
     * Inspects current settings + last-checked timestamp and either fires a
     * silent check or no-ops. Idempotent — safe to call multiple times.
     */
    fun runIfDue() {
        if (!updateManager.isSupported) {
            logger.i { "Update auto-check skipped: platform unsupported (dev / jvmRun build)" }
            return
        }
        val settings = stateManager.state.value.settings
        if (!settings.autoCheckForUpdates) {
            logger.i { "Update auto-check skipped: disabled in settings" }
            return
        }
        val now = now()
        val elapsed = now - settings.lastUpdateCheckEpochMillis
        if (settings.lastUpdateCheckEpochMillis > 0 && elapsed in 0 until THROTTLE_WINDOW_MILLIS) {
            logger.i { "Update auto-check skipped: last checked ${elapsed}ms ago" }
            return
        }

        scope.launch(ioDispatcher) {
            updateManager.check()
            // Wait for the in-flight check to settle into a terminal state
            // before recording the timestamp. We treat Available, UpToDate,
            // and Failed as terminal; Downloading and Ready are post-check
            // states that follow a separate user action.
            val terminal = updateManager.state.first { state ->
                state is UpdateUiState.Available ||
                    state is UpdateUiState.UpToDate ||
                    state is UpdateUiState.Failed
            }
            // Only persist on success-shaped outcomes. A network failure
            // shouldn't push the timestamp forward — we want the next
            // launch to retry.
            if (terminal !is UpdateUiState.Failed) {
                val current = stateManager.state.value.settings
                stateManager.updateSettings(current.copy(lastUpdateCheckEpochMillis = now))
                logger.i { "Auto-update check complete (${terminal::class.simpleName}); throttle armed for 24h" }
            } else {
                logger.w { "Auto-update check failed: ${terminal.message} — will retry on next launch" }
            }
        }
    }

    companion object {
        const val THROTTLE_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
