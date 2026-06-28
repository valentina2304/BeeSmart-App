package com.example.beesmart.ui.hives

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.ui.theme.BlueInfo
import com.example.beesmart.ui.theme.Gray
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.StatusWatch

fun HiveType.localizedName(): String = when (this) {
    HiveType.Langstroth -> "Stup vertical modular - Langstroth"
    HiveType.Dadant -> "Stup vertical cu rame mari - Dadant"
    HiveType.TopBar -> "Stup orizontal cu bare - Top-Bar"
    HiveType.Warre -> "Stup vertical natural - Warré"
    HiveType.Other -> "Alt tip de stup"
}

fun HiveType.shortName(): String = when (this) {
    HiveType.Langstroth -> "Langstroth"
    HiveType.Dadant -> "Dadant"
    HiveType.TopBar -> "Top-Bar"
    HiveType.Warre -> "Warre"
    HiveType.Other -> "Alt tip"
}

fun HiveStatus.localizedName(): String = when (this) {
    HiveStatus.Active -> "Activ"
    HiveStatus.Queenless -> "Fără regină"
    HiveStatus.Weak -> "Slab"
    HiveStatus.Sick -> "Bolnav"
    HiveStatus.Preparing -> "În pregătire"
    HiveStatus.Inactive -> "Inactiv"
}

fun HiveStatus.statusIcon(): ImageVector = when (this) {
    HiveStatus.Active -> Icons.Default.CheckCircle
    HiveStatus.Queenless -> Icons.Default.Warning
    HiveStatus.Weak -> Icons.Default.Warning
    HiveStatus.Sick -> Icons.Default.Warning
    HiveStatus.Preparing -> Icons.Default.Build
    HiveStatus.Inactive -> Icons.Default.Clear
}

fun HiveStatus.statusColor(): Color = when (this) {
    HiveStatus.Active -> GreenSuccess
    HiveStatus.Queenless -> StatusWatch
    HiveStatus.Weak -> StatusWatch
    HiveStatus.Sick -> RedError
    HiveStatus.Preparing -> BlueInfo
    HiveStatus.Inactive -> Gray
}
