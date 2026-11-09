package com.techrifter.fluxvoice.ui.screen.benchmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techrifter.fluxvoice.data.local.db.TurnBenchmarkEntity
import com.techrifter.fluxvoice.domain.repository.TurnBenchmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BenchmarkSummary(
    val totalTurns: Int = 0,
    val avgTtftMs: Long = 0L,
    val avgTtfaMs: Long = 0L,
    val avgTotalMs: Long = 0L,
    val totalTokens: Int = 0
)

data class BenchmarkHistoryUiState(
    val benchmarks: List<TurnBenchmarkEntity> = emptyList(),
    val summary: BenchmarkSummary = BenchmarkSummary(),
    val selectedFilterPipeline: String? = null
)

@HiltViewModel
class BenchmarkHistoryViewModel @Inject constructor(
    private val turnBenchmarkRepository: TurnBenchmarkRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BenchmarkHistoryUiState> = combine(
        turnBenchmarkRepository.getAllBenchmarks(),
        _selectedFilter
    ) { benchmarks, filter ->
        val filtered = if (filter == null) benchmarks else benchmarks.filter { it.pipelineId == filter }

        val summary = if (filtered.isEmpty()) {
            BenchmarkSummary()
        } else {
            val count = filtered.size
            val validTtft = filtered.map { it.ttftMs }.filter { it > 0 }
            val validTtfa = filtered.map { it.ttfaMs }.filter { it > 0 }
            val validTotal = filtered.map { it.totalTurnMs }.filter { it > 0 }

            BenchmarkSummary(
                totalTurns = count,
                avgTtftMs = if (validTtft.isNotEmpty()) validTtft.average().toLong() else 0L,
                avgTtfaMs = if (validTtfa.isNotEmpty()) validTtfa.average().toLong() else 0L,
                avgTotalMs = if (validTotal.isNotEmpty()) validTotal.average().toLong() else 0L,
                totalTokens = filtered.sumOf { it.totalTokens }
            )
        }

        BenchmarkHistoryUiState(
            benchmarks = filtered,
            summary = summary,
            selectedFilterPipeline = filter
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BenchmarkHistoryUiState())

    fun setFilter(pipelineId: String?) {
        _selectedFilter.value = pipelineId
    }

    fun clearAllBenchmarks() {
        viewModelScope.launch {
            turnBenchmarkRepository.clearAllBenchmarks()
        }
    }
}

