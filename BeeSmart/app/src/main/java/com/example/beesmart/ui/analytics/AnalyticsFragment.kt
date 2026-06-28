package com.example.beesmart.ui.analytics

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
class AnalyticsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    AnalyticsScreen(
                        onNavigateToApiaries = {
                            findNavController().navigate(R.id.action_analytics_to_apiaries)
                        },
                        onNavigateToInspections = {
                            findNavController().navigate(R.id.action_analytics_to_inspectionList)
                        },
                        onNavigateToTreatments = {
                            findNavController().navigate(R.id.action_analytics_to_treatmentList)
                        },
                        onNavigateToExtractions = {
                            findNavController().navigate(R.id.action_analytics_to_extractionList)
                        },
                        onBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}
