package com.example.beesmart.ui.theme

import androidx.compose.ui.graphics.Color

// BeeSmart brand palette: honey, wax, propolis, and apiary green.
val YellowPrimary = Color(0xFFF4A51C)
val YellowLight = Color(0xFFFFE3A3)
val YellowDark = Color(0xFFB56A00)

val BrownPrimary = Color(0xFF6B4423)
val BrownLight = Color(0xFFE7D7BF)
val BrownDark = Color(0xFF3B2614)

val GreenSuccess = Color(0xFF2F7D4A)
val RedError = Color(0xFFB9382E)
val BlueInfo = Color(0xFF2F80A8)

val WaxSurface = Color(0xFFFFF8E6)
val CreamSurface = Color(0xFFFFFCF4)
val SageSoft = Color(0xFFDDEEDB)
val PollenAccent = Color(0xFFFFC857)

// Additional colors
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Gray = Color(0xFF9E9E9E)
val LightGray = Color(0xFFE0E0E0)

// Honey gradient for hero cards
val HoneyGradientStart = Color(0xFFFFF4D9)
val HoneyGradientMid   = Color(0xFFFCE9BA)
val HoneyGradientEnd   = Color(0xFFF4C75D)

// Semantic tokens — the SINGLE source of truth for statuses
val StatusOk     = GreenSuccess          // active / healthy / optimal flight
val StatusWatch  = Color(0xFFC77E00)     // needs watching (single amber)
val StatusDanger = RedError              // critical / queenless
val StatusInfo   = BlueInfo
