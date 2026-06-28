package com.example.beesmart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.beesmart.BuildConfig
import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.di.UnauthenticatedClient
import com.example.beesmart.network.AuthApi
import com.example.beesmart.network.models.ConfirmEmailRequest
import com.example.beesmart.ui.auth.ComposeAuthActivity
import com.example.beesmart.ui.splash.SplashScreen
import com.example.beesmart.ui.theme.BeeSmartTheme
import com.example.beesmart.utils.NetworkConfig
import com.example.beesmart.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val deepLinkHost = BuildConfig.DEEP_LINK_HOST.lowercase(Locale.US)
    private val httpsScheme = BuildConfig.DEEP_LINK_SCHEME.lowercase(Locale.US)
    private val customScheme = BuildConfig.CUSTOM_SCHEME.lowercase(Locale.US)
    private val confirmEmailPath = "/confirm-email"
    private val resetPasswordPath = "/reset-password"
    private val hivePathSegment = "hive"

    @Inject
    @UnauthenticatedClient
    lateinit var authApi: AuthApi

    @Inject
    lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Log network configuration for debugging
        Log.d(TAG, NetworkConfig.getDebugInfo())

        val isAuthenticated = intent.getBooleanExtra("AUTHENTICATED", false)
        val showSplash = !isAuthenticated && !hasDeepLink(intent)

        if (showSplash) {
            setContent {
                BeeSmartTheme {
                    SplashScreen(onFinished = { runAuthCheck() })
                }
            }
        } else {
            runAuthCheck()
        }
    }

    private fun runAuthCheck() {
        val sessionManager = SessionManager(this)

        lifecycleScope.launch {
            try {
                val token = sessionManager.accessTokenFlow.first()
                val refreshToken = sessionManager.refreshTokenFlow.first()
                val isAuthenticated = intent.getBooleanExtra("AUTHENTICATED", false)
                val hasValidAccessToken = !token.isNullOrEmpty() && !sessionManager.isTokenExpired()
                val hasRefreshableSession = !refreshToken.isNullOrEmpty()
                val hasSession = hasValidAccessToken || hasRefreshableSession

                Log.d(
                    TAG,
                    "startup session: tokenPresent=${!token.isNullOrEmpty()}, " +
                        "refreshTokenPresent=$hasRefreshableSession, " +
                        "hasValidAccessToken=$hasValidAccessToken, isAuthenticatedIntent=$isAuthenticated"
                )

                if (!hasSession && !hasDeepLink(intent) && !isAuthenticated) {
                    if (!token.isNullOrEmpty()) {
                        Log.d(TAG, "Stored session cannot be refreshed; clearing tokens and launching auth")
                        sessionManager.clearTokens()
                    }
                    Log.d(TAG, "No valid session found, launching ComposeAuthActivity")
                    val authIntent = Intent(this@MainActivity, ComposeAuthActivity::class.java)
                    startActivity(authIntent)
                    finish()
                } else {
                    if (hasValidAccessToken) {
                        val jwtUserId = extractUserIdFromJwt(token.orEmpty())
                        val storedUserId = sessionManager.getCurrentUserId()
                        if (jwtUserId != null && jwtUserId != storedUserId) {
                            Log.d(TAG, "User mismatch at startup (jwt=$jwtUserId stored=$storedUserId) — clearing cache")
                            appDatabase.clearAllTables()
                            sessionManager.saveUserId(jwtUserId)
                        }
                    }
                    setupMainUI(isAuthenticated)
                }
            } catch (e: Exception) {
                Log.w(TAG, "token read error", e)
                val authIntent = Intent(this@MainActivity, ComposeAuthActivity::class.java)
                startActivity(authIntent)
                finish()
            }
        }
    }

    private fun setupMainUI(isAuthenticated: Boolean) {
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Get NavController from the NavHostFragment
        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        val navController = navHost?.navController
        navController?.let { setupBottomNavigation(it) }

        val handledDeepLink = !isAuthenticated && hasDeepLink(intent)
        if (handledDeepLink) {
            handleDeepLink(intent)
        }

        // Navigate to home if authenticated and not already there
        if (!handledDeepLink && navController != null && navController.currentDestination?.id != R.id.homeFragment) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .setLaunchSingleTop(true)
                .build()
            Log.d(TAG, "Navigating to home from MainActivity (authenticated: $isAuthenticated)")
            navController.navigate(R.id.homeFragment, null, navOptions)
        }
    }

    private fun setupBottomNavigation(navController: NavController) {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val destinationToMenuItem = mapOf(
            R.id.homeFragment to R.id.homeFragment,
            R.id.apiaryListFragment to R.id.apiaryListFragment,
            R.id.hiveListFragment to R.id.apiaryListFragment,
            R.id.hiveDetailFragment to R.id.apiaryListFragment,
            R.id.hiveStatsFragment to R.id.apiaryListFragment,
            R.id.inspectionListFragment to R.id.inspectionListFragment,
            R.id.inspectionDetailFragment to R.id.inspectionListFragment,
            R.id.taskListFragment to R.id.taskListFragment,
            R.id.userProfileFragment to R.id.userProfileFragment
        )
        val hiddenDestinations = setOf(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment,
            R.id.resetPasswordFragment,
            R.id.createApiaryFragment,
            R.id.createHiveFragment,
            R.id.editHiveFragment,
            R.id.createInspectionFragment,
            R.id.createTaskFragment,
            R.id.qrScannerFragment,
            R.id.treatmentListFragment,
            R.id.createTreatmentFragment,
            R.id.extractionListFragment,
            R.id.createExtractionFragment,
            R.id.analyticsFragment
        )

        bottomNavigation.setOnItemSelectedListener { item ->
            navigateToTopLevelDestination(navController, item.itemId)
        }

        bottomNavigation.setOnItemReselectedListener { item ->
            navigateToTopLevelDestination(navController, item.itemId)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigation.isVisible = destination.id !in hiddenDestinations
            val selectedItemId = destinationToMenuItem[destination.id]
            if (selectedItemId != null && bottomNavigation.selectedItemId != selectedItemId) {
                bottomNavigation.menu.findItem(selectedItemId)?.isChecked = true
            }
            bottomNavigation.labelVisibilityMode =
                com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_UNLABELED
        }
    }

    private fun navigateToTopLevelDestination(
        navController: NavController,
        destinationId: Int
    ): Boolean {
        val currentDestination = navController.currentDestination?.id
        if (currentDestination == destinationId) {
            navController.popBackStack(destinationId, false)
            return true
        }

        if (navController.popBackStack(destinationId, false)) {
            return true
        }

        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.homeFragment, false)
            .build()

        return runCatching {
            navController.navigate(destinationId, null, options)
        }.onFailure { error ->
            Log.e(TAG, "Bottom navigation failed for $destinationId", error)
        }.isSuccess
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        setIntent(intent)

        val isAuthenticated = intent.getBooleanExtra("AUTHENTICATED", false)
        if (isAuthenticated) {
            // User just authenticated, navigate to home
            val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
            val navController = navHost?.navController

            if (navController != null && navController.currentDestination?.id != R.id.homeFragment) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .setLaunchSingleTop(true)
                    .build()
                Log.d(TAG, "onNewIntent: navigating to home (authenticated)")
                navController.navigate(R.id.homeFragment, null, navOptions)
            }
        } else {
            handleDeepLink(intent)
        }
    }

    private fun hasDeepLink(intent: Intent): Boolean {
        return intent.data != null
    }

    private fun getQueryParameterDecoded(uri: Uri, paramName: String): String? {
        val query = uri.encodedQuery ?: return null

        val params = query.split("&")
        for (param in params) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2 && pair[0] == paramName) {
                return Uri.decode(pair[1])
            }
        }
        return null
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host?.lowercase(Locale.US)
        val path = uri.path ?: ""

        Log.d(
            TAG,
            "Deep link received: scheme=$scheme, host=$host, path=$path, " +
                "tokenPresent=${!getQueryParameterDecoded(uri, "token").isNullOrEmpty()}, " +
                "emailPresent=${!getQueryParameterDecoded(uri, "email").isNullOrEmpty()}"
        )

        when {
            scheme == customScheme && uri.host == "confirm-email" -> {
                val token = getQueryParameterDecoded(uri, "token")
                val email = getQueryParameterDecoded(uri, "email")
                if (!token.isNullOrEmpty() && !email.isNullOrEmpty()) {
                    confirmEmailDirectly(email, token)
                } else {
                    showError("Link invalid - lipsesc date necesare")
                }
            }

            scheme == customScheme && uri.host == "reset-password" -> {
                val token = getQueryParameterDecoded(uri, "token")
                val email = getQueryParameterDecoded(uri, "email")
                if (!token.isNullOrEmpty() && !email.isNullOrEmpty()) {
                    navigateToResetPassword(email, token)
                } else {
                    showError("Link invalid - lipsesc date necesare")
                }
            }

            scheme == customScheme && uri.host == "open" -> {
                navigateToLogin()
            }

            scheme == customScheme && uri.host == hivePathSegment -> {
                val hiveId = uri.lastPathSegment
                if (!hiveId.isNullOrEmpty()) {
                    navigateToHiveEdit(hiveId)
                } else {
                    showError("Link stup invalid - lipseste ID-ul")
                }
            }

            scheme == httpsScheme && host == deepLinkHost && path == confirmEmailPath -> {
                val token = getQueryParameterDecoded(uri, "token")
                val email = getQueryParameterDecoded(uri, "email")
                if (!token.isNullOrEmpty() && !email.isNullOrEmpty()) {
                    confirmEmailDirectly(email, token)
                } else {
                    showError("Link invalid - lipsesc date necesare")
                }
            }

            scheme == httpsScheme && host == deepLinkHost && path == resetPasswordPath -> {
                val token = getQueryParameterDecoded(uri, "token")
                val email = getQueryParameterDecoded(uri, "email")
                if (!token.isNullOrEmpty() && !email.isNullOrEmpty()) {
                    navigateToResetPassword(email, token)
                } else {
                    showError("Link invalid - lipsesc date necesare")
                }
            }

            scheme == httpsScheme && host == deepLinkHost && uri.pathSegments.firstOrNull() == hivePathSegment -> {
                val hiveId = uri.pathSegments.getOrNull(1)
                    ?: getQueryParameterDecoded(uri, "hiveId")
                if (!hiveId.isNullOrEmpty()) {
                    navigateToHiveEdit(hiveId)
                } else {
                    showError("Link stup invalid - lipseste ID-ul")
                }
            }

            else -> {
                Log.d(TAG, "Unknown deep link: ${uri.scheme}://${uri.host}${uri.path}")
            }
        }
    }

    private fun confirmEmailDirectly(email: String, token: String) {
        lifecycleScope.launch {
            try {
                // authApi is now injected via Hilt
                val request = ConfirmEmailRequest(token = token, email = email)

                Log.d(TAG, "Confirming email from deep link")
                val response = authApi.confirmEmail(request)

                if (response.isSuccessful) {
                    Log.d(TAG, "Email confirmed successfully")
                    showSuccess("Email confirmat cu succes! Poți să te autentifici acum.")

                    // Navigate to login after a short delay
                    findViewById<android.view.View>(android.R.id.content).postDelayed({
                        navigateToLogin()
                    }, 2000)
                } else {
                    Log.e(TAG, "Email confirmation failed: ${response.code()}")
                    val errorMsg = when (response.code()) {
                        400 -> "Token invalid sau expirat"
                        else -> "Eroare la confirmare: ${response.code()}"
                    }
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Email confirmation exception", e)
                showError("Eroare de rețea - verifică conexiunea")
            }
        }
    }

    private fun navigateToResetPassword(email: String, token: String) {
        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        val navController = navHost?.navController

        if (navController != null) {
            try {
                val bundle = Bundle().apply {
                    putString("email", email)
                    putString("token", token)
                }

                Log.d(TAG, "Navigating to resetPasswordFragment from deep link")
                navController.navigate(R.id.resetPasswordFragment, bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to reset password failed", e)
                showError("Eroare la navigare - verifică configurarea aplicației")
            }
        } else {
            Log.e(TAG, "NavController is null")
            showError("Eroare internă - NavController null")
        }
    }

    private fun navigateToLogin() {
        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        val navController = navHost?.navController

        if (navController != null) {
            try {
                // Clear backstack and navigate to login
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()

                navController.navigate(R.id.loginFragment, null, navOptions)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to login failed", e)
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showSuccess(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToHiveEdit(hiveId: String) {
        val navHost = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        val navController = navHost?.navController

        if (navController != null) {
            try {
                val bundle = Bundle().apply {
                    putString("hiveId", hiveId)
                }
                Log.d(TAG, "Navigating to editHiveFragment with hiveId=$hiveId")
                navController.navigate(R.id.editHiveFragment, bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to hive edit failed", e)
                showError("Nu s-a putut deschide formularul de editare a stupului")
            }
        } else {
            Log.e(TAG, "NavController is null")
            showError("Eroare internă - NavController null")
        }
    }

    private fun extractUserIdFromJwt(jwt: String): String? {
        return try {
            val payload = jwt.split(".").getOrNull(1) ?: return null
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val json = String(decoded, Charsets.UTF_8)
            Regex(""""sub"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}
