package com.example.beesmart.ui.tasks

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.beesmart.R
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.ui.components.BeeSmartEmptyState
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TaskListScreen(
    onNavigateBack: () -> Unit,
    onTaskClick: (String) -> Unit, // taskId
    onCreateTask: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var taskToDelete by remember { mutableStateOf<TaskResponse?>(null) }
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) hasSeenInitialResume = true
                else viewModel.loadTasks(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notifPermission.status.isGranted) notifPermission.launchPermissionRequest()
        }
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TaskListUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            is TaskListUiState.OperationSuccess -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    // Delete confirmation dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text(stringResource(R.string.sx_tte_task_delete_title)) },
            text = {
                Text("Sigur vrei să ștergi \"${task.title}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask(task.id)
                        taskToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.sx_tte_task_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text(stringResource(R.string.sx_tte_task_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.sx_tte_task_list_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_tte_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadTasks(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.sx_tte_refresh))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTask,
                icon = { Icon(Icons.Default.Add, stringResource(R.string.sx_tte_add_task_cd)) },
                text = { Text(stringResource(R.string.sx_tte_new_task)) },
                containerColor = YellowPrimary,
                contentColor = BrownPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedFilter.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                contentColor = BrownPrimary,
                edgePadding = 0.dp
            ) {
                TaskFilter.values().forEach { filter ->
                    Tab(
                        selected = selectedFilter == filter,
                        onClick = {
                            selectedFilter = filter
                            viewModel.setFilter(filter)
                        },
                        text = { Text(getFilterText(filter)) }
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is TaskListUiState.Success -> {
                        if (state.tasks.isEmpty()) {
                            // Empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tasks),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    getEmptyStateText(selectedFilter),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.sx_tte_empty_hint),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Tasks list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (selectedFilter == TaskFilter.ALL) {
                                    item {
                                        TaskSummaryHeader(tasks = state.tasks)
                                    }
                                }
                                items(
                                    items = state.tasks,
                                    key = { it.id }
                                ) { task ->
                                    TaskCard(
                                        task = task,
                                        onClick = { onTaskClick(task.id) },
                                        onDelete = { taskToDelete = task },
                                        onCompleteToggle = { isChecked ->
                                            viewModel.completeTask(task.id, isChecked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is TaskListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YellowPrimary)
                        }
                    }
                    is TaskListUiState.Error -> {
                        // Error is shown via snackbar, show retry option
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.sx_tte_task_load_error),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadTasks(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowPrimary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.sx_tte_retry))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sx_tte_retry))
                            }
                        }
                    }
                    is TaskListUiState.OperationSuccess -> {
                        // Success is shown via snackbar
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryHeader(tasks: List<TaskResponse>) {
    val completed = tasks.count { it.status == TaskStatus.Completed }
    val overdue = tasks.count { it.dueDate?.let(::isOverdue) == true && it.status != TaskStatus.Completed }
    val pending = tasks.size - completed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = WaxSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(YellowPrimary.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tasks),
                        contentDescription = null,
                        tint = BrownPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sx_tte_work_plan_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.sx_tte_work_plan_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskCountTile(stringResource(R.string.sx_tte_count_total), tasks.size.toString(), BrownPrimary, Modifier.weight(1f))
                TaskCountTile(stringResource(R.string.sx_tte_count_active), pending.toString(), GreenSuccess, Modifier.weight(1f))
                TaskCountTile(stringResource(R.string.sx_tte_count_overdue), overdue.toString(), RedError, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TaskCountTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.11f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
fun TaskCard(
    task: TaskResponse,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCompleteToggle: (Boolean) -> Unit
) {
    val isCompleted = task.status == TaskStatus.Completed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) BrownLight.copy(alpha = 0.3f) else WaxSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with checkbox, title and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { checked ->
                        onCompleteToggle(checked)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = GreenSuccess,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.sx_tte_delete_cd),
                            tint = RedError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Priority chip and due date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val priorityColors = getPriorityChipColors(task.priority)

                // Priority chip
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            getPriorityText(task.priority),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = priorityColors.content
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = priorityColors.container,
                        labelColor = priorityColors.content
                    ),
                    modifier = Modifier.height(28.dp)
                )

                // Due date
                task.dueDate?.let { dueDate ->
                    val isOverdue = isOverdue(dueDate) && !isCompleted
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isOverdue) RedError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDate(dueDate),
                            fontSize = 13.sp,
                            color = if (isOverdue) RedError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location (Apiary/Hive)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildLocationText(task),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Helper functions
private fun getFilterText(filter: TaskFilter): String = when (filter) {
    TaskFilter.ALL -> "Toate"
    TaskFilter.PENDING -> "În așteptare"
    TaskFilter.OVERDUE -> "Întârziate"
    TaskFilter.COMPLETED -> "Finalizate"
}

private fun getEmptyStateText(filter: TaskFilter): String = when (filter) {
    TaskFilter.ALL -> "Niciun task încă"
    TaskFilter.PENDING -> "Niciun task în așteptare"
    TaskFilter.OVERDUE -> "Niciun task întârziat"
    TaskFilter.COMPLETED -> "Niciun task finalizat"
}

private fun getPriorityText(priority: TaskPriority): String = when (priority) {
    TaskPriority.Low -> "Prioritate scăzută"
    TaskPriority.Normal -> "Prioritate normală"
    TaskPriority.High -> "Prioritate înaltă"
    TaskPriority.Critical -> "Prioritate critică"
}

private data class PriorityChipColors(
    val container: Color,
    val content: Color
)

private fun getPriorityChipColors(priority: TaskPriority): PriorityChipColors = when (priority) {
    TaskPriority.Low -> PriorityChipColors(
        container = Color(0xFFE4F1E7),
        content = Color(0xFF245D38)
    )
    TaskPriority.Normal -> PriorityChipColors(
        container = Color(0xFFF0E4D5),
        content = BrownDark
    )
    TaskPriority.High -> PriorityChipColors(
        container = Color(0xFFFFE4AD),
        content = Color(0xFF6F4300)
    )
    TaskPriority.Critical -> PriorityChipColors(
        container = Color(0xFFF5D3CF),
        content = Color(0xFF7A241E)
    )
}

private fun buildLocationText(task: TaskResponse): String {
    val parts = mutableListOf<String>()
    task.apiaryName?.let { parts.add(it) }
    task.hiveName?.let { parts.add(it) }
    return if (parts.isEmpty()) "Task general" else parts.joinToString(" → ")
}

private fun formatDate(dateString: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(dateString)
        val localDate = zonedDateTime.toLocalDate()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        when (localDate) {
            today -> "Astăzi"
            tomorrow -> "Mâine"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("d MMM")
                localDate.format(formatter)
            }
        }
    } catch (e: Exception) {
        dateString
    }
}

private fun isOverdue(dateString: String): Boolean {
    return try {
        val zonedDateTime = ZonedDateTime.parse(dateString)
        val dueDate = zonedDateTime.toLocalDate()
        dueDate.isBefore(LocalDate.now())
    } catch (e: Exception) {
        false
    }
}
