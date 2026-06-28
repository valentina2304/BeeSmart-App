package com.example.beesmart.ui.auth.resetpassword

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
class ResetPasswordFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val email = arguments?.getString("email") ?: ""
        val token = arguments?.getString("token") ?: ""

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    ResetPasswordScreen(
                        token = token,
                        email = email,
                        onNavigateToLogin = {
                            findNavController().navigate(R.id.action_resetPassword_to_login)
                        }
                    )
                }
            }
        }
    }
}