package com.aya.module.presentation.map

import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.PanelOffset
import com.aya.module.domain.model.SavedCameraState

/** Zoom saat auto-focus ke posisi GPS user */
const val FOCUS_ZOOM = 17f

/** Perintah kamera satu-shot dari ViewModel ke UI */
sealed interface MapCameraEvent {
    /**
     * Terbang ke lokasi target.
     * zoom null = pertahankan zoom saat ini (dipakai saat lompat ke marker A/B).
     */
    data class FlyTo(
        val location: LocationData,
        val zoom: Float? = null
    ) : MapCameraEvent
}

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
    data object FocusPointA : MapIntent
    data object FocusPointB : MapIntent
    data object ToggleTrackPanelLock : MapIntent
    data object ToggleZoomPanelLock : MapIntent
    data class TrackPanelOffsetChanged(val offset: PanelOffset) : MapIntent
    data class ZoomPanelOffsetChanged(val offset: PanelOffset) : MapIntent
    data class SaveCameraState(val camera: SavedCameraState) : MapIntent
}
