package com.example.beesmart.ui.treatment

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
class TreatmentListFragment : Fragment() {
    private val args: TreatmentListFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    TreatmentListScreen(
                        hiveId = args.hiveId,
                        onNavigateBack = { findNavController().popBackStack() },
                        onAddTreatment = {
                            val action = TreatmentListFragmentDirections
                                .actionTreatmentListToCreateTreatment(
                                    hiveId = args.hiveId,
                                    treatmentId = null
                                )
                            findNavController().navigate(action)
                        },
                        onTreatmentClick = { treatment ->
                            val action = TreatmentListFragmentDirections
                                .actionTreatmentListToCreateTreatment(
                                    hiveId = args.hiveId,
                                    treatmentId = treatment.id
                                )
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
