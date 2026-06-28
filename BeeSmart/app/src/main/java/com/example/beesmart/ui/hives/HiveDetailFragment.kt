package com.example.beesmart.ui.hives

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.beesmart.ui.theme.BeeSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HiveDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    HiveDetailScreen(
                        onNavigateBack = { findNavController().popBackStack() },
                        onNavigateToTreatments = { hiveId: String ->
                            val action = HiveDetailFragmentDirections
                                .actionHiveDetailToTreatmentList(hiveId = hiveId)
                            findNavController().navigate(action)
                        },
                        onNavigateToExtractions = { hiveId: String ->
                            val action = HiveDetailFragmentDirections
                                .actionHiveDetailToExtractionList(hiveId = hiveId)
                            findNavController().navigate(action)
                        },
                        onNavigateToStats = { hiveId: String ->
                            val action = HiveDetailFragmentDirections
                                .actionHiveDetailToHiveStats(hiveId = hiveId)
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
