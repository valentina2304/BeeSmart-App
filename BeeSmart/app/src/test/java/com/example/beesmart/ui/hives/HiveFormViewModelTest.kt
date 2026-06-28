package com.example.beesmart.ui.hives

import androidx.lifecycle.SavedStateHandle
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
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
class HiveFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: HiveRepository = mockk()
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)

    @Test
    fun `rapid repeated save creates hive only once while first save is active`() = runTest {
        val releaseCreate = CompletableDeferred<Unit>()
        coEvery { repository.getCachedHivesByApiaryId("apiary-1") } returns emptyList()
        coEvery { repository.enqueueCreateHive(eq("apiary-1"), any()) } coAnswers {
            releaseCreate.await()
            Result.Success(hiveResponse(name = "Stup 1"))
        }

        val viewModel = viewModel()
        viewModel.saveHive(
            name = "Stup 1",
            type = HiveType.Langstroth,
            status = HiveStatus.Active,
            notes = null,
            reginaPrezenta = true,
            varstaRegina = 1,
            rameAlbine = 8,
            ramePuiet = 5,
            rameMiere = 2
        )
        viewModel.saveHive(
            name = "Stup 1",
            type = HiveType.Langstroth,
            status = HiveStatus.Active,
            notes = null,
            reginaPrezenta = true,
            varstaRegina = 1,
            rameAlbine = 8,
            ramePuiet = 5,
            rameMiere = 2
        )

        releaseCreate.complete(Unit)
        advanceUntilIdle()

        assertEquals(HiveFormUiState.Success("Stup creat"), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.getCachedHivesByApiaryId("apiary-1") }
        coVerify(exactly = 1) {
            repository.enqueueCreateHive(
                "apiary-1",
                match {
                    it.name == "Stup 1" &&
                        it.reginaPrezenta &&
                        it.rameAlbine == 8 &&
                        it.ramePuiet == 5 &&
                        it.rameMiere == 2
                }
            )
        }
    }

    @Test
    fun `resetState allows another save after success`() = runTest {
        coEvery { repository.getCachedHivesByApiaryId("apiary-1") } returns emptyList()
        coEvery { repository.enqueueCreateHive(eq("apiary-1"), any()) } returns Result.Success(hiveResponse())

        val viewModel = viewModel()
        viewModel.saveHive("Stup 1", HiveType.Langstroth, HiveStatus.Active, null, false, 0, 0, 0, 0)
        advanceUntilIdle()

        viewModel.resetState()
        viewModel.saveHive("Stup 2", HiveType.Dadant, HiveStatus.Preparing, "roi nou", true, 1, 6, 3, 1)
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.enqueueCreateHive(eq("apiary-1"), any()) }
        assertEquals(HiveFormUiState.Success("Stup creat"), viewModel.uiState.value)
    }

    private fun viewModel() = HiveFormViewModel(
        repository = repository,
        syncScheduler = syncScheduler,
        savedStateHandle = SavedStateHandle(mapOf("apiaryId" to "apiary-1"))
    )

    private fun hiveResponse(name: String = "Stup") = HiveResponse(
        id = "hive-1",
        apiaryId = "apiary-1",
        apiaryName = "Stupina 1",
        name = name,
        type = HiveType.Langstroth,
        status = HiveStatus.Active,
        notes = null,
        createdAt = "2026-06-06T00:00:00Z",
        updatedAt = "2026-06-06T00:00:00Z"
    )
}
