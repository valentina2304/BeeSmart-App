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
class HiveListFragment : Fragment() {

    private val args: HiveListFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    HiveListScreen(
                        apiaryName = args.apiaryName,
                        onNavigateBack = { findNavController().popBackStack() },
                        onHiveClick = { hiveId ->
                            val action = HiveListFragmentDirections
                                .actionHiveListFragmentToHiveDetailFragment(hiveId)
                            findNavController().navigate(action)
                        },
                        onCreateHive = { apiaryId, apiaryName ->
                            val action = HiveListFragmentDirections
                                .actionHiveListFragmentToCreateHiveFragment(
                                    apiaryId = apiaryId,
                                    apiaryName = apiaryName
                                )
                            findNavController().navigate(action)
                        },
                        onEditHive = { hiveId ->
                            val action = HiveListFragmentDirections
                                .actionHiveListFragmentToEditHiveFragment(hiveId)
                            findNavController().navigate(action)
                        },
                        onViewHiveInspections = { hiveId, hiveName ->
                            val action = HiveListFragmentDirections
                                .actionHiveListFragmentToInspectionListFragment(
                                    hiveId = hiveId,
                                    hiveName = hiveName,
                                    apiaryId = args.apiaryId,
                                    apiaryName = args.apiaryName
                                )
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}