package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.model.LocationData
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
        // Pulihkan kondisi terakhir (A/B aktif + titiknya) saat aplikasi dibuka ulang,
        // termasuk setelah force stop
        viewModelScope.launch {
            val saved = mapStateRepository.load()
            _state.update {
                it.copy(
                    isActiveA = saved.isActiveA,
                    pointA = saved.pointA,
                    isActiveB = saved.isActiveB,
                    pointB = saved.pointB
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
}
