package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.usecase.GetCurrentLocationUseCase
import com.aya.module.domain.usecase.GetLocationUpdatesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val getLocationUpdates: GetLocationUpdatesUseCase,
    private val getCurrentLocation: GetCurrentLocationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var trackingJob: Job? = null

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> {
                _state.update { it.copy(hasPermission = true) }
                startTracking()
            }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            // Langsung tampilkan posisi terakhir (kalau ada), lalu ikuti update real-time
            getCurrentLocation()?.let { last ->
                _state.update { it.copy(location = last) }
            }
            getLocationUpdates()
                .catch { /* diam saja — tampilkan apa yang sudah ada */ }
                .collect { loc -> _state.update { it.copy(location = loc) } }
        }
    }

    override fun onCleared() {
        trackingJob?.cancel()
        super.onCleared()
    }
}
