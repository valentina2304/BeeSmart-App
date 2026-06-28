package com.example.beesmart.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.DashboardStats
import com.example.beesmart.data.repository.ExtractionRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.HomeRepository
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.data.repository.TreatmentRepository
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val apiaryRepository: ApiaryRepository,
    private val taskRepository: TaskRepository,
    private val hiveRepository: HiveRepository,
    private val inspectionRepository: InspectionRepository,
    private val treatmentRepository: TreatmentRepository,
    private val extractionRepository: ExtractionRepository,
    private val connectivity: ConnectivityObserver,
    private val syncQueueDao: SyncQueueDao,
    private val syncScheduler: SyncScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _dashboardData = MutableStateFlow(DashboardData())
    val dashboardData: StateFlow<DashboardData> = _dashboardData.asStateFlow()

    // "Offline" reflects real device connectivity only. A transient backend blip
    // (timeout / cold start) is handled internally by the reachability circuit-breaker
    // and resolved on the next sync — it must NOT label the user as offline while they
    // actually have internet.
    val isOffline: StateFlow<Boolean> =
        connectivity.isOnline
            .map { online -> !online }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = !connectivity.isCurrentlyOnline()
            )

    // Number of operations waiting in the sync queue. Drives the badge on the
    // sync button — when 0, no offline writes are pending.
    val pendingSyncCount: StateFlow<Int> = syncQueueDao.observePendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var loadHomeJob: Job? = null

    fun loadHomeData(forceRefresh: Boolean = false) {
        if (loadHomeJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadHomeJob?.cancel()

        loadHomeJob = viewModelScope.launch {
            val cachedProfile = homeRepository.getCachedUserProfile()
            val cachedStatsResult = homeRepository.getDashboardStats()
            if (cachedStatsResult is Result.Success) {
                val cachedUserName = cachedProfile?.let(homeRepository::extractUserName)
                    ?: _dashboardData.value.userName.ifBlank { "User" }
                applyDashboardStats(cachedUserName, cachedStatsResult.data)
            } else if (_dashboardData.value.userName.isBlank()) {
                _uiState.value = HomeUiState.Loading
            }

            val profileResult = homeRepository.getUserProfile()

            val userName = if (profileResult is Result.Success) {
                homeRepository.extractUserName(profileResult.data)
            } else {
                "User"
            }

            // 2. Load Stats
            val statsResult = homeRepository.getDashboardStats()
            val stats = if (statsResult is Result.Success) {
                statsResult.data
            } else {
                DashboardStats()
            }

            // Update UI
            applyDashboardStats(userName, stats)

            // 3. Prewarm Room caches so the other screens have data when offline.
            // Fire-and-forget; ignore errors (offline = cache already serves the UI).
            prewarmCaches()
        }
    }

    private fun prewarmCaches() {
        if (!connectivity.isCurrentlyOnline()) return
        viewModelScope.launch {
            // Top-level lists refresh in parallel; errors are swallowed since
            // cache already serves the UI when a repo call fails.
            val apiaries = async { runCatching { apiaryRepository.getAllApiaries() } }
            val tasks = async { runCatching { taskRepository.getAllTasks() } }
            val inspections = async { runCatching { inspectionRepository.getAllInspections() } }
            val hives = async { runCatching { hiveRepository.getAllHives() } }
            val treatments = async { runCatching { treatmentRepository.getAllTreatments() } }
            val extractions = async { runCatching { extractionRepository.getAllExtractions() } }
            apiaries.await(); tasks.await(); inspections.await()
            hives.await(); treatments.await(); extractions.await()
            refreshDashboardStats()
        }
    }

    private suspend fun refreshDashboardStats() {
        val userName = _dashboardData.value.userName
        val stats = (homeRepository.getDashboardStats() as? Result.Success)?.data ?: return
        applyDashboardStats(userName, stats)
    }

    private fun applyDashboardStats(userName: String, stats: DashboardStats) {
        val dashboard = DashboardData(
            userName = userName,
            hivesCount = stats.hivesCount.toString(),
            activeHivesCount = stats.activeHivesCount,
            pendingTasksCount = stats.pendingTasksCount,
            overdueTasksCount = stats.overdueTasksCount,
            inspectionsCount = stats.inspectionsCount,
            honeyAnalytics = stats.honeyAnalytics,
            apiaryRadar = stats.apiaryRadar,
            deepBeeAdvice = stats.deepBeeAdvice
        )
        _dashboardData.value = dashboard
        _uiState.value = HomeUiState.Success(userName, dashboard.hivesCount)
    }

    /**
     * User-triggered sync from the home top bar. Kicks the WorkManager job and
     * waits up to 10s for the queue to drain so we can refresh dashboard stats
     * with the new server IDs assigned during the sync.
     */
    fun triggerManualSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            syncScheduler.requestSync()
            // Wait until the queue drains (or 10 s timeout) so the user sees
            // a definite end-state. The WorkManager runs out-of-process so we
            // observe the count flow rather than awaiting the worker directly.
            withTimeoutOrNull(10_000) {
                syncQueueDao.observePendingCount().first { it == 0 }
            }
            // Small grace period so the spinner doesn't flash off instantly.
            delay(300)
            _isSyncing.value = false
            // Refresh dashboard so newly-synced server IDs replace localIds.
            loadHomeData(forceRefresh = true)
        }
    }

    fun logout() {
        _uiState.value = HomeUiState.LoggingOut
        viewModelScope.launch {
            when (homeRepository.logout()) {
                is Result.Success -> _uiState.value = HomeUiState.LoggedOut
                is Result.Error -> _uiState.value = HomeUiState.LoggedOut // Force logout anyway
                else -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Idle
    }

    fun refreshData() {
        loadHomeData(forceRefresh = true)
    }
}
