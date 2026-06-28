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
class CreateHiveFragment : Fragment() {

    private val args: CreateHiveFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    HiveFormScreen(
                        apiaryName = args.apiaryName,
                        onNavigateBack = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}