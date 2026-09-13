package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData

/** ===== UI STATE (satu objek immutable) ===== */
data class MapUiState(
    val hasPermission: Boolean = false,
    val isTracking: Boolean = false,
    val isLoading: Boolean = false,
    val location: LocationData? = null,
    val error: String? = null
)

/** ===== INTENT (semua event user/sistem masuk lewat sini) ===== */
sealed interface MapIntent {
    data object PermissionGranted : MapIntent
    data object PermissionDenied : MapIntent
    data object StartTracking : MapIntent
    data object StopTracking : MapIntent
    data class LocationReceived(val location: LocationData) : MapIntent
    data class ShowError(val message: String) : MapIntent
}
