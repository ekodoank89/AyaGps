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

    /** Satu pintu masuk untuk semua intent (pola MVI) */
    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> onPermissionGranted()
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }
            MapIntent.StartTracking -> startTracking()
            MapIntent.StopTracking -> stopTracking()
            is MapIntent.LocationReceived -> _state.update {
                it.copy(location = intent.location, isLoading = false, error = null)
            }
            is MapIntent.ShowError -> _state.update {
                it.copy(error = intent.message, isLoading = false)
            }
        }
    }

    private fun onPermissionGranted() {
        _state.update { it.copy(hasPermission = true) }
        loadLastKnownLocation()
    }

    private fun startTracking() {
        if (_state.value.isTracking) return
        trackingJob?.cancel()
        _state.update { it.copy(isTracking = true, isLoading = true) }
        trackingJob = viewModelScope.launch {
            getLocationUpdates()
                .catch { e ->
                    onIntent(MapIntent.ShowError(e.message ?: "Gagal melacak lokasi"))
                }
                .collect { onIntent(MapIntent.LocationReceived(it)) }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        _state.update { it.copy(isTracking = false, isLoading = false) }
    }

    private fun loadLastKnownLocation() {
        viewModelScope.launch {
            try {
                getCurrentLocation()?.let { loc ->
                    _state.update { it.copy(location = loc) }
                }
            } catch (_: SecurityException) {
                // izin belum tersedia
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onCleared() {
        trackingJob?.cancel()
        super.onCleared()
    }
}
