package com.example.beesmart.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.beesmart.MainActivity
import com.example.beesmart.ui.auth.forgotpassword.ForgotPasswordScreen
import com.example.beesmart.ui.auth.login.LoginScreen
import com.example.beesmart.ui.auth.register.RegisterScreen
import com.example.beesmart.ui.theme.BeeSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ComposeAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BeeSmartTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthNavigation(
                        onNavigateToHome = {
                            // Navigate to MainActivity with authenticated flag
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("AUTHENTICATED", true)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthNavigation(
    onNavigateToHome: () -> Unit
) {
    val navController = rememberNavController()
    var loginPrefill by remember { mutableStateOf(LoginPrefill()) }

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onNavigateToForgotPassword = {
                    navController.navigate("forgot-password")
                },
                onNavigateToHome = onNavigateToHome,
                prefilledEmail = loginPrefill.email,
                prefilledPassword = loginPrefill.password
            )
        }

        composable("register") {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = { email, password ->
                    loginPrefill = LoginPrefill(email, password)
                    navController.popBackStack()
                }
            )
        }

        composable("forgot-password") {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

private data class LoginPrefill(
    val email: String = "",
    val password: String = ""
)

