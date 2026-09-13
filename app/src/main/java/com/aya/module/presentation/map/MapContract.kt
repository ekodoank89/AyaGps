package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData

/** ===== UI STATE ===== */
data class MapUiState(
    val hasPermission: Boolean = false,
    val location: LocationData? = null
)

/** ===== INTENT ===== */
sealed interface MapIntent {
    data object PermissionGranted : MapIntent
    data object PermissionDenied : MapIntent
}
