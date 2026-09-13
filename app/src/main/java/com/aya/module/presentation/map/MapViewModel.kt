package com.aya.module.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aya.module.domain.model.FavoritePoint
import com.aya.module.domain.model.JitterConfig
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

    /** Perintah kamera satu-shot (auto-focus GPS / lompat ke marker) */
    private val _events = Channel<MapCameraEvent>(Channel.BUFFERED)
    val events: Flow<MapCameraEvent> = _events.receiveAsFlow()

    private var jitterJobA: Job? = null
    private var jitterJobB: Job? = null
    private var jitterPersistJob: Job? = null

    init {
        // Pulihkan semua kondisi terakhir, termasuk setelah force stop
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
                    favorite = points.favorite,
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
            // Lanjutkan jitter yang tertunda saat aplikasi dimatikan
            if (_state.value.isJitterActiveA) startJitterJob(JitterTarget.A)
            if (_state.value.isJitterActiveB) startJitterJob(JitterTarget.B)
        }
    }

    fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.PermissionGranted -> _state.update { it.copy(hasPermission = true) }
            MapIntent.PermissionDenied -> _state.update { it.copy(hasPermission = false) }

            // Play: simpan koordinat pin. Stop: hapus titik + hentikan jitter terkait.
            is MapIntent.ToggleA -> {
                val wasActive = _state.value.isActiveA
                if (wasActive) stopJitterInternal(JitterTarget.A)
                _state.update {
                    if (it.isActiveA) it.copy(isActiveA = false, pointA = null)
                    else it.copy(isActiveA = true, pointA = intent.pinLocation)
                }
                persistPoints()
                if (wasActive) persistJitter()
            }
            is MapIntent.ToggleB -> {
                val wasActive = _state.value.isActiveB
                if (wasActive) stopJitterInternal(JitterTarget.B)
                _state.update {
                    if (it.isActiveB) it.copy(isActiveB = false, pointB = null)
                    else it.copy(isActiveB = true, pointB = intent.pinLocation)
                }
                persistPoints()
                if (wasActive) persistJitter()
            }

            // Favorite: simpan/ganti/hapus (persisten)
            is MapIntent.SaveFavorite -> {
                _state.update {
                    it.copy(favorite = FavoritePoint(intent.name, intent.location))
                }
                persistPoints()
            }
            MapIntent.DeleteFavorite -> {
                _state.update { it.copy(favorite = null) }
                persistPoints()
            }

            // Jitter: mulai/hentikan gerak acak untuk target
            is MapIntent.ToggleJitter -> toggleJitter(intent.target)

            // Ubah setelan jitter (berlaku di tick berikutnya); simpan dengan debounce
            is MapIntent.UpdateJitterConfig -> {
                _state.update {
                    if (intent.target == JitterTarget.A) it.copy(jitterConfigA = intent.config)
                    else it.copy(jitterConfigB = intent.config)
                }
                persistJitterDebounced()
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

            // Auto-focus: ambil posisi GPS terkini, kirim sebagai perintah kamera
            MapIntent.FocusCurrentLocation -> viewModelScope.launch {
                try {
                    getCurrentLocation()?.let { loc ->
                        _events.send(MapCameraEvent.FlyTo(loc, FOCUS_ZOOM))
                    }
                } catch (_: SecurityException) {
                    // izin lokasi belum tersedia — abaikan
                }
            }

            // Tap chip: terbang ke posisi marker (zoom dipertahankan)
            MapIntent.FocusPointA -> flyToPoint { it.pointA }
            MapIntent.FocusPointB -> flyToPoint { it.pointB }
            MapIntent.FocusFavorite -> flyToPoint { it.favorite?.location }
        }
    }

    /** Kirim perintah kamera ke titik terpilih jika ada */
    private fun flyToPoint(selector: (MapUiState) -> LocationData?) {
        selector(_state.value)?.let { point ->
            viewModelScope.launch {
                _events.send(MapCameraEvent.FlyTo(point))
            }
        }
    }

    // ================== JITTER (GERAK ACAK) ==================

    private fun toggleJitter(target: JitterTarget) {
        val isActiveNow = when (target) {
            JitterTarget.A -> _state.value.isJitterActiveA
            JitterTarget.B -> _state.value.isJitterActiveB
        }
        if (isActiveNow) {
            stopJitterInternal(target)
            persistJitter()
        } else {
            // Titik dasar = posisi titik saat jitter diaktifkan (hasil play dari pin)
            val base = when (target) {
                JitterTarget.A -> _state.value.pointA
                JitterTarget.B -> _state.value.pointB
            } ?: return // titik belum di-play — abaikan
            _state.update {
                if (target == JitterTarget.A) it.copy(isJitterActiveA = true, jitterBaseA = base)
                else it.copy(isJitterActiveB = true, jitterBaseB = base)
            }
            startJitterJob(target)
            persistJitter()
        }
    }

    private fun stopJitterInternal(target: JitterTarget) {
        if (target == JitterTarget.A) {
            jitterJobA?.cancel()
            jitterJobA = null
            _state.update { it.copy(isJitterActiveA = false, jitterBaseA = null) }
        } else {
            jitterJobB?.cancel()
            jitterJobB = null
            _state.update { it.copy(isJitterActiveB = false, jitterBaseB = null) }
        }
    }

    /**
     * Loop jitter: tiap interval detik, titik bergerak acak maksimal `step` meter,
     * selalu dijaga tetap dalam `radius` dari titik dasar.
     * Setelan dibaca ulang tiap tick sehingga perubahan slider langsung berlaku.
     */
    private fun startJitterJob(target: JitterTarget) {
        val job = viewModelScope.launch {
            val base = (if (target == JitterTarget.A) _state.value.jitterBaseA
            else _state.value.jitterBaseB) ?: return@launch
            var offX = 0.0
            var offY = 0.0
            while (isActive) {
                val config = if (target == JitterTarget.A) _state.value.jitterConfigA
                else _state.value.jitterConfigB
                delay(config.intervalSeconds * 1000L)
                val stillActive = if (target == JitterTarget.A) _state.value.isJitterActiveA
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
                    if (target == JitterTarget.A) it.copy(pointA = newPos)
                    else it.copy(pointB = newPos)
                }
            }
        }
        if (target == JitterTarget.A) jitterJobA = job else jitterJobB = job
    }

    // ================== PERSISTENSI ==================

    /** Simpan kondisi A/B + favorit ke disk setiap kali berubah */
    private fun persistPoints() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.save(
                SavedPointsState(
                    isActiveA = s.isActiveA,
                    pointA = s.pointA,
                    isActiveB = s.isActiveB,
                    pointB = s.pointB,
                    favorite = s.favorite
                )
            )
        }
    }

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

    private fun persistJitter() {
        val s = _state.value
        viewModelScope.launch {
            mapStateRepository.saveJitter(
                SavedJitterState(
                    configA = s.jitterConfigA,
                    configB = s.jitterConfigB,
                    isActiveA = s.isJitterActiveA,
                    baseA = s.jitterBaseA,
                    isActiveB = s.isJitterActiveB,
                    baseB = s.jitterBaseB
                )
            )
        }
    }

    /** Debounce: slider menghasilkan banyak event — simpan 400ms setelah perubahan terakhir */
    private fun persistJitterDebounced() {
        jitterPersistJob?.cancel()
        jitterPersistJob = viewModelScope.launch {
            delay(400)
            persistJitter()
        }
    }

    override fun onCleared() {
        jitterJobA?.cancel()
        jitterJobB?.cancel()
        super.onCleared()
    }
}
