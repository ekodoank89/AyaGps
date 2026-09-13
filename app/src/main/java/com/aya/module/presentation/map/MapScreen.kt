package com.aya.module.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aya.module.AyaGpsApp
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.PanelOffset
import com.aya.module.domain.model.SavedCameraState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val DEFAULT_POSITION = LatLng(-6.2088, 106.8456) // Jakarta
private val STANDARD_ZOOM = 16f
private val MAX_ZOOM = 20f

private val PinRed = Color(0xFFE53935)
private val TrackAColor = Color(0xFF1E88E5) // biru
private val TrackBColor = Color(0xFFFB8C00) // oranye

/** Titik jangkar awal panel */
private enum class PanelAnchor { BottomCenter, CenterEnd }

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AyaGpsApp
    val viewModel: MapViewModel = viewModel {
        MapViewModel(
            getCurrentLocation = app.container.getCurrentLocation,
            mapStateRepository = app.container.mapStateRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Kamera di-hoist: dibaca tombol A/B, dan dikendalikan autofocus/zoom
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, STANDARD_ZOOM)
    }

    // Pulihkan posisi kamera/pin terakhir dari disk (sekali, setelah data dimuat)
    var cameraRestored by remember { mutableStateOf(false) }
    LaunchedEffect(state.cameraState) {
        val saved = state.cameraState ?: return@LaunchedEffect
        if (!cameraRestored) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(saved.latitude, saved.longitude), saved.zoom
            )
            cameraRestored = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onIntent(
            if (result.values.all { it }) MapIntent.PermissionGranted
            else MapIntent.PermissionDenied
        )
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) viewModel.onIntent(MapIntent.PermissionGranted)
        else permissionLauncher.launch(PERMISSIONS)
    }

    // Perintah kamera satu-shot: auto-focus GPS (zoom FOCUS_ZOOM)
    // dan lompat ke marker A/B (zoom saat ini dipertahankan)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MapCameraEvent.FlyTo -> {
                    val target = LatLng(
                        event.location.latitude, event.location.longitude
                    )
                    val zoom = event.zoom ?: cameraPositionState.position.zoom
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(target, zoom)
                    )
                }
            }
        }
    }

    // Simpan posisi kamera/pin terakhir saat aplikasi ditinggalkan
    // (home, layar mati, buka recents) — menjamin data tersimpan sebelum force stop
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val pos = cameraPositionState.position
                viewModel.onIntent(
                    MapIntent.SaveCameraState(
                        SavedCameraState(
                            latitude = pos.target.latitude,
                            longitude = pos.target.longitude,
                            zoom = pos.zoom
                        )
                    )
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** Ambil koordinat pin tengah SAAT tombol ditekan (snapshot) */
    val currentPin: () -> LocationData = {
        val target = cameraPositionState.position.target
        LocationData(latitude = target.latitude, longitude = target.longitude)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(
            hasPermission = state.hasPermission,
            cameraPositionState = cameraPositionState,
            pointA = state.pointA,
            pointB = state.pointB,
            isActiveA = state.isActiveA,
            isActiveB = state.isActiveB,
            onFocusA = { viewModel.onIntent(MapIntent.FocusPointA) },
            onFocusB = { viewModel.onIntent(MapIntent.FocusPointB) }
        )

        // ===== PANEL 1: A / lock / B (default bawah-tengah) =====
        DraggablePanel(
            anchor = PanelAnchor.BottomCenter,
            anchorPadding = 40.dp,
            isLocked = state.isTrackPanelLocked,
            onToggleLock = { viewModel.onIntent(MapIntent.ToggleTrackPanelLock) },
            savedOffset = state.trackPanelOffset,
            onOffsetChanged = { off ->
                viewModel.onIntent(MapIntent.TrackPanelOffsetChanged(PanelOffset(off.x, off.y)))
            },
            modifier = Modifier.zIndex(2f)
        ) {
            TrackPanelContent(
                isLocked = state.isTrackPanelLocked,
                onToggleLock = { viewModel.onIntent(MapIntent.ToggleTrackPanelLock) },
                isActiveA = state.isActiveA,
                isActiveB = state.isActiveB,
                onToggleA = { viewModel.onIntent(MapIntent.ToggleA(currentPin())) },
                onToggleB = { viewModel.onIntent(MapIntent.ToggleB(currentPin())) }
            )
        }

        // ===== PANEL 2: AutoFocus / lock / ZoomIn / ZoomOut (default kanan-tengah) =====
        DraggablePanel(
            anchor = PanelAnchor.CenterEnd,
            anchorPadding = 16.dp,
            isLocked = state.isZoomPanelLocked,
            onToggleLock = { viewModel.onIntent(MapIntent.ToggleZoomPanelLock) },
            savedOffset = state.zoomPanelOffset,
            onOffsetChanged = { off ->
                viewModel.onIntent(MapIntent.ZoomPanelOffsetChanged(PanelOffset(off.x, off.y)))
            },
            modifier = Modifier.zIndex(2f)
        ) {
            ZoomPanelContent(
                isLocked = state.isZoomPanelLocked,
                onToggleLock = { viewModel.onIntent(MapIntent.ToggleZoomPanelLock) },
                onFocus = { viewModel.onIntent(MapIntent.FocusCurrentLocation) },
                onZoomIn = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(MAX_ZOOM)) }
                },
                onZoomOut = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(STANDARD_ZOOM)) }
                }
            )
        }
    }
}

// ================== PETA ==================

@Composable
private fun MapContent(
    hasPermission: Boolean,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    pointA: LocationData?,
    pointB: LocationData?,
    isActiveA: Boolean,
    isActiveB: Boolean,
    onFocusA: () -> Unit,
    onFocusB: () -> Unit
) {
    /** Titik tengah peta = posisi pin */
    val center: LatLng = cameraPositionState.position.target

    /** Status tampil/sembunyi chip koordinat pin (tersimpan saat rotasi) */
    var isPinChipVisible by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                tiltGesturesEnabled = false,
                indoorLevelPickerEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // Marker A — muncul saat play, hilang saat stop
            pointA?.let { p ->
                MarkerComposable(
                    state = MarkerState(position = LatLng(p.latitude, p.longitude)),
                    title = "Titik A"
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Marker A",
                        tint = TrackAColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Marker B — muncul saat play, hilang saat stop
            pointB?.let { p ->
                MarkerComposable(
                    state = MarkerState(position = LatLng(p.latitude, p.longitude)),
                    title = "Titik B"
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Marker B",
                        tint = TrackBColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // ---- PIN TETAP DI TENGAH ----
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Pin tengah",
            tint = PinRed,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .offset(y = (-24).dp)
        )

        // ---- KOLOM CHIP (TENGAH ATAS): chip pin + chip A + chip B ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (isPinChipVisible) {
                // Chip koordinat pin — TAP untuk sembunyikan
                Surface(
                    onClick = { isPinChipVisible = false },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = String.format(
                                Locale.US, "%.6f, %.6f", center.latitude, center.longitude
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Chip tersembunyi — tersisa ikon mata tertutup, TAP untuk tampilkan lagi
                Surface(
                    onClick = { isPinChipVisible = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Tampilkan koordinat pin",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
            }

            // Chip titik A — tampil saat A aktif, TAP untuk terbang ke marker A
            if (isActiveA) {
                PointChip(
                    label = "A",
                    accent = TrackAColor,
                    location = pointA,
                    onClick = onFocusA
                )
            }

            // Chip titik B — tampil saat B aktif, TAP untuk terbang ke marker B
            if (isActiveB) {
                PointChip(
                    label = "B",
                    accent = TrackBColor,
                    location = pointB,
                    onClick = onFocusB
                )
            }
        }
    }
}

/**
 * Chip titik: indikator berkedip + badge huruf + koordinat titik yang ditandai pin.
 * Bisa di-tap untuk menerbangkan kamera ke marker terkait.
 */
@Composable
private fun PointChip(
    label: String,
    accent: Color,
    location: LocationData?,
    onClick: () -> Unit
) {
    val blinkTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = "Titik aktif",
                tint = accent.copy(alpha = blinkAlpha),
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = location?.let {
                    String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude)
                } ?: "-",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ================== PANEL BISA DIGESER (GENERIC) ==================
// Semua state panel (lock + posisi drag) di-hoist ke ViewModel dan dipersistenkan
// ke disk, sehingga bertahan terhadap force stop.

@Composable
private fun DraggablePanel(
    anchor: PanelAnchor,
    anchorPadding: Dp,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    savedOffset: PanelOffset?,
    onOffsetChanged: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var isOffsetApplied by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val padPx = with(density) { anchorPadding.toPx() }

        val currentScreenW by rememberUpdatedState(screenW)
        val currentScreenH by rememberUpdatedState(screenH)
        val currentPanelSize by rememberUpdatedState(panelSize)
        val currentPad by rememberUpdatedState(padPx)

        /** Posisi dasar (tanpa drag) sesuai anchor */
        fun basePos(pw: Float, ph: Float, sw: Float, sh: Float, pad: Float): Offset =
            when (anchor) {
                PanelAnchor.BottomCenter -> Offset((sw - pw) / 2f, sh - ph - pad)
                PanelAnchor.CenterEnd -> Offset(sw - pw - pad, (sh - ph) / 2f)
            }

        // Pulihkan posisi drag yang tersimpan (sekali, setelah panel terukur)
        LaunchedEffect(savedOffset, panelSize) {
            if (isOffsetApplied) return@LaunchedEffect
            if (panelSize == IntSize.Zero || savedOffset == null) return@LaunchedEffect
            val base = basePos(
                panelSize.width.toFloat(), panelSize.height.toFloat(), screenW, screenH, padPx
            )
            val maxX = (screenW - panelSize.width).coerceAtLeast(0f)
            val maxY = (screenH - panelSize.height).coerceAtLeast(0f)
            val absX = (base.x + savedOffset.x).coerceIn(0f, maxX)
            val absY = (base.y + savedOffset.y).coerceIn(0f, maxY)
            dragOffset = Offset(absX - base.x, absY - base.y)
            isOffsetApplied = true
        }

        // Saat ukuran layar/panel berubah (rotasi): cukup clamp posisi.
        LaunchedEffect(screenW, screenH, panelSize) {
            if (panelSize == IntSize.Zero) return@LaunchedEffect
            val base = basePos(
                panelSize.width.toFloat(), panelSize.height.toFloat(), screenW, screenH, padPx
            )
            val maxX = (screenW - panelSize.width).coerceAtLeast(0f)
            val maxY = (screenH - panelSize.height).coerceAtLeast(0f)
            val absX = (base.x + dragOffset.x).coerceIn(0f, maxX)
            val absY = (base.y + dragOffset.y).coerceIn(0f, maxY)
            dragOffset = Offset(absX - base.x, absY - base.y)
        }

        val alignment = when (anchor) {
            PanelAnchor.BottomCenter -> Alignment.BottomCenter
            PanelAnchor.CenterEnd -> Alignment.CenterEnd
        }
        val sidePadding = when (anchor) {
            PanelAnchor.BottomCenter -> Modifier.padding(bottom = anchorPadding)
            PanelAnchor.CenterEnd -> Modifier.padding(end = anchorPadding)
        }

        Box(
            modifier = Modifier
                .align(alignment)
                .then(sidePadding)
                .onSizeChanged { panelSize = it }
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .shakeIfUnlocked(!isLocked)
                .then(
                    if (!isLocked) Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                onOffsetChanged(dragOffset) // simpan posisi ke disk
                            },
                            onDragCancel = { isDragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val size = currentPanelSize
                            if (size == IntSize.Zero || size.width == 0) return@detectDragGestures
                            val base = basePos(
                                size.width.toFloat(), size.height.toFloat(),
                                currentScreenW, currentScreenH, currentPad
                            )
                            val maxX = (currentScreenW - size.width).coerceAtLeast(0f)
                            val maxY = (currentScreenH - size.height).coerceAtLeast(0f)
                            val absX = (base.x + dragOffset.x + dragAmount.x).coerceIn(0f, maxX)
                            val absY = (base.y + dragOffset.y + dragAmount.y).coerceIn(0f, maxY)
                            dragOffset = Offset(absX - base.x, absY - base.y)
                        }
                    } else Modifier
                )
        ) {
            content()
        }
    }
}

// ================== KONTEN PANEL ==================

/** Panel 1: A / lock / B (vertikal) */
@Composable
private fun TrackPanelContent(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    isActiveA: Boolean,
    isActiveB: Boolean,
    onToggleA: () -> Unit,
    onToggleB: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isLocked) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TrackButton("A", isActiveA, onToggleA)
            PanelDivider()
            LockButton(isLocked, onToggleLock)
            PanelDivider()
            TrackButton("B", isActiveB, onToggleB)
        }
    }
}

/** Panel 2: AutoFocus / lock / ZoomIn / ZoomOut (vertikal) */
@Composable
private fun ZoomPanelContent(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onFocus: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isLocked) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RoundIconButton(
                icon = Icons.Filled.CenterFocusStrong,
                description = "Auto fokus ke posisi saya",
                onClick = onFocus
            )
            PanelDivider()
            LockButton(isLocked, onToggleLock)
            PanelDivider()
            RoundIconButton(
                icon = Icons.Filled.ZoomIn,
                description = "Perbesar ke zoom maksimum",
                onClick = onZoomIn
            )
            PanelDivider()
            RoundIconButton(
                icon = Icons.Filled.ZoomOut,
                description = "Kembali ke zoom standar",
                onClick = onZoomOut
            )
        }
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        modifier = Modifier
            .width(36.dp)
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun TrackButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isActive) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = "Tombol $label",
                    tint = if (isActive) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LockButton(isLocked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (isLocked) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (isLocked) "Buka kunci panel" else "Kunci posisi panel",
                tint = if (isLocked) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Getaran halus saat panel tidak terkunci = tanda panel bisa digeser */
@Composable
private fun Modifier.shakeIfUnlocked(unlocked: Boolean): Modifier {
    val transition = rememberInfiniteTransition(label = "shake")
    val angle by transition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeAngle"
    )
    return if (unlocked) graphicsLayer { rotationZ = angle } else this
}

private fun hasLocationPermission(context: Context): Boolean =
    PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
