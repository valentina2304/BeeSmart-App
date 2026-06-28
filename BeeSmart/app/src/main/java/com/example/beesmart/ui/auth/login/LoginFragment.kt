package com.example.beesmart.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beesmart.R
import com.example.beesmart.ui.theme.BeeSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val prefilledEmail = savedStateHandle?.remove<String>("registered_email").orEmpty()
        val prefilledPassword = savedStateHandle?.remove<String>("registered_password").orEmpty()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    LoginScreen(
                        onNavigateToRegister = {
                            findNavController().navigate(R.id.action_login_to_register)
                        },
                        onNavigateToForgotPassword = {
                            findNavController().navigate(R.id.action_login_to_forgotPassword)
                        },
                        onNavigateToHome = {
                            findNavController().navigate(R.id.action_login_to_home)
                        },
                        prefilledEmail = prefilledEmail,
                        prefilledPassword = prefilledPassword
                    )
                }
            }
        }
    }
}
