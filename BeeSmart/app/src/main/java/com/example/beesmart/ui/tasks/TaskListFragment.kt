package com.example.beesmart.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beesmart.ui.theme.BeeSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TaskListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BeeSmartTheme {
                    TaskListScreen(
                        onNavigateBack = { findNavController().popBackStack() },
                        onTaskClick = { taskId ->
                            // Tapping a card opens the task in the form (view/edit).
                            val action = TaskListFragmentDirections.actionTaskListToEditTask(taskId)
                            findNavController().navigate(action)
                        },
                        onCreateTask = {
                            val action = TaskListFragmentDirections.actionTaskListToCreateTask()
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }
}