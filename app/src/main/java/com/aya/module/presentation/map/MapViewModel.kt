package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.usecase.GetLocationUpdatesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val getLocationUpdates: GetLocationUpdatesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var jobA: Job? = null
    private var jobB: Job? = null

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> _state.update { it.copy(hasPermission = true) }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }
            MapIntent.ToggleTrackingA -> toggleTracking(isA = true)
            MapIntent.ToggleTrackingB -> toggleTracking(isA = false)
        }
    }

    private fun toggleTracking(isA: Boolean) {
        val active = if (isA) _state.value.isTrackingA else _state.value.isTrackingB
        if (active) stopTracking(isA) else startTracking(isA)
    }

    private fun startTracking(isA: Boolean) {
        _state.update {
            if (isA) it.copy(isTrackingA = true) else it.copy(isTrackingB = true)
        }
        val job = viewModelScope.launch {
            getLocationUpdates(intervalMillis = 1_000L) // 1 detik: lintasan lebih halus
                .catch {
                    _state.update {
                        if (isA) it.copy(isTrackingA = false) else it.copy(isTrackingB = false)
                    }
                }
                .collect { loc ->
                    _state.update { s ->
                        when {
                            isA && s.isTrackingA -> s.copy(trackA = s.trackA + loc)
                            !isA && s.isTrackingB -> s.copy(trackB = s.trackB + loc)
                            else -> s
                        }
                    }
                }
        }
        if (isA) jobA = job else jobB = job
    }

    private fun stopTracking(isA: Boolean) {
        if (isA) jobA?.cancel() else jobB?.cancel()
        _state.update {
            if (isA) it.copy(isTrackingA = false) else it.copy(isTrackingB = false)
        }
    }

    override fun onCleared() {
        jobA?.cancel()
        jobB?.cancel()
        super.onCleared()
    }
}
