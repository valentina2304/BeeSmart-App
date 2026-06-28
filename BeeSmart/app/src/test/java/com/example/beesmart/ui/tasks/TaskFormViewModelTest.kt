package com.example.beesmart.ui.tasks

import androidx.lifecycle.SavedStateHandle
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.notifications.TaskNotificationScheduler
import com.example.beesmart.sync.SyncScheduler
import com.example.beesmart.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val taskRepository: TaskRepository = mockk()
    private val apiaryRepository: ApiaryRepository = mockk()
    private val hiveRepository: HiveRepository = mockk()
    private val notificationScheduler: TaskNotificationScheduler = mockk(relaxed = true)
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)

    @Before
    fun setUp() {
        coEvery { apiaryRepository.getCachedApiaries() } returns emptyList()
    }

    @Test
    fun `rapid repeated save creates task only once while first save is active`() = runTest {
        val releaseCreate = CompletableDeferred<Unit>()
        coEvery { apiaryRepository.getAllApiaries() } returns Result.Success(emptyList())
        coEvery { taskRepository.enqueueCreateTask(any()) } coAnswers {
            releaseCreate.await()
            Result.Success(taskResponse(title = "Verifica hrana"))
        }

        val viewModel = viewModel()
        viewModel.saveTask("Verifica hrana", null, TaskPriority.Normal, null, null, null)
        viewModel.saveTask("Verifica hrana", null, TaskPriority.Normal, null, null, null)

        releaseCreate.complete(Unit)
        advanceUntilIdle()

        assertEquals(TaskFormUiState.Success("Task creat cu succes"), viewModel.uiState.value)
        coVerify(exactly = 1) {
            taskRepository.enqueueCreateTask(match { it.title == "Verifica hrana" })
        }
    }

    @Test
    fun `resetState allows another save after success`() = runTest {
        coEvery { apiaryRepository.getAllApiaries() } returns Result.Success(emptyList())
        coEvery { taskRepository.enqueueCreateTask(any()) } returns Result.Success(taskResponse())

        val viewModel = viewModel()
        viewModel.saveTask("Verifica hrana", null, TaskPriority.Normal, null, null, null)
        advanceUntilIdle()

        viewModel.resetState()
        viewModel.saveTask("Curata fundul", null, TaskPriority.Normal, null, null, null)
        advanceUntilIdle()

        coVerify(exactly = 2) { taskRepository.enqueueCreateTask(any()) }
        assertEquals(TaskFormUiState.Success("Task creat cu succes"), viewModel.uiState.value)
    }

    private fun viewModel() = TaskFormViewModel(
        taskRepository = taskRepository,
        apiaryRepository = apiaryRepository,
        hiveRepository = hiveRepository,
        notificationScheduler = notificationScheduler,
        syncScheduler = syncScheduler,
        savedStateHandle = SavedStateHandle()
    )

    private fun taskResponse(title: String = "Task") = TaskResponse(
        id = "task-1",
        userId = "user-1",
        apiaryId = null,
        apiaryName = null,
        hiveId = null,
        hiveName = null,
        title = title,
        description = null,
        priority = TaskPriority.Normal,
        status = TaskStatus.Pending,
        dueDate = null,
        completedAt = null,
        createdAt = "2026-06-06T00:00:00Z",
        updatedAt = "2026-06-06T00:00:00Z"
    )
}
