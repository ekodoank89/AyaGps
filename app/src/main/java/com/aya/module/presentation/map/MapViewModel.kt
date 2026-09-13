package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MapViewModel : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> _state.update { it.copy(hasPermission = true) }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }

            // Play: simpan koordinat pin. Stop: hapus titik (chip & marker hilang).
            is MapIntent.ToggleA -> _state.update {
                if (it.isActiveA) it.copy(isActiveA = false, pointA = null)
                else it.copy(isActiveA = true, pointA = intent.pinLocation)
            }
            is MapIntent.ToggleB -> _state.update {
                if (it.isActiveB) it.copy(isActiveB = false, pointB = null)
                else it.copy(isActiveB = true, pointB = intent.pinLocation)
            }
        }
    }
}
