package com.example.beesmart.ui.auth.forgotpassword

import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForgotPasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    @Test
    fun `validateEmail reports invalid address`() {
        val viewModel = ForgotPasswordViewModel(authRepository)

        viewModel.validateEmail("bad")

        assertEquals("Email invalid", viewModel.validationState.value.emailError)
    }

    @Test
    fun `sendResetEmail with blank email sets validation error without repository call`() {
        val viewModel = ForgotPasswordViewModel(authRepository)

        viewModel.sendResetEmail(" ")

        assertEquals("Introdu email-ul", viewModel.validationState.value.emailError)
        coVerify(exactly = 0) { authRepository.forgotPassword(any()) }
    }

    @Test
    fun `sendResetEmail success exposes success message`() = runTest {
        coEvery { authRepository.forgotPassword("bee@example.com") } returns
            Result.Success("Email trimis")
        val viewModel = ForgotPasswordViewModel(authRepository)

        viewModel.sendResetEmail("bee@example.com")
        advanceUntilIdle()

        assertEquals(ForgotPasswordUiState.Success("Email trimis"), viewModel.uiState.value)
    }

    @Test
    fun `sendResetEmail error exposes repository message`() = runTest {
        coEvery { authRepository.forgotPassword("bee@example.com") } returns
            Result.Error("Nu exista cont")
        val viewModel = ForgotPasswordViewModel(authRepository)

        viewModel.sendResetEmail("bee@example.com")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ForgotPasswordUiState.Error)
        assertEquals("Nu exista cont", (viewModel.uiState.value as ForgotPasswordUiState.Error).message)
    }
}
