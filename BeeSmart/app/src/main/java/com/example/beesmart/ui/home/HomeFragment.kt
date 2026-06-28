package com.example.beesmart.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beesmart.R
import com.example.beesmart.ui.auth.ComposeAuthActivity
import com.example.beesmart.ui.theme.BeeSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    HomeScreen(
                        onNavigateToApiaries = {
                            findNavController().navigate(R.id.action_home_to_apiaries)
                        },
                        onNavigateToTasks = {
                            findNavController().navigate(R.id.action_home_to_tasks)
                        },
                        onNavigateToInspections = {
                            findNavController().navigate(R.id.action_home_to_inspectionList)
                        },
                        onNavigateToProfile = {
                            findNavController().navigate(R.id.action_home_to_userProfile)
                        },
                        onNavigateToQrScanner = {
                            findNavController().navigate(R.id.action_home_to_qrScanner)
                        },
                        onNavigateToTreatments = {
                            findNavController().navigate(R.id.action_home_to_treatmentList)
                        },
                        onNavigateToExtractions = {
                            findNavController().navigate(R.id.action_home_to_extractionList)
                        },
                        onNavigateToCreateInspection = {
                            findNavController().navigate(R.id.action_home_to_createInspection)
                        },
                        onNavigateToNotifications = {
                            findNavController().navigate(R.id.action_home_to_notificationHistory)
                        },
                        onNavigateToAnalytics = {
                            findNavController().navigate(R.id.action_home_to_analytics)
                        },
                        onLogout = {
                            // Launch ComposeAuthActivity and finish MainActivity
                            val intent = Intent(requireContext(), ComposeAuthActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            requireActivity().finish()
                        }
                    )
                }
            }
        }
    }
}
