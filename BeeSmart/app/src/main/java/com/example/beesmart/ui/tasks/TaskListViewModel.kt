package com.example.beesmart.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.data.repository.Result
import com.example.beesmart.notifications.TaskNotificationScheduler
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val notificationScheduler: TaskNotificationScheduler,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private var currentFilter: TaskFilter = TaskFilter.ALL
    private var loadTasksJob: Job? = null
    private var observeTasksJob: Job? = null

    init {
        observeCachedTasks()
        loadTasks()
    }

    fun setFilter(filter: TaskFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        observeCachedTasks()
        loadTasks(forceRefresh = true)
    }

    private fun observeCachedTasks() {
        observeTasksJob?.cancel()
        observeTasksJob = viewModelScope.launch {
            val flow = when (currentFilter) {
                TaskFilter.ALL -> taskRepository.observeCachedAllTasks()
                TaskFilter.PENDING -> taskRepository.observeCachedPendingTasks()
                TaskFilter.OVERDUE -> taskRepository.observeCachedOverdueTasks()
                TaskFilter.COMPLETED -> taskRepository.observeCachedAllTasks()
                    .map { tasks -> tasks.filter { it.status == TaskStatus.Completed } }
            }
            flow.collect { tasks ->
                _uiState.value = TaskListUiState.Success(tasks)
            }
        }
    }

    fun loadTasks(forceRefresh: Boolean = false) {
        if (loadTasksJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadTasksJob?.cancel()

        loadTasksJob = viewModelScope.launch {
            val cached = getCachedTasksForCurrentFilter()
            if (cached.isNotEmpty()) {
                _uiState.value = TaskListUiState.Success(cached)
            } else if (_uiState.value !is TaskListUiState.Success) {
                _uiState.value = TaskListUiState.Loading
            }

            val result = when (currentFilter) {
                TaskFilter.ALL -> taskRepository.getAllTasks()
                TaskFilter.PENDING -> taskRepository.getPendingTasks()
                TaskFilter.OVERDUE -> taskRepository.getOverdueTasks()
                TaskFilter.COMPLETED -> {
                    val all = taskRepository.getAllTasks()
                    if (all is Result.Success) {
                        Result.Success(all.data.filter { it.status == TaskStatus.Completed })
                    } else {
                        all
                    }
                }
            }

            when (result) {
                is Result.Success -> _uiState.value = TaskListUiState.Success(result.data)
                is Result.Error -> {
                    if (_uiState.value !is TaskListUiState.Success) {
                        _uiState.value = TaskListUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun getCachedTasksForCurrentFilter(): List<TaskResponse> {
        return when (currentFilter) {
            TaskFilter.ALL -> taskRepository.getCachedAllTasks()
            TaskFilter.PENDING -> taskRepository.getCachedPendingTasks()
            TaskFilter.OVERDUE -> taskRepository.getCachedOverdueTasks()
            TaskFilter.COMPLETED -> taskRepository.getCachedAllTasks()
                .filter { it.status == TaskStatus.Completed }
        }
    }

    fun completeTask(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            if (isCompleted) {
                when (val result = taskRepository.enqueueCompleteTask(taskId)) {
                    is Result.Success -> {
                        notificationScheduler.cancelTaskNotifications(taskId)
                        syncScheduler.requestSync()
                    }
                    is Result.Error -> _uiState.value = TaskListUiState.Error("Eroare: ${result.message}")
                    else -> {}
                }
            } else {
                // When unchecking a completed task, restore it to Pending or Overdue
                when (val result = taskRepository.enqueueUncompleteTask(taskId)) {
                    is Result.Success -> {
                        syncScheduler.requestSync()
                    }
                    is Result.Error -> {
                        _uiState.value = TaskListUiState.Error("Eroare: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            when (val result = taskRepository.enqueueDeleteTask(taskId)) {
                is Result.Success -> {
                    notificationScheduler.cancelTaskNotifications(taskId)
                    syncScheduler.requestSync()
                }
                is Result.Error -> _uiState.value = TaskListUiState.Error(result.message)
                else -> {}
            }
        }
    }
}
