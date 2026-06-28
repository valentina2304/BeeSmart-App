package com.example.beesmart.ui.auth.register

import com.example.beesmart.data.repository.AuthRepository
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
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val connectivity: ConnectivityObserver = mockk {
        every { isCurrentlyOnline() } returns true
        every { isOnline } returns flowOf(true)
    }

    @Test
    fun `field validation updates validation and password strength`() {
        val viewModel = viewModel()

        viewModel.validateFirstName("A")
        viewModel.validateLastName("B")
        viewModel.validatePhoneNumber("123")
        viewModel.validateEmail("bad")
        viewModel.validatePassword("Password1!")
        viewModel.validateConfirmPassword("different")

        val validation = viewModel.validationState.value
        assertEquals("Prenumele este prea scurt", validation.firstNameError)
        assertEquals("Numele este prea scurt", validation.lastNameError)
        assertTrue(validation.phoneNumberError?.contains("telefon invalid") == true)
        assertEquals("Email invalid", validation.emailError)
        assertNull(validation.passwordError)
        assertEquals("Parolele nu se potrivesc", validation.confirmPasswordError)
        assertEquals(2, viewModel.passwordStrength.value?.level)
    }

    @Test
    fun `register with missing required fields returns error without repository call`() {
        val viewModel = viewModel()

        viewModel.register("", "Pop", "", "bee@example.com", "Password1!", "Password1!", null)

        assertTrue(viewModel.uiState.value is RegisterUiState.Error)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register with mismatched passwords sets confirm validation without repository call`() {
        val viewModel = viewModel()

        viewModel.register("Ana", "Pop", "", "bee@example.com", "Password1!", "Password2!", null)

        assertEquals("Parolele nu se potrivesc", viewModel.validationState.value.confirmPasswordError)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `register success sends normalized request and exposes local success message`() = runTest {
        coEvery {
            authRepository.register("bee@example.com", "Password1!", "Ana", "Pop", null, null)
        } returns Result.Success(AuthResponse(message = "Verifica email-ul"))
        val viewModel = viewModel()

        viewModel.register("Ana", "Pop", "", "bee@example.com", "Password1!", "Password1!", null)
        advanceUntilIdle()

        assertEquals(RegisterUiState.Success("Cont creat cu succes."), viewModel.uiState.value)
        coVerify(exactly = 1) {
            authRepository.register("bee@example.com", "Password1!", "Ana", "Pop", null, null)
        }
    }

    private fun viewModel() = RegisterViewModel(authRepository, connectivity)
}
