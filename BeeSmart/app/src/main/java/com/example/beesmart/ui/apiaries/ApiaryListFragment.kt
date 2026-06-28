package com.example.beesmart.ui.apiaries

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
class ApiaryListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    ApiaryListScreen(
                        onNavigateBack = { findNavController().popBackStack() },
                        onApiaryClick = { apiaryId, apiaryName ->
                            val action = ApiaryListFragmentDirections
                                .actionApiaryListFragmentToHiveListFragment(
                                    apiaryId = apiaryId,
                                    apiaryName = apiaryName
                                )
                            findNavController().navigate(action)
                        },
                        onCreateApiary = {
                            findNavController().navigate(R.id.createApiaryFragment)
                        },
                        onEditApiary = { apiaryId ->
                            val action = ApiaryListFragmentDirections
                                .actionApiaryListToCreateApiary(apiaryId = apiaryId)
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}
