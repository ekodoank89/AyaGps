package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.model.FavoritePoint
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.SavedJitterState
import com.aya.module.domain.model.SavedPanelLocks
import com.aya.module.domain.model.SavedPanelOffsets
import com.aya.module.domain.model.SavedPointsState
import com.aya.module.domain.model.offsetByMeters
import com.aya.module.domain.repository.MapStateRepository
import com.aya.module.domain.usecase.GetCurrentLocationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MapViewModel(
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val mapStateRepository: MapStateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val _events = Channel<MapCameraEvent>(Channel.BUFFERED)
    val events: Flow<MapCameraEvent> = _events.receiveAsFlow()

    private var jitterJobA: Job? = null
    private var jitterJobB: Job? = null
    private var jitterPersistJob: Job? = null

    init {
        viewModelScope.launch {
            val points = mapStateRepository.load()
            val locks = mapStateRepository.loadPanelLocks()
            val offsets = mapStateRepository.loadPanelOffsets()
            val camera = mapStateRepository.loadCameraState()
            val jitter = mapStateRepository.loadJitter()
            _state.update {
                it.copy(
                    isActiveA = points.isActiveA,
                    pointA = points.pointA,
                    isActiveB = points.isActiveB,
                    pointB = points.pointB,
                    favoritesA = points.favoritesA,
                    favoritesB = points.favoritesB,
                    isPinChipVisible = points.isPinChipVisible,
                    jitterConfigA = jitter.configA,
                    jitterConfigB = jitter.configB,
                    isJitterActiveA = jitter.isActiveA && points.pointA != null,
                    jitterBaseA = jitter.baseA?.takeIf { points.pointA != null },
                    isJitterActiveB = jitter.isActiveB && points.pointB != null,
                    jitterBaseB = jitter.baseB?.takeIf { points.pointB != null },
                    isTrackPanelLocked = locks.trackLocked,
                    isZoomPanelLocked = locks.zoomLocked,
                    trackPanelOffset = offsets.track,
                    zoomPanelOffset = offsets.zoom,
                    cameraState = camera
                )
            }
            if (_state.value.isJitterActiveA) startJitterJob(PointCategory.A)
            if (_state.value.isJitterActiveB) startJitterJob(PointCategory.B)
        }
    }

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> _state.update { it.copy(hasPermission = true) }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }
            MapIntent.PermissionsFlowDone -> _state.update { it.copy(permissionsFlowDone = true) }

            is MapIntent.ToggleA -> togglePlay(PointCategory.A, intent.pinLocation)
            is MapIntent.ToggleB -> togglePlay(PointCategory.B, intent.pinLocation)

            is MapIntent.SaveFavorite -> {
                _state.update { s ->
                    val current = if (intent.category == PointCategory.A) s.favoritesA else s.favoritesB
                    val updated = current.toMutableList().apply {
                        val idx = intent.index
                        if (idx != null && idx >= 0 && idx < size) {
                            set(idx, FavoritePoint(intent.name, intent.location))
                        } else {
                            add(FavoritePoint(intent.name, intent.location))
                        }
                    }
                    if (intent.category == PointCategory.A) s.copy(favoritesA = updated)
                    else s.copy(favoritesB = updated)
                }
                persistPoints()
            }

            is MapIntent.DeleteFavorite -> {
                _state.update { s ->
                    val current = if (intent.category == PointCategory.A) s.favoritesA else s.favoritesB
                    val updated = current.toMutableList().apply {
                        if (intent.index >= 0 && intent.index < size) removeAt(intent.index)
                    }
                    if (intent.category == PointsCategory.A) s.copy(favoritesA = updated)
                    else s.copy(favoritesB = updated)
                }
                persistPoints()
            }

            is MapIntent.PlayFavorite -> playFavorite(intent.category, intent.index)

            is MapIntent.ToggleJitter -> toggleJitter(intent.target)

            is MapIntent.UpdateJitterConfig -> {
                _state.update {
                    if (intent.target == PointCategory.A) it.copy(jitterConfigA = intent.config)
                    else it.copy(jitterConfigB = intent.config)
                }
                persistJitterDebounced()
            }

            MapIntent.ToggleTrackPanelLock -> {
                _state.update { it.copy(isTrackPanelLocked = !it.isTrackPanelLocked) }
                persistLocks()
            }
            MapIntent.ToggleZoomPanelLock -> {
                _state.update { it.copy(isZoomPanelLocked = !it.isZoomPanelLocked) }
                persistLocks()
            }

            is MapIntent.TrackPanelOffsetChanged -> {
                _state.update { it.copy(trackPanelOffset = intent.offset) }
                persistPanelOffsets()
            }
            is MapIntent.ZoomPanelOffsetChanged -> {
                _state.update { it.copy(zoomPanelOffset = intent.offset) }
                persistPanelOffsets()
            }

            is MapIntent.SaveCameraState -> viewModelScope.launch {
                mapStateRepository.saveCameraState(intent.camera)
            }

            is MapIntent.PinChipVisibilityChanged -> {
                _state.update { it.copy(isPinChipVisible = intent.visible) }
                persistPoints()
            }

            MapIntent.FocusCurrentLocation -> viewModelScope.launch {
                try {
                    getCurrentLocation()?.let { loc ->
                        _events.send(MapCameraEvent.FlyTo(loc, FOCUS_ZOOM))
                    }
                } catch (_: SecurityException) {
                }
            }

            MapIntent.FocusPointA -> flyToPoint { it.pointA }
            MapIntent.FocusPointB -> flyToPoint { it.pointB }
        }
    }

    private fun flyToPoint(selector: (MapUiState) -> LocationData?) {
        selector(_state.value)?.let { point ->
            viewModelScope.launch {
                _events.send(MapCameraEvent.FlyTo(point))
            }
        }
    }

    // ================== PLAY / STOP ==================

    private fun togglePlay(category: PointCategory, pin: LocationData) {
        val wasActive = if (category == PointCategory.A) _state.value.isActiveA
        else _state.value.isActiveB
        if (wasActive) {
            stopJitterInternal(category)
            _state.update {
                if (category == PointCategory.A) it.copy(isActiveA = false, pointA = null)
                else it.copy(isActiveB = false, pointB = null)
            }
        } else {
            _state.update {
                if (category == PointCategory.A) it.copy(
                    isActiveA = true, pointA = pin,
                    isJitterActiveA = true, jitterBaseA = pin
                )
                else it.copy(
                    isActiveB = true, pointB = pin,
                    isJitterActiveB = true, jitterBaseB = pin
                )
            }
            startJitterJob(category)
        }
        persistPoints()
        persistJitter()
    }

    private fun playFavorite(category: PointCategory, index: Int) {
        val s = _state.value
        val fav = (if (category == PointCategory.A) s.favoritesA else s.favoritesB)
            .getOrNull(index) ?: return
        stopJitterInternal(category)
        _state.update {
            if (category == PointCategory.A) it.copy(
                isActiveA = true, pointA = fav.location,
                isJitterActiveA = true, jitterBaseA = fav.location
            )
            else it.copy(
                isActiveB = true, pointB = fav.location,
                isJitterActiveB = true, jitterBaseB = fav.location
            )
        }
        startJitterJob(category)
        persistPoints()
        persistJitter()
        viewModelScope.launch {
            _events.send(MapCameraEvent.FlyTo(fav.location))
        }
    }

    // ================== JITTER ==================

    private fun toggleJitter(category: PointCategory) {
        val activeNow = if (category == PointCategory.A) _state.value.isJitterActiveA
        else _state.value.isJitterActiveB
        if (activeNow) {
            stopJitterInternal(category)
        } else {
            val base = if (category == PointCategory.A) _state.value.pointA
            else _state.value.pointB
            base ?: return
            _state.update {
                if (category == PointCategory.A) it.copy(isJitterActiveA = true, jitterBaseA = base)
                else it.copy(isJitterActiveB = true, jitterBaseB = base)
            }
            startJitterJob(category)
        }
        persistJitter()
    }

    private fun stopJitterInternal(category: PointCategory) {
        if (category == PointCategory.A) {
            jitterJobA?.cancel()
            jitterJobA = null
            _state.update { it.copy(isJitterActiveA = false, jitterBaseA = null) }
        } else {
            jitterJobB?.cancel()
            jitterJobB = null
            _state.update { it.copy(isJitterActiveB = false, jitterBaseB = null) }
        }
    }

    private fun startJitterJob(category: PointCategory) {
        val job = viewModelScope.launch {
            val base = (if (category == PointCategory.A) _state.value.jitterBaseA
            else _state.value.jitterBaseB) ?: return@launch
            var offX = 0.0
            var offY = 0.0
            while (isActive) {
                val config = if (category == PointCategory.A) _state.value.jitterConfigA
                else _state.value.jitterConfigB
                delay(config.intervalSeconds * 1000L)
                val stillActive = if (category == PointCategory.A) _state.value.isJitterActiveA
                else _state.value.isJitterActiveB
                if (!stillActive) break

                val angle = Random.nextDouble(0.0, 2.0 * PI)
                val dist = Random.nextDouble(0.0, config.stepMeters.toDouble())
                var nx = offX + dist * cos(angle)
                var ny = offY + dist * sin(angle)
                val distFromBase = sqrt(nx * nx + ny * ny)
                if (distFromBase > config.radiusMeters) {
                    val scale = config.radiusMeters / distFromBase
                    nx *= scale
                    ny *= scale
                }
                offX = nx
                offY = ny
                val newPos = base.offsetByMeters(offX, offY)
                _state.update {
                    if (category == PointCategory.A) it.copy(pointA = newPos)
                    else it.copy(pointB = newPos)
                }
            }
        }
        if (category == PointCategory.A) jitterJobA = job else jitterJobB = job
    }

    // ================== PERSISTENSI ==================

    private fun persistPoints() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.save(
                SavedPointsState(
                    isActiveA = s.isActiveA,
                    pointA = s.pointA,
                    isActiveB = s.isActiveB,
                    pointB = s.pointB,
                    favoritesA = s.favoritesA,
                    favoritesB = s.favoritesB,
                    isPinChipVisible = s.isPinChipVisible
                )
            )
        }
    }

    private fun persistLocks() { ... }  // TIDAK BERUBAH dari versi sebelumnya
