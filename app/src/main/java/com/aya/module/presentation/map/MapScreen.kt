package com.aya.module.presentation.map
import android.Manifest
import android.content.
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aya.module.AyaGpsApp
import com.aya.module.domain.model.LocationData
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.math.roundToInt

private val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val DEFAULT_POSITION = LatLng(-6.2088, 106.8456) // Jakarta

private val PinRed = Color(0xFFE53935)
private val TrackAColor = Color(0xFF1E88E5) // biru
private val TrackBColor = Color(0xFFFB8C00) // oranye

private val PanelBottomPadding = 40.dp

/** Saver agar posisi panel tidak reset saat layar dirotasi */
private val OffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AyaGpsApp
    val viewModel: MapViewModel = viewModel {
        MapViewModel(getLocationUpdates = app.container.getLocationUpdates)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

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

    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(
            hasPermission = state.hasPermission,
            trackA = state.trackA,
            trackB = state.trackB
        )

        DraggableControlPanel(
            isTrackingA = state.isTrackingA,
            isTrackingB = state.isTrackingB,
            onToggleA = { viewModel.onIntent(MapIntent.ToggleTrackingA) },
            onToggleB = { viewModel.onIntent(MapIntent.ToggleTrackingB) },
            modifier = Modifier.zIndex(2f)
        )
    }
}

// ================== PETA ==================

@Composable
private fun MapContent(
    hasPermission: Boolean,
    trackA: List<LocationData>,
    trackB: List<LocationData>
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 16f)
    }

    /** Titik tengah peta = posisi pin */
    val center: LatLng = cameraPositionState.position.target

    val pointsA = remember(trackA) { trackA.map { LatLng(it.latitude, it.longitude) } }
    val pointsB = remember(trackB) { trackB.map { LatLng(it.latitude, it.longitude) } }

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
            if (pointsA.size >= 2) Polyline(points = pointsA, color = TrackAColor, width = 6f)
            if (pointsB.size >= 2) Polyline(points = pointsB, color = TrackBColor, width = 6f)
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

        // ---- CHIP KOORDINAT (kiri atas) ----
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
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
    }
}

// ================== PANEL KONTROL BISA DIGESER ==================
// Panel SELALU dirender sejak frame pertama (di-anchor bawah-tengah).
// Tidak ada logika kondisional yang bisa menyembunyikannya.

@Composable
private fun DraggableControlPanel(
    isTrackingA: Boolean,
    isTrackingB: Boolean,
    onToggleA: () -> Unit,
    onToggleB: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val bottomPadPx = with(density) { PanelBottomPadding.toPx() }

        // Nilai terbaru agar akurat di dalam gesture lambda
        val currentScreenW by rememberUpdatedState(screenW)
        val currentScreenH by rememberUpdatedState(screenH)
        val currentPanelSize by rememberUpdatedState(panelSize)
        val currentBottomPad by rememberUpdatedState(bottomPadPx)

        // Saat ukuran layar/panel berubah (rotasi): cukup clamp posisi.
        // TIDAK mengatur visibilitas — panel tidak pernah hilang.
        LaunchedEffect(screenW, screenH, panelSize) {
            if (panelSize == IntSize.Zero) return@LaunchedEffect
            val baseX = (screenW - panelSize.width) / 2f
            val baseY = screenH - panelSize.height - bottomPadPx
            val maxX = (screenW - panelSize.width).coerceAtLeast(0f)
            val maxY = (screenH - panelSize.height).coerceAtLeast(0f)
            val absX = (baseX + dragOffset.x).coerceIn(0f, maxX)
            val absY = (baseY + dragOffset.y).coerceIn(0f, maxY)
            dragOffset = Offset(absX - baseX, absY - baseY)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)          // <-- dijamin tampil: bawah-tengah
                .padding(bottom = PanelBottomPadding)
                .onSizeChanged { panelSize = it }        // ukuran hanya untuk clamp
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .shakeIfUnlocked(!isLocked)
                .then(
                    if (!isLocked) Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val size = currentPanelSize
                            if (size == IntSize.Zero || size.width == 0) return@detectDragGestures
                            val baseX = (currentScreenW - size.width) / 2f
                            val baseY = currentScreenH - size.height - currentBottomPad
                            val maxX = (currentScreenW - size.width).coerceAtLeast(0f)
                            val maxY = (currentScreenH - size.height).coerceAtLeast(0f)
                            val absX = (baseX + dragOffset.x + dragAmount.x).coerceIn(0f, maxX)
                            val absY = (baseY + dragOffset.y + dragAmount.y).coerceIn(0f, maxY)
                            dragOffset = Offset(absX - baseX, absY - baseY)
                        }
                    } else Modifier
                )
        ) {
            PanelContent(
                isLocked = isLocked,
                isDragging = isDragging,
                isTrackingA = isTrackingA,
                isTrackingB = isTrackingB,
                onToggleLock = { isLocked = !isLocked },
                onToggleA = onToggleA,
                onToggleB = onToggleB
            )
        }
    }
}

@Composable
private fun PanelContent(
    isLocked: Boolean,
    isDragging: Boolean,
    isTrackingA: Boolean,
    isTrackingB: Boolean,
    onToggleLock: () -> Unit,
    onToggleA: () -> Unit,
    onToggleB: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = if (isDragging) 10.dp else 5.dp,
        border = if (isLocked) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackButton("A", isTrackingA, onToggleA)
            VerticalDivider(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LockButton(isLocked, onToggleLock)
            VerticalDivider(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            TrackButton("B", isTrackingB, onToggleB)
        }
    }
}

@Composable
private fun TrackButton(label: String, isTracking: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isTracking) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = "Tombol $label",
                    tint = if (isTracking) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isTracking) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** Getaran halus saat panel tidak terkunci = tanda panel bisa digeser (juga penanda build baru) */
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
