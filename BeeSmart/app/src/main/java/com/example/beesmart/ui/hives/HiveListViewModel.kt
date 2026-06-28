package com.example.beesmart.ui.hives

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.BeeFlightAdvisor
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.WeatherRepository
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiveListViewModel @Inject constructor(
    private val hiveRepository: HiveRepository,
    private val apiaryRepository: ApiaryRepository,
    private val weatherRepository: WeatherRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<HiveListUiState>(HiveListUiState.Loading)
    val uiState: StateFlow<HiveListUiState> = _uiState.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    public val apiaryId: String = checkNotNull(savedStateHandle["apiaryId"])
    private var loadHivesJob: Job? = null
    private var loadWeatherJob: Job? = null
    private var observeHivesJob: Job? = null

    init {
        observeCachedHives()
        loadHives()
        loadWeather()
    }

    private fun observeCachedHives() {
        observeHivesJob?.cancel()
        observeHivesJob = viewModelScope.launch {
            hiveRepository.observeCachedHivesByApiaryId(apiaryId).collect { hives ->
                _uiState.value = HiveListUiState.Success(
                    hives.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                )
            }
        }
    }

    fun loadHives(forceRefresh: Boolean = false) {
        if (loadHivesJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadHivesJob?.cancel()

        loadHivesJob = viewModelScope.launch {
            val cached = hiveRepository.getCachedHivesByApiaryId(apiaryId)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            if (cached.isNotEmpty()) {
                _uiState.value = HiveListUiState.Success(cached)
            } else if (_uiState.value !is HiveListUiState.Success) {
                _uiState.value = HiveListUiState.Loading
            }

            // Folosim hiveRepository
            when (val result = hiveRepository.getHivesByApiaryId(apiaryId)) {
                is Result.Success -> {
                    _uiState.value = HiveListUiState.Success(
                        result.data.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    )
                }
                is Result.Error -> {
                    if (_uiState.value !is HiveListUiState.Success) {
                        _uiState.value = HiveListUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    fun loadWeather(forceRefresh: Boolean = false) {
        if (loadWeatherJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadWeatherJob?.cancel()

        loadWeatherJob = viewModelScope.launch {
            val location = apiaryRepository.getCachedApiaryLocation(apiaryId)
            if (location.isNullOrBlank()) {
                _weatherState.value = WeatherUiState.Unavailable(
                    "Adaugă o locație pentru această stupină ca să vezi vremea."
                )
                return@launch
            }
            _weatherState.value = WeatherUiState.Loading
            when (val result = weatherRepository.getBundleForLocation(location)) {
                is Result.Success -> {
                    val verdict = BeeFlightAdvisor.evaluate(result.data.current)
                    _weatherState.value = WeatherUiState.Success(location, result.data, verdict)
                }
                is Result.Error -> _weatherState.value = WeatherUiState.Unavailable(result.message)
                else -> {}
            }
        }
    }

    fun deleteHive(hiveId: String) {
        viewModelScope.launch {
            // Folosim hiveRepository
            when (val result = hiveRepository.enqueueDeleteHive(hiveId)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                }
                is Result.Error -> {
                    _uiState.value = HiveListUiState.Error("Eroare la ștergere: ${result.message}")
                }
                else -> {}
            }
        }
    }
}
