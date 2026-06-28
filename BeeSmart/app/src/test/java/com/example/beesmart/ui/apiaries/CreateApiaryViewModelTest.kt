package com.example.beesmart.ui.apiaries

import androidx.lifecycle.SavedStateHandle
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.sync.SyncScheduler
import com.example.beesmart.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateApiaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: ApiaryRepository = mockk()
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)

    @Test
    fun `rapid repeated save creates apiary only once while first save is active`() = runTest {
        val releaseCreate = CompletableDeferred<Unit>()
        coEvery { repository.getCachedApiaries() } returns emptyList()
        coEvery { repository.enqueueCreateApiary(any()) } coAnswers {
            releaseCreate.await()
            Result.Success(apiaryResponse(name = "Stupina 1"))
        }

        val viewModel = viewModel()
        viewModel.saveApiary("Stupina 1", null, "Brasov")
        viewModel.saveApiary("Stupina 1", null, "Brasov")

        releaseCreate.complete(Unit)
        advanceUntilIdle()

        assertEquals(CreateApiaryUiState.Success("Stupina creată"), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.getCachedApiaries() }
        coVerify(exactly = 1) {
            repository.enqueueCreateApiary(match { it.name == "Stupina 1" && it.location == "Brasov" })
        }
    }

    @Test
    fun `resetState allows another save after success`() = runTest {
        coEvery { repository.getCachedApiaries() } returns emptyList()
        coEvery { repository.enqueueCreateApiary(any()) } returns Result.Success(apiaryResponse())

        val viewModel = viewModel()
        viewModel.saveApiary("Stupina 1", null, null)
        advanceUntilIdle()

        viewModel.resetState()
        viewModel.saveApiary("Stupina 2", "Livada", "Sibiu")
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.enqueueCreateApiary(any()) }
        assertEquals(CreateApiaryUiState.Success("Stupina creată"), viewModel.uiState.value)
    }

    private fun viewModel() = CreateApiaryViewModel(repository, syncScheduler, SavedStateHandle())

    private fun apiaryResponse(name: String = "Stupina") = ApiaryResponse(
        id = "apiary-1",
        userId = "user-1",
        name = name,
        description = null,
        location = null,
        hiveCount = 0,
        createdAt = "2026-06-06T00:00:00Z",
        updatedAt = "2026-06-06T00:00:00Z"
    )
}
