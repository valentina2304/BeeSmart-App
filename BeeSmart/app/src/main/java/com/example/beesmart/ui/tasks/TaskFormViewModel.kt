package com.example.beesmart.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.network.models.*
import com.example.beesmart.notifications.TaskNotificationScheduler
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class TaskFormViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val apiaryRepository: ApiaryRepository,
    private val hiveRepository: HiveRepository,
    private val notificationScheduler: TaskNotificationScheduler,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskFormUiState>(TaskFormUiState.Idle)
    val uiState: StateFlow<TaskFormUiState> = _uiState.asStateFlow()

    private val _apiaries = MutableStateFlow<List<ApiaryResponse>>(emptyList())
    val apiaries: StateFlow<List<ApiaryResponse>> = _apiaries.asStateFlow()

    private val _hives = MutableStateFlow<List<HiveResponse>>(emptyList())
    val hives: StateFlow<List<HiveResponse>> = _hives.asStateFlow()

    private val taskId: String? = savedStateHandle["taskId"]
    val isEditMode: Boolean = !taskId.isNullOrEmpty()
    private var saveInProgress = false

    init {
        loadApiaries()
        if (isEditMode) {
            loadTaskDetails()
        }
    }

    private fun loadApiaries() {
        viewModelScope.launch {
            apiaryRepository.getCachedApiaries()
                .takeIf { it.isNotEmpty() }
                ?.let { _apiaries.value = it }

            when (val result = apiaryRepository.getAllApiaries()) {
                is Result.Success -> _apiaries.value = result.data
                is Result.Error -> _uiState.value = TaskFormUiState.Error("Eroare la încărcarea stupinelor")
                else -> {}
            }
        }
    }

    fun loadHivesForApiary(apiaryId: String) {
        viewModelScope.launch {
            hiveRepository.getCachedHivesByApiaryId(apiaryId)
                .takeIf { it.isNotEmpty() }
                ?.let { _hives.value = it }

            when (val result = hiveRepository.getHivesByApiaryId(apiaryId)) {
                is Result.Success -> _hives.value = result.data
                is Result.Error -> _uiState.value = TaskFormUiState.Error("Eroare la încărcarea stupilor")
                else -> {}
            }
        }
    }

    fun clearHives() {
        _hives.value = emptyList()
    }

    private fun loadTaskDetails() {
        taskId ?: return
        viewModelScope.launch {
            val cached = taskRepository.getCachedTaskById(taskId)
            if (cached != null) {
                publishTask(cached)
                return@launch
            }

            _uiState.value = TaskFormUiState.Loading
            when (val result = taskRepository.getTaskById(taskId)) {
                is Result.Success -> {
                    publishTask(result.data)
                }
                is Result.Error -> _uiState.value = TaskFormUiState.Error(result.message)
                else -> {}
            }
        }
    }

    private fun publishTask(task: TaskResponse) {
        if (task.apiaryId != null) {
            loadHivesForApiary(task.apiaryId)
        }

        var parsedDate: LocalDate? = null
        try {
            if (task.dueDate != null) {
                parsedDate = ZonedDateTime.parse(task.dueDate).toLocalDate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _uiState.value = TaskFormUiState.LoadedData(
            title = task.title,
            description = task.description,
            priority = task.priority,
            dueDate = parsedDate,
            apiaryId = task.apiaryId,
            hiveId = task.hiveId,
            status = task.status
        )
    }

    fun saveTask(
        title: String,
        description: String?,
        priority: TaskPriority,
        dueDate: LocalDate?,
        apiaryId: String?,
        hiveId: String?,
        currentStatus: TaskStatus = TaskStatus.Pending
    ) {
        if (saveInProgress || _uiState.value is TaskFormUiState.Loading) return

        if (title.length < 3) {
            _uiState.value = TaskFormUiState.Error("Titlul trebuie să aibă minim 3 caractere")
            return
        }

        saveInProgress = true
        viewModelScope.launch {
            _uiState.value = TaskFormUiState.Loading

            val dueDateString = dueDate?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()?.toString()

            val result = if (isEditMode && taskId != null) {
                val request = UpdateTaskRequest(
                    title = title,
                    description = description,
                    priority = priority,
                    status = currentStatus,
                    dueDate = dueDateString,
                    apiaryId = apiaryId,
                    hiveId = hiveId
                )
                taskRepository.enqueueUpdateTask(taskId, request)
            } else {
                val request = CreateTaskRequest(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDateString,
                    apiaryId = apiaryId,
                    hiveId = hiveId
                )
                taskRepository.enqueueCreateTask(request)
            }

            when (result) {
                is Result.Success -> {
                    val task = result.data
                    syncScheduler.requestSync()
                    if (isEditMode) {
                        notificationScheduler.cancelTaskNotifications(task.id)
                    }
                    if (task.dueDate != null) {
                        notificationScheduler.scheduleTaskNotifications(task.id, task.title, task.dueDate)
                    }
                    val msg = if (isEditMode) "Task actualizat cu succes" else "Task creat cu succes"
                    _uiState.value = TaskFormUiState.Success(msg)
                }
                is Result.Error -> {
                    _uiState.value = TaskFormUiState.Error(result.message)
                    saveInProgress = false
                }
                else -> {
                    saveInProgress = false
                }
            }
        }
    }

    fun resetState() {
        saveInProgress = false
        _uiState.value = TaskFormUiState.Idle
    }
}
