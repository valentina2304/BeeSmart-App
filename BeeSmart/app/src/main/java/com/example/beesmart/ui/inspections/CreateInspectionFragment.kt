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
import com.example.beesmart.utils.PhotoManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CreateInspectionFragment : Fragment() {

    private val args: CreateInspectionFragmentArgs by navArgs()
    
    @Inject
    lateinit var photoManager: PhotoManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    InspectionFormScreen(
                        inspectionId = args.inspectionId,
                        hiveId = args.hiveId,
                        onNavigateBack = { findNavController().popBackStack() },
                        photoManager = photoManager
                    )
                }
            }
        }
    }
}
