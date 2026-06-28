package com.example.beesmart.ui.extraction

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
class ExtractionListFragment : Fragment() {
    private val args: ExtractionListFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    ExtractionListScreen(
                        hiveId = args.hiveId,
                        onNavigateBack = { findNavController().popBackStack() },
                        onAddExtraction = {
                            val action = ExtractionListFragmentDirections
                                .actionExtractionListToCreateExtraction(
                                    hiveId = args.hiveId,
                                    extractionId = null
                                )
                            findNavController().navigate(action)
                        },
                        onExtractionClick = { extraction ->
                            val action = ExtractionListFragmentDirections
                                .actionExtractionListToCreateExtraction(
                                    hiveId = args.hiveId,
                                    extractionId = extraction.id
                                )
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
