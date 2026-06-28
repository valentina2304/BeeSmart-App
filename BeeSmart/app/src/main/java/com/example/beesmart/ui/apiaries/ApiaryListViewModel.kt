package com.example.beesmart.ui.apiaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.BeeFlightAdvisor
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.WeatherRepository
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.network.models.WeatherResponse
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ApiaryListViewModel @Inject constructor(
    private val repository: ApiaryRepository,
    private val weatherRepository: WeatherRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<ApiaryListUiState>(ApiaryListUiState.Loading)
    val uiState: StateFlow<ApiaryListUiState> = _uiState.asStateFlow()

    private val _weatherByApiaryId = MutableStateFlow<Map<String, ApiaryWeatherUiState>>(emptyMap())
    val weatherByApiaryId: StateFlow<Map<String, ApiaryWeatherUiState>> = _weatherByApiaryId.asStateFlow()

    private var loadApiariesJob: Job? = null
    private var weatherJob: Job? = null
    private var observeApiariesJob: Job? = null

    init {
        observeCachedApiaries()
        loadApiaries()
    }

    private fun observeCachedApiaries() {
        observeApiariesJob?.cancel()
        observeApiariesJob = viewModelScope.launch {
            repository.observeCachedApiaries().collect { apiaries ->
                _uiState.value = ApiaryListUiState.Success(apiaries)
                loadWeatherForApiaries(apiaries)
            }
        }
    }

    fun loadApiaries(forceRefresh: Boolean = false) {
        if (loadApiariesJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadApiariesJob?.cancel()

        loadApiariesJob = viewModelScope.launch {
            val cached = repository.getCachedApiaries()
            if (cached.isNotEmpty()) {
                _uiState.value = ApiaryListUiState.Success(cached)
                loadWeatherForApiaries(cached)
            } else if (_uiState.value !is ApiaryListUiState.Success) {
                _uiState.value = ApiaryListUiState.Loading
            }

            when (val result = repository.getAllApiaries()) {
                is Result.Success -> {
                    _uiState.value = ApiaryListUiState.Success(result.data)
                    loadWeatherForApiaries(result.data)
                }
                is Result.Error -> {
                    if (_uiState.value !is ApiaryListUiState.Success) {
                        _uiState.value = ApiaryListUiState.Error(result.message)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun loadWeatherForApiaries(apiaries: List<ApiaryResponse>) {
        weatherJob?.cancel()
        val apiariesWithLocation = apiaries.filter { !it.location.isNullOrBlank() }
        if (apiariesWithLocation.isEmpty()) {
            _weatherByApiaryId.value = emptyMap()
            return
        }
        _weatherByApiaryId.value = apiariesWithLocation.associate { it.id to ApiaryWeatherUiState.Loading }

        weatherJob = viewModelScope.launch {
            apiariesWithLocation.forEach { apiary ->
                launch {
                    val state = when (val result = weatherRepository.getWeatherForLocation(apiary.location.orEmpty())) {
                        is Result.Success -> ApiaryWeatherUiState.Success(result.data.toApiaryWeatherSummary())
                        is Result.Error -> ApiaryWeatherUiState.Unavailable
                        else -> ApiaryWeatherUiState.Unavailable
                    }
                    _weatherByApiaryId.value = _weatherByApiaryId.value + (apiary.id to state)
                }
            }
        }
    }

    fun deleteApiary(apiaryId: String) {
        viewModelScope.launch {
            when (val result = repository.enqueueDeleteApiary(apiaryId)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                }
                is Result.Error -> {
                    _uiState.value = ApiaryListUiState.Error("Eroare la \u0219tergere: ${result.message}")
                }
                else -> Unit
            }
        }
    }
}

sealed class ApiaryWeatherUiState {
    object Loading : ApiaryWeatherUiState()
    object Unavailable : ApiaryWeatherUiState()
    data class Success(val summary: ApiaryWeatherSummary) : ApiaryWeatherUiState()
}

data class ApiaryWeatherSummary(
    val tempC: Int,
    val feelsLikeC: Int,
    val condition: String,
    val humidity: Int,
    val windKmH: Int,
    val flight: BeeFlightAdvisor.Verdict
)

private fun WeatherResponse.toApiaryWeatherSummary(): ApiaryWeatherSummary {
    val description = weather.firstOrNull()
        ?.description
        ?.replaceFirstChar { it.uppercaseChar() }
        ?: "Vreme indisponibil\u0103"
    return ApiaryWeatherSummary(
        tempC = main.temp.roundToInt(),
        feelsLikeC = main.feelsLike.roundToInt(),
        condition = description,
        humidity = main.humidity,
        windKmH = ((wind?.speed ?: 0.0) * 3.6).roundToInt(),
        flight = BeeFlightAdvisor.evaluate(this)
    )
}
