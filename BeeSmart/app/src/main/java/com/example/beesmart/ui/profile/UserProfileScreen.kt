package com.example.beesmart.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val lastServerSyncMillis by viewModel.lastServerSyncMillis.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailConfirmed by remember { mutableStateOf(false) }

    var firstNameError by remember { mutableStateOf<String?>(null) }
    var lastNameError by remember { mutableStateOf<String?>(null) }

    // Tracks whether real profile data has arrived. Until then the hero card shows a neutral
    // skeleton instead of fabricated placeholder values (avoids the "Profil BeeSmart" flash).
    var profileLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UserProfileUiState.ProfileLoaded -> {
                firstName = state.profile.firstName ?: ""
                lastName = state.profile.lastName ?: ""
                phoneNumber = state.profile.phoneNumber ?: ""
                email = state.profile.email
                emailConfirmed = state.profile.emailConfirmed
                profileLoaded = true

                state.profile.birthDate?.let { dateStr ->
                    try {
                        val dateString = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
                        birthDate = dateString
                    } catch (e: Exception) {
                        birthDate = ""
                    }
                }
            }
            is UserProfileUiState.Success -> {
                if (state.message.contains("Logged out") || state.message.contains("Deconectare")) {
                    onLogout()
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            val calendar = Calendar.getInstance()
            birthDate.takeIf { it.length == 10 }?.let {
                try {
                    val parts = it.split("-")
                    calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                } catch (_: Exception) { }
            }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    birthDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    showDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).also { dialog ->
                dialog.datePicker.maxDate = System.currentTimeMillis()
                dialog.setOnDismissListener { showDatePicker = false }
                dialog.show()
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.sx_aph_profile_logout_title)) },
            text = { Text(stringResource(R.string.sx_aph_profile_logout_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    }
                ) {
                    Text(stringResource(R.string.sx_aph_profile_logout_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.sx_aph_profile_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sx_aph_profile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_aph_profile_back))
                    }
                },
                actions = {
                    if (!isEditMode) {
                        IconButton(onClick = { viewModel.setEditMode(true) }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.sx_aph_profile_edit_cd))
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.sx_aph_profile_logout_cd))
                        }
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileHeroCard(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    emailConfirmed = emailConfirmed,
                    isOnline = isOnline,
                    isLoaded = profileLoaded,
                    onResendConfirmation = { viewModel.resendConfirmationEmail() },
                    showResend = !emailConfirmed && !isEditMode
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProfileSyncCard(
                    pendingSyncCount = pendingSyncCount,
                    lastServerSyncMillis = lastServerSyncMillis,
                    isOnline = isOnline
                )

                Spacer(modifier = Modifier.height(14.dp))

                val fieldShimmer = rememberShimmerBrush()
                // Profile Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(YellowPrimary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BrownPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.sx_aph_profile_personal_data),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrownPrimary
                                )
                                Text(
                                    text = if (isEditMode) stringResource(R.string.sx_aph_profile_update_account_info) else stringResource(R.string.sx_aph_profile_saved_account_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        if (profileLoaded) {
                        // First Name
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = {
                                firstName = it
                                firstNameError = if (it.length < 2 && it.isNotEmpty()) "Minim 2 caractere" else null
                            },
                            label = { Text(stringResource(R.string.sx_aph_profile_first_name)) },
                            enabled = isEditMode,
                            modifier = Modifier.fillMaxWidth(),
                            isError = firstNameError != null,
                            supportingText = firstNameError?.let { { Text(it) } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YellowPrimary,
                                focusedLabelColor = YellowPrimary,
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Last Name
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = {
                                lastName = it
                                lastNameError = if (it.length < 2 && it.isNotEmpty()) "Minim 2 caractere" else null
                            },
                            label = { Text(stringResource(R.string.sx_aph_profile_last_name)) },
                            enabled = isEditMode,
                            modifier = Modifier.fillMaxWidth(),
                            isError = lastNameError != null,
                            supportingText = lastNameError?.let { { Text(it) } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YellowPrimary,
                                focusedLabelColor = YellowPrimary,
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Phone Number
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text(stringResource(R.string.sx_aph_profile_phone)) },
                            enabled = isEditMode,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YellowPrimary,
                                focusedLabelColor = YellowPrimary,
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Birth Date
                        OutlinedTextField(
                            value = birthDate,
                            onValueChange = { },
                            label = { Text(stringResource(R.string.sx_aph_profile_birth_date)) },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEditMode) {
                                    showDatePicker = true
                                },
                            trailingIcon = {
                                if (isEditMode) {
                                    Icon(Icons.Default.DateRange, stringResource(R.string.sx_aph_profile_select_date_cd))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = if (isEditMode) YellowPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledLabelColor = if (isEditMode) YellowPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = YellowPrimary
                            )
                        )
                        } else {
                            // Shimmer field placeholders while the profile loads.
                            SkeletonField(brush = fieldShimmer)
                            Spacer(modifier = Modifier.height(12.dp))
                            SkeletonField(brush = fieldShimmer)
                            Spacer(modifier = Modifier.height(12.dp))
                            SkeletonField(brush = fieldShimmer)
                            Spacer(modifier = Modifier.height(12.dp))
                            SkeletonField(brush = fieldShimmer)
                        }
                    }
                }

                // Action Buttons (Edit Mode)
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel Button
                        OutlinedButton(
                            onClick = { viewModel.setEditMode(false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = YellowPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 2.dp
                            )
                        ) {
                            Text(stringResource(R.string.sx_aph_profile_cancel))
                        }

                        // Save Button
                        Button(
                            onClick = {
                                // Validation
                                var hasError = false

                                if (firstName.isEmpty() || firstName.length < 2) {
                                    firstNameError = "Minim 2 caractere"
                                    hasError = true
                                }

                                if (lastName.isEmpty() || lastName.length < 2) {
                                    lastNameError = "Minim 2 caractere"
                                    hasError = true
                                }

                                if (!hasError) {
                                    viewModel.updateProfile(
                                        firstName = firstName,
                                        lastName = lastName,
                                        phone = phoneNumber,
                                        birthDate = birthDate.takeIf { it.isNotEmpty() }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowPrimary,
                                contentColor = BrownPrimary
                            )
                        ) {
                            Text(stringResource(R.string.sx_aph_profile_save))
                        }
                    }
                }
            }

            // Loading Indicator — only for actions after the profile is loaded (e.g. saving).
            // The initial load is represented by the shimmer skeleton instead.
            if (uiState is UserProfileUiState.Loading && profileLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x4D000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YellowPrimary)
                }
            }
        }
    }

    // Handle error/success messages
    when (val state = uiState) {
        is UserProfileUiState.Error -> {
            LaunchedEffect(state) {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
        }
        is UserProfileUiState.Success -> {
            if (!state.message.contains("Logged out") && !state.message.contains("Deconectare")) {
                LaunchedEffect(state) {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.resetState()
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun ProfileHeroCard(
    firstName: String,
    lastName: String,
    email: String,
    emailConfirmed: Boolean,
    isOnline: Boolean,
    isLoaded: Boolean,
    showResend: Boolean,
    onResendConfirmation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.97f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HoneyGradientStart.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val shimmer = rememberShimmerBrush()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            brush = if (isLoaded) {
                                Brush.linearGradient(listOf(HoneyGradientStart, YellowLight))
                            } else {
                                shimmer
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .then(
                            if (isLoaded) Modifier.border(
                                width = 1.dp,
                                color = YellowPrimary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(20.dp)
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoaded) {
                        Text(
                            text = profileInitials(firstName, lastName, email),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrownPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                if (isLoaded) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profileDisplayName(firstName, lastName, email),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = email.ifBlank { stringResource(R.string.sx_aph_profile_email_unavailable) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EmailStatusBadge(emailConfirmed)
                            if (!isOnline) OfflineBadge()
                        }
                    }
                } else {
                    // Animated shimmer while the profile loads — no fabricated name.
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkeletonBar(widthFraction = 0.6f, height = 20.dp, brush = shimmer)
                        SkeletonBar(widthFraction = 0.85f, height = 14.dp, brush = shimmer)
                    }
                }
            }

            AnimatedVisibility(
                visible = showResend,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = onResendConfirmation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrownPrimary)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sx_aph_profile_resend_activation))
                }
            }
        }
    }
}

@Composable
private fun EmailStatusBadge(emailConfirmed: Boolean) {
    val background = if (emailConfirmed) GreenSuccess.copy(alpha = 0.13f) else RedError.copy(alpha = 0.11f)
    val foreground = if (emailConfirmed) GreenSuccess else RedError
    val icon = if (emailConfirmed) Icons.Default.CheckCircle else Icons.Default.Info
    val text = if (emailConfirmed) stringResource(R.string.sx_aph_profile_email_verified) else stringResource(R.string.sx_aph_profile_email_unverified)

    Surface(
        shape = RoundedCornerShape(50),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground
            )
        }
    }
}

/** Animated left-to-right shimmer sweep, shared by all skeleton placeholders on this screen. */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "profile_shimmer")
    val translate by transition.animateFloat(
        initialValue = -2 * SHIMMER_WIDTH,
        targetValue = 2 * SHIMMER_WIDTH,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "profile_shimmer_translate"
    )
    val base = MaterialTheme.colorScheme.onSurface
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.07f),
            base.copy(alpha = 0.17f),
            base.copy(alpha = 0.07f)
        ),
        start = Offset(translate, translate),
        end = Offset(translate + SHIMMER_WIDTH, translate + SHIMMER_WIDTH)
    )
}

private const val SHIMMER_WIDTH = 320f

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    brush: Brush,
    cornerRadius: androidx.compose.ui.unit.Dp = 6.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(brush, RoundedCornerShape(cornerRadius))
    )
}

/** Field-shaped shimmer placeholder, matching the height of an OutlinedTextField. */
@Composable
private fun SkeletonField(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(brush, RoundedCornerShape(8.dp))
    )
}

@Composable
private fun OfflineBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = BrownPrimary.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = BrownPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sx_aph_profile_offline),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrownPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ProfileSyncCard(
    pendingSyncCount: Int,
    lastServerSyncMillis: Long?,
    isOnline: Boolean
) {
    val isSynced = pendingSyncCount == 0

    // Offline takes priority: explain that the screen is showing the last saved data.
    val accent: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val title: String
    val subtitle: String
    when {
        !isOnline -> {
            accent = BrownPrimary
            icon = Icons.Default.CloudOff
            title = "Mod offline"
            subtitle = if (isSynced) {
                "Afișăm ultimele date salvate. " + formatLastServerSync(lastServerSyncMillis)
            } else {
                "Afișăm ultimele date salvate. $pendingSyncCount modificări se vor sincroniza la reconectare"
            }
        }
        isSynced -> {
            accent = GreenSuccess
            icon = Icons.Default.CheckCircle
            title = "Date sincronizate"
            subtitle = formatLastServerSync(lastServerSyncMillis)
        }
        else -> {
            accent = YellowPrimary
            icon = Icons.Default.Refresh
            title = "Sincronizare în așteptare"
            subtitle = "$pendingSyncCount modificări așteaptă conexiunea cu cloud-ul"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSynced && isOnline) GreenSuccess else BrownPrimary,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrownPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun profileDisplayName(firstName: String, lastName: String, email: String): String {
    val fullName = listOf(firstName, lastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return fullName.ifBlank { email.substringBefore("@").ifBlank { "Profil BeeSmart" } }
}

private fun profileInitials(firstName: String, lastName: String, email: String): String {
    val parts = listOf(firstName, lastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val source = if (parts.isNotEmpty()) parts else listOf(email.substringBefore("@"))
    return source
        .take(2)
        .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
        .ifBlank { "B" }
}

private fun formatLastServerSync(lastServerSyncMillis: Long?): String {
    if (lastServerSyncMillis == null) {
        return "Nu există încă o sincronizare confirmată în cloud"
    }
    return try {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("ro-RO"))
        val date = Instant.ofEpochMilli(lastServerSyncMillis)
            .atZone(ZoneId.systemDefault())
        "Ultima sincronizare în cloud: ${formatter.format(date)}"
    } catch (e: Exception) {
        "Ultima sincronizare în cloud este salvată"
    }
}
