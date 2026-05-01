package com.mikepenz.agentbelay.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun InsightsTabHost() {
    val viewModel: InsightsViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsState()
    InsightsScreen(
        state = state,
        onSelectSession = viewModel::selectSession,
        onRequestAi = viewModel::requestAiSuggestion,
        onSortChange = viewModel::setSortBy,
        onHarnessFilterChange = viewModel::setHarnessFilter,
    )
}
