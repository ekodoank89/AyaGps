package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData

/** ===== UI STATE ===== */
data class MapUiState(
    val hasPermission: Boolean = false,
    val isTrackingA: Boolean = false,
    val isTrackingB: Boolean = false,
    val trackA: List<LocationData> = emptyList(),
    val trackB: List<LocationData> = emptyList()
)

/** ===== INTENT ===== */
sealed interface MapIntent {
    data object PermissionGranted : MapIntent
    data object PermissionDenied : MapIntent
    data object ToggleTrackingA : MapIntent
    data object ToggleTrackingB : MapIntent
}
