package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.PanelOffset
import com.aya.module.domain.model.SavedCameraState

/** ===== UI STATE ===== */
data class MapUiState(
    val hasPermission: Boolean = false,
    val isActiveA: Boolean = false,
    val isActiveB: Boolean = false,
    val pointA: LocationData? = null,
    val pointB: LocationData? = null,
    val isTrackPanelLocked: Boolean = false,
    val isZoomPanelLocked: Boolean = false,
    val trackPanelOffset: PanelOffset? = null,
    val zoomPanelOffset: PanelOffset? = null,
    val cameraState: SavedCameraState? = null
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
    data class TrackPanelOffsetChanged(val offset: PanelOffset) : MapIntent
    data class ZoomPanelOffsetChanged(val offset: PanelOffset) : MapIntent
    data class SaveCameraState(val camera: SavedCameraState) : MapIntent
}
