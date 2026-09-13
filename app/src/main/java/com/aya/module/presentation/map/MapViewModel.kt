package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.SavedPanelLocks
import com.aya.module.domain.model.SavedPanelOffsets
import com.aya.module.domain.model.SavedPointsState
import com.aya.module.domain.repository.MapStateRepository
import com.aya.module.domain.usecase.GetCurrentLocationUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val mapStateRepository: MapStateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    /** Event satu-shot (mis. target auto-focus kamera) */
    private val _events = Channel<LocationData>(Channel.BUFFERED)
    val events: Flow<LocationData> = _events.receiveAsFlow()

    init {
        // Pulihkan semua kondisi terakhir saat aplikasi dibuka ulang,
        // termasuk setelah force stop: titik A/B, lock panel, posisi drag, posisi kamera
        viewModelScope.launch {
            val points = mapStateRepository.load()
            val locks = mapStateRepository.loadPanelLocks()
            val offsets = mapStateRepository.loadPanelOffsets()
            val camera = mapStateRepository.loadCameraState()
            _state.update {
                it.copy(
                    isActiveA = points.isActiveA,
                    pointA = points.pointA,
                    isActiveB = points.isActiveB,
                    pointB = points.pointB,
                    isTrackPanelLocked = locks.trackLocked,
                    isZoomPanelLocked = locks.zoomLocked,
                    trackPanelOffset = offsets.track,
                    zoomPanelOffset = offsets.zoom,
                    cameraState = camera
                )
            }
        }
    }

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> _state.update { it.copy(hasPermission = true) }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }

            // Play: simpan koordinat pin. Stop: hapus titik (chip & marker hilang).
            is MapIntent.ToggleA -> {
                _state.update {
                    if (it.isActiveA) it.copy(isActiveA = false, pointA = null)
                    else it.copy(isActiveA = true, pointA = intent.pinLocation)
                }
                persistPoints()
            }
            is MapIntent.ToggleB -> {
                _state.update {
                    if (it.isActiveB) it.copy(isActiveB = false, pointB = null)
                    else it.copy(isActiveB = true, pointB = intent.pinLocation)
                }
                persistPoints()
            }

            // Lock panel: toggle + persistenkan
            MapIntent.ToggleTrackPanelLock -> {
                _state.update { it.copy(isTrackPanelLocked = !it.isTrackPanelLocked) }
                persistLocks()
            }
            MapIntent.ToggleZoomPanelLock -> {
                _state.update { it.copy(isZoomPanelLocked = !it.isZoomPanelLocked) }
                persistLocks()
            }

            // Posisi drag panel (dikirim saat gesture selesai) + persistenkan
            is MapIntent.TrackPanelOffsetChanged -> {
                _state.update { it.copy(trackPanelOffset = intent.offset) }
                persistPanelOffsets()
            }
            is MapIntent.ZoomPanelOffsetChanged -> {
                _state.update { it.copy(zoomPanelOffset = intent.offset) }
                persistPanelOffsets()
            }

            // Simpan posisi kamera/pin terakhir (dipanggil saat aplikasi ditinggalkan)
            is MapIntent.SaveCameraState -> viewModelScope.launch {
                mapStateRepository.saveCameraState(intent.camera)
            }

            // Auto-focus: ambil posisi GPS terkini, kirim sebagai event kamera
            MapIntent.FocusCurrentLocation -> viewModelScope.launch {
                try {
                    getCurrentLocation()?.let { _events.send(it) }
                } catch (_: SecurityException) {
                    // izin lokasi belum tersedia — abaikan
                }
            }
        }
    }

    /** Simpan kondisi A/B ke disk setiap kali berubah */
    private fun persistPoints() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.save(
                SavedPointsState(
                    isActiveA = s.isActiveA,
                    pointA = s.pointA,
                    isActiveB = s.isActiveB,
                    pointB = s.pointB
                )
            )
        }
    }

    /** Simpan kondisi lock kedua panel ke disk setiap kali berubah */
    private fun persistLocks() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.savePanelLocks(
                SavedPanelLocks(
                    trackLocked = s.isTrackPanelLocked,
                    zoomLocked = s.isZoomPanelLocked
                )
            )
        }
    }

    /** Simpan posisi drag kedua panel ke disk setiap kali berubah */
    private fun persistPanelOffsets() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.savePanelOffsets(
                SavedPanelOffsets(
                    track = s.trackPanelOffset,
                    zoom = s.zoomPanelOffset
                )
            )
        }
    }
}
