package com.mikepenz.agentbelay.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightEngine
import com.mikepenz.agentbelay.insights.SessionMetrics
import com.mikepenz.agentbelay.insights.ai.AiSuggestion
import com.mikepenz.agentbelay.insights.ai.InsightAiAnalyzer
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.SessionSummary
import com.mikepenz.agentbelay.usage.UsageIngestService
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the full state for the Insights tab. The flow is:
 *  1. List recent sessions from `usage_records` (left rail).
 *  2. On selection, load that session's full record + history join, build
 *     [SessionMetrics], run [InsightEngine].
 *  3. On user click, send one insight to [InsightAiAnalyzer] and stash the
 *     reply against the insight key in `aiSuggestions`.
 *
 * AI calls are completely opt-in (gated by `AppSettings.insightsAiEnabled`)
 * and fired only via [requestAiSuggestion].
 */
/**
 * Sort orderings for the Insights session list. Mirrored on the History
 * 3-pane view so users learn one mental model.
 */
enum class SessionSort(val label: String) {
    Recent("Recent"),
    Cost("Cost"),
    Turns("Turns"),
}

data class InsightsUiState(
    /** All sessions returned from storage — pre-sort, pre-filter. */
    val allSessions: List<SessionSummary> = emptyList(),
    /** Sessions after applying [sortBy] + [harnessFilter]. */
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    val insights: List<Insight> = emptyList(),
    val loadingSessions: Boolean = true,
    val loadingInsights: Boolean = false,
    val aiSuggestions: Map<String, AiSuggestion> = emptyMap(),
    val aiInflight: Set<String> = emptySet(),
    val aiErrors: Map<String, String> = emptyMap(),
    val aiEnabled: Boolean = false,
    val sortBy: SessionSort = SessionSort.Recent,
    /**
     * The set of harnesses the user has chosen to keep visible. `null` means
     * no filter (show all). Empty set is treated identically to `null` to
     * keep the UI sane when the user toggles every option off.
     */
    val harnessFilter: Set<com.mikepenz.agentbelay.model.Source>? = null,
) {
    /** Harnesses actually present in the data — drives the filter dropdown options. */
    val availableHarnesses: List<com.mikepenz.agentbelay.model.Source>
        get() = allSessions.map { it.harness }.distinct().sortedBy { it.ordinal }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class InsightsViewModel(
    private val database: DatabaseStorage,
    private val stateManager: AppStateManager,
    private val ingestService: UsageIngestService,
    private val engine: InsightEngine,
    private val aiAnalyzer: InsightAiAnalyzer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var sessionLoadJob: Job? = null
    private var insightsLoadJob: Job? = null

    init {
        loadSessions()
        // Re-pull sessions when ingest finishes a pass.
        ingestService.scans.onEach { loadSessions() }.launchIn(viewModelScope)
        // Reflect the AI toggle live.
        stateManager.state
            .onEach { state ->
                _uiState.value = _uiState.value.copy(aiEnabled = state.settings.insightsAiEnabled)
            }
            .launchIn(viewModelScope)
    }

    fun setSortBy(sort: SessionSort) {
        if (_uiState.value.sortBy == sort) return
        _uiState.value = applyFilters(_uiState.value.copy(sortBy = sort))
    }

    fun setHarnessFilter(filter: Set<com.mikepenz.agentbelay.model.Source>?) {
        // Treat empty set as null so the UI never ends up with zero rows
        // by accident (the dropdown can return an empty selection).
        val normalized = filter?.takeIf { it.isNotEmpty() }
        if (_uiState.value.harnessFilter == normalized) return
        _uiState.value = applyFilters(_uiState.value.copy(harnessFilter = normalized))
    }

    fun selectSession(sessionId: String) {
        if (_uiState.value.selectedSessionId == sessionId) return
        _uiState.value = _uiState.value.copy(
            selectedSessionId = sessionId,
            insights = emptyList(),
            aiSuggestions = emptyMap(),
            aiErrors = emptyMap(),
        )
        runDetectors(sessionId)
    }

    fun requestAiSuggestion(insight: Insight) {
        if (!_uiState.value.aiEnabled) return
        val key = aiKey(insight)
        val state = _uiState.value
        if (key in state.aiInflight || key in state.aiSuggestions) return
        _uiState.value = state.copy(
            aiInflight = state.aiInflight + key,
            aiErrors = state.aiErrors - key,
        )
        viewModelScope.launch {
            val session = currentSessionMetrics() ?: run {
                fail(key, "No session selected")
                return@launch
            }
            val result = runCatching {
                aiAnalyzer.elevate(insight, session)
            }.getOrElse { Result.failure(it) }
            result.onSuccess { suggestion ->
                val s = _uiState.value
                _uiState.value = s.copy(
                    aiSuggestions = s.aiSuggestions + (key to suggestion),
                    aiInflight = s.aiInflight - key,
                )
            }.onFailure { err ->
                fail(key, err.message ?: err::class.simpleName ?: "AI request failed")
            }
        }
    }

    private fun fail(key: String, message: String) {
        val s = _uiState.value
        _uiState.value = s.copy(
            aiInflight = s.aiInflight - key,
            aiErrors = s.aiErrors + (key to message),
        )
    }

    private fun loadSessions() {
        sessionLoadJob?.cancel()
        sessionLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingSessions = true)
            try {
                val sessions = withContext(Dispatchers.IO) {
                    database.listRecentSessions(minTurns = 5)
                }
                val withSort = applyFilters(_uiState.value.copy(allSessions = sessions))
                val selected = withSort.selectedSessionId
                    ?.takeIf { id -> withSort.sessions.any { it.sessionId == id } }
                    ?: withSort.sessions.firstOrNull()?.sessionId
                _uiState.value = withSort.copy(selectedSessionId = selected)
                if (selected != null) runDetectors(selected)
            } finally {
                _uiState.value = _uiState.value.copy(loadingSessions = false)
            }
        }
    }

    /**
     * Returns a copy of [state] whose [InsightsUiState.sessions] is the
     * sorted+filtered view of [InsightsUiState.allSessions]. Centralised so
     * sort/filter setters and load callbacks stay in sync.
     */
    private fun applyFilters(state: InsightsUiState): InsightsUiState {
        val filter = state.harnessFilter
        val filtered = state.allSessions.filter { filter == null || it.harness in filter }
        val sorted = when (state.sortBy) {
            SessionSort.Recent -> filtered.sortedByDescending { it.lastTsMillis }
            SessionSort.Cost -> filtered.sortedByDescending { it.totalCostUsd }
            SessionSort.Turns -> filtered.sortedByDescending { it.turnCount }
        }
        return state.copy(sessions = sorted)
    }

    private fun runDetectors(sessionId: String) {
        insightsLoadJob?.cancel()
        insightsLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingInsights = true)
            try {
                val metrics = buildSessionMetrics(sessionId) ?: return@launch
                val insights = withContext(Dispatchers.Default) { engine.analyze(metrics) }
                _uiState.value = _uiState.value.copy(insights = insights)
            } finally {
                _uiState.value = _uiState.value.copy(loadingInsights = false)
            }
        }
    }

    private suspend fun buildSessionMetrics(sessionId: String): SessionMetrics? = withContext(Dispatchers.IO) {
        val turns = database.queryUsageBySession(sessionId)
        if (turns.isEmpty()) return@withContext null
        val history = database.queryHistoryBySession(sessionId)
        val summary = _uiState.value.sessions.firstOrNull { it.sessionId == sessionId }
        val cwd = history.firstOrNull()?.request?.hookInput?.cwd
        SessionMetrics(
            harness = turns.first().harness,
            sessionId = sessionId,
            cwd = cwd,
            model = summary?.model ?: turns.firstOrNull { it.model != null }?.model,
            turns = turns,
            history = history,
        )
    }

    private suspend fun currentSessionMetrics(): SessionMetrics? {
        val id = _uiState.value.selectedSessionId ?: return null
        return buildSessionMetrics(id)
    }

    /** Stable key per insight for the AI-state maps. */
    internal fun aiKey(insight: Insight): String =
        "${insight.sessionId}:${insight.kind.name}"
}
