package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData

/** ===== UI STATE ===== */
data class MapUiState(
    val hasPermission: Boolean = false,
    val isActiveA: Boolean = false,
    val isActiveB: Boolean = false,
    val pointA: LocationData? = null,
    val pointB: LocationData? = null,
    val isTrackPanelLocked: Boolean = false,
    val isZoomPanelLocked: Boolean = false
)

/** ===== INTENT ===== */
sealed interface MapIntent {
    data object PermissionGranted : MapIntent
    data object PermissionDenied : MapIntent
    data class ToggleA(val pinLocation: LocationData) : MapIntent
    data class ToggleB(val pinLocation: LocationData) : MapIntent
    data object FocusCurrentLocation : MapIntent
    data object ToggleTrackPanelLock : MapIntent
    data object ToggleZoomPanelLock : MapIntent
}
