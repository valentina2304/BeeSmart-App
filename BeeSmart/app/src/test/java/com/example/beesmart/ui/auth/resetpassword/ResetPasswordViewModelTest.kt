package com.example.beesmart.ui.auth.resetpassword

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    @Test
    fun `setTokenAndEmail with missing data marks invalid token`() {
        val viewModel = ResetPasswordViewModel(authRepository)

        viewModel.setTokenAndEmail("", "bee@example.com")

        assertEquals(ResetPasswordUiState.InvalidToken, viewModel.uiState.value)
    }

    @Test
    fun `password validation catches short and mismatched passwords`() {
        val viewModel = ResetPasswordViewModel(authRepository)

        viewModel.validateNewPassword("short")
        viewModel.validateConfirmPassword("different")

        assertEquals("Minim 8 caractere", viewModel.validationState.value.newPasswordError)
        assertEquals("Parolele nu coincid", viewModel.validationState.value.confirmPasswordError)

        viewModel.validateNewPassword("Password1!")
        viewModel.validateConfirmPassword("Password1!")

        assertNull(viewModel.validationState.value.newPasswordError)
        assertNull(viewModel.validationState.value.confirmPasswordError)
    }

    @Test
    fun `resetPassword without token marks invalid token and does not call repository`() {
        val viewModel = ResetPasswordViewModel(authRepository)

        viewModel.resetPassword("Password1!", "Password1!")

        assertEquals(ResetPasswordUiState.InvalidToken, viewModel.uiState.value)
        coVerify(exactly = 0) { authRepository.resetPassword(any(), any(), any()) }
    }

    @Test
    fun `resetPassword with mismatched confirmation sets validation error`() {
        val viewModel = ResetPasswordViewModel(authRepository)
        viewModel.setTokenAndEmail("token", "bee@example.com")

        viewModel.resetPassword("Password1!", "Password2!")

        assertEquals("Parolele nu coincid", viewModel.validationState.value.confirmPasswordError)
        coVerify(exactly = 0) { authRepository.resetPassword(any(), any(), any()) }
    }

    @Test
    fun `resetPassword success exposes success message`() = runTest {
        coEvery { authRepository.resetPassword("token", "bee@example.com", "Password1!") } returns
            Result.Success("Parola schimbata")
        val viewModel = ResetPasswordViewModel(authRepository)
        viewModel.setTokenAndEmail("token", "bee@example.com")

        viewModel.resetPassword("Password1!", "Password1!")
        advanceUntilIdle()

        assertEquals(ResetPasswordUiState.Success("Parola schimbata"), viewModel.uiState.value)
        coVerify(exactly = 1) {
            authRepository.resetPassword("token", "bee@example.com", "Password1!")
        }
    }

    @Test
    fun `resetPassword error exposes repository message`() = runTest {
        coEvery { authRepository.resetPassword("token", "bee@example.com", "Password1!") } returns
            Result.Error("Token invalid")
        val viewModel = ResetPasswordViewModel(authRepository)
        viewModel.setTokenAndEmail("token", "bee@example.com")

        viewModel.resetPassword("Password1!", "Password1!")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ResetPasswordUiState.Error)
        assertEquals("Token invalid", (viewModel.uiState.value as ResetPasswordUiState.Error).message)
    }
}
