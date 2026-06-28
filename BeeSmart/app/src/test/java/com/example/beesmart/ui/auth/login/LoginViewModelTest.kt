package com.example.beesmart.ui.auth.login

import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.EmailNotConfirmedException
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.AuthResponse
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val connectivity: ConnectivityObserver = mockk {
        every { isCurrentlyOnline() } returns true
        every { isOnline } returns flowOf(true)
    }

    @Test
    fun `validateEmail reports invalid input and clears after valid input`() {
        val viewModel = viewModel()

        viewModel.validateEmail("not-an-email")
        assertEquals("Email invalid", viewModel.validationState.value.emailError)

        viewModel.validateEmail("keeper@example.com")
        assertNull(viewModel.validationState.value.emailError)
    }

    @Test
    fun `validatePassword reports short passwords and clears valid password`() {
        val viewModel = viewModel()

        viewModel.validatePassword("123")
        assertEquals("Minim 6 caractere", viewModel.validationState.value.passwordError)

        viewModel.validatePassword("123456")
        assertNull(viewModel.validationState.value.passwordError)
    }

    @Test
    fun `login with blank fields returns validation error without repository call`() {
        val viewModel = viewModel()

        viewModel.login(" ", "secret")

        assertTrue(viewModel.uiState.value is LoginUiState.Error)
        coVerify(exactly = 0) { authRepository.login(any(), any()) }
    }

    @Test
    fun `login success moves state to success`() = runTest {
        coEvery { authRepository.login("bee@example.com", "secret123") } returns
            Result.Success(AuthResponse(accessToken = "token", refreshToken = "refresh"))
        val viewModel = viewModel()

        viewModel.login("bee@example.com", "secret123")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LoginUiState.Success)
        coVerify(exactly = 1) { authRepository.login("bee@example.com", "secret123") }
    }

    @Test
    fun `login email-not-confirmed error exposes resend state`() = runTest {
        coEvery { authRepository.login("bee@example.com", "secret123") } returns
            Result.Error("Email neconfirmat", 401, EmailNotConfirmedException("bee@example.com"))
        val viewModel = viewModel()

        viewModel.login("bee@example.com", "secret123")
        advanceUntilIdle()

        assertEquals(LoginUiState.EmailNotConfirmed("bee@example.com"), viewModel.uiState.value)
    }

    private fun viewModel() = LoginViewModel(authRepository, connectivity)
}
