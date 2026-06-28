package com.example.beesmart.ui.inspections

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
class InspectionListFragment : Fragment() {

    private val args: InspectionListFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    InspectionListScreen(
                        hiveId = args.hiveId,
                        hiveName = args.hiveName,
                        apiaryId = args.apiaryId,
                        apiaryName = args.apiaryName,
                        onNavigateBack = { findNavController().popBackStack() },
                        onCreateInspection = { hiveId ->
                            val action = InspectionListFragmentDirections
                                .actionInspectionListToCreateInspection(
                                    inspectionId = null,
                                    hiveId = hiveId
                                )
                            findNavController().navigate(action)
                        },
                        onEditInspection = { inspectionId ->
                            val action = InspectionListFragmentDirections
                                .actionInspectionListToCreateInspection(
                                    inspectionId = inspectionId,
                                    hiveId = null
                                )
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}