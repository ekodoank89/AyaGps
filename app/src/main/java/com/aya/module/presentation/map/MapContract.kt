package com.aya.module.presentation.map

/** ===== UI STATE ===== */
data class MapUiState(
    val hasPermission: Boolean = false
)

/** ===== INTENT ===== */
sealed interface MapIntent {
    data object PermissionGranted : MapIntent
    data object PermissionDenied : MapIntent
}
