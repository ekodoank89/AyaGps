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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aya.module.AyaGpsApp
import com.aya.module.domain.model.FavoritePoint
import com.aya.module.domain.model.JitterConfig
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

/** State form tambah/edit favorit */
private data class FavoriteFormState(
    val editIndex: Int?, // null = tambah baru
    val initialName: String,
    val initialLat: Double?,
    val initialLng: Double?
)

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
    // dan lompat ke titik/marker (zoom saat ini dipertahankan)
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

    // Simpan posisi kamera/pin terakhir saat aplikasi ditinggalkan (ON_PAUSE)
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

    // State dialog (transien — tidak perlu persisten)
    var showFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteDialogPin by remember { mutableStateOf<LocationData?>(null) }
    var showJitterDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapContent(
            hasPermission = state.hasPermission,
            cameraPositionState = cameraPositionState,
            pointA = state.pointA,
            pointB = state.pointB,
            isActiveA = state.isActiveA,
            isActiveB = state.isActiveB,
            isJitterActiveA = state.isJitterActiveA,
            isJitterActiveB = state.isJitterActiveB,
            onFocusA = { viewModel.onIntent(MapIntent.FocusPointA) },
            onFocusB = { viewModel.onIntent(MapIntent.FocusPointB) }
        )

        // ===== PANEL 1: A / B / lock / Fav / Jitter (default bawah-tengah) =====
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
                onToggleA = { viewModel.onIntent(MapIntent.ToggleA(currentPin())) },
                isActiveB = state.isActiveB,
                onToggleB = { viewModel.onIntent(MapIntent.ToggleB(currentPin())) },
                isFavoriteActive = state.favoritesA.isNotEmpty() || state.favoritesB.isNotEmpty(),
                onFavorite = {
                    favoriteDialogPin = currentPin()
                    showFavoriteDialog = true
                },
                isJitterActive = state.isJitterActiveA || state.isJitterActiveB,
                onJitter = { showJitterDialog = true }
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

        // ===== DIALOG FAVORIT =====
        if (showFavoriteDialog) {
            FavoriteDialog(
                pin = favoriteDialogPin ?: currentPin(),
                favoritesA = state.favoritesA,
                favoritesB = state.favoritesB,
                onDismiss = { showFavoriteDialog = false },
                onPlay = { category, index ->
                    showFavoriteDialog = false
                    viewModel.onIntent(MapIntent.PlayFavorite(category, index))
                },
                onSave = { category, index, name, location ->
                    viewModel.onIntent(MapIntent.SaveFavorite(category, index, name, location))
                },
                onDelete = { category, index ->
                    viewModel.onIntent(MapIntent.DeleteFavorite(category, index))
                }
            )
        }

        // ===== DIALOG JITTER =====
        if (showJitterDialog) {
            JitterDialog(
                configA = state.jitterConfigA,
                configB = state.jitterConfigB,
                isPointActiveA = state.isActiveA,
                isPointActiveB = state.isActiveB,
                isJitterActiveA = state.isJitterActiveA,
                isJitterActiveB = state.isJitterActiveB,
                onUpdateConfig = { target, config ->
                    viewModel.onIntent(MapIntent.UpdateJitterConfig(target, config))
                },
                onToggleJitter = { target ->
                    viewModel.onIntent(MapIntent.ToggleJitter(target))
                },
                onDismiss = { showJitterDialog = false }
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
    isJitterActiveA: Boolean,
    isJitterActiveB: Boolean,
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
            // Marker A — ikut bergerak saat jitter aktif
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

            // Marker B — ikut bergerak saat jitter aktif
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

        // ---- CHIP KOORDINAT PIN (ATAS TENGAH) — TAP untuk hide/unhide ----
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            if (isPinChipVisible) {
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
                        CoordTwoLines(latitude = center.latitude, longitude = center.longitude)
                    }
                }
            } else {
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
        }

        // ---- CHIP TITIK A (ATAS KIRI) ----
        if (isActiveA) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                PointChip(
                    label = "A",
                    accent = TrackAColor,
                    location = pointA,
                    isJitterActive = isJitterActiveA,
                    onClick = onFocusA
                )
            }
        }

        // ---- CHIP TITIK B (ATAS KANAN) ----
        if (isActiveB) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                PointChip(
                    label = "B",
                    accent = TrackBColor,
                    location = pointB,
                    isJitterActive = isJitterActiveB,
                    onClick = onFocusB
                )
            }
        }
    }
}

/** Dua baris koordinat: baris 1 latitude, baris 2 longitude (monospace) */
@Composable
private fun CoordTwoLines(latitude: Double, longitude: Double) {
    Column {
        Text(
            text = String.format(Locale.US, "%.6f", latitude),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = String.format(Locale.US, "%.6f", longitude),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Chip titik A/B: badge + titik berkedip (+ label JITTER saat aktif) + koordinat 2 baris */
@Composable
private fun PointChip(
    label: String,
    accent: Color,
    location: LocationData?,
    isJitterActive: Boolean,
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = "Titik aktif",
                    tint = accent.copy(alpha = blinkAlpha),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.height(2.dp))
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
                if (isJitterActive) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "JITTER",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (location != null) {
                CoordTwoLines(latitude = location.latitude, longitude = location.longitude)
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ================== DIALOG FAVORIT ==================

/**
 * Dialog favorit: 2 tab (Favorite A / Favorite B), daftar favorit per kategori
 * dengan aksi play (tap nama), edit, dan hapus (konfirmasi). Tombol + Tambah
 * membuka form input (Dari Pin / Manual).
 */
@Composable
private fun FavoriteDialog(
    pin: LocationData,
    favoritesA: List<FavoritePoint>,
    favoritesB: List<FavoritePoint>,
    onDismiss: () -> Unit,
    onPlay: (PointCategory, Int) -> Unit,
    onSave: (PointCategory, Int?, String, LocationData) -> Unit,
    onDelete: (PointCategory, Int) -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 = A, 1 = B
    val category = if (tab == 0) PointCategory.A else PointCategory.B
    val favorites = if (tab == 0) favoritesA else favoritesB

    var formState by remember { mutableStateOf<FavoriteFormState?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    // Dialog konfirmasi hapus
    if (deleteIndex != null) {
        val idx = deleteIndex!!
        val target = favorites.getOrNull(idx)
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("Hapus favorit?") },
            text = { Text("Yakin ingin menghapus \"${target?.name ?: "-"}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category, idx)
                    deleteIndex = null
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteIndex = null }) { Text("Batal") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Favorite") },
        text = {
            if (formState == null) {
                // ===== MODE DAFTAR =====
                Column {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favorite A") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Favorite B") })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (favorites.isEmpty()) {
                        Text(
                            text = "Belum ada favorit ${category.name}. " +
                                    "Tekan \"Tambah\" untuk membuat.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            favorites.forEachIndexed { index, fav ->
                                FavoriteListItem(
                                    favorite = fav,
                                    onPlay = { onPlay(category, index) },
                                    onEdit = {
                                        formState = FavoriteFormState(
                                            editIndex = index,
                                            initialName = fav.name,
                                            initialLat = fav.location.latitude,
                                            initialLng = fav.location.longitude
                                        )
                                    },
                                    onDelete = { deleteIndex = index }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            formState = FavoriteFormState(null, "", null, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Tambah Favorite ${category.name}")
                    }
                }
            } else {
                // ===== MODE FORM (TAMBAH / EDIT) =====
                FavoriteForm(
                    category = category,
                    form = formState!!,
                    pin = pin,
                    onCancel = { formState = null },
                    onSubmit = { index, name, location ->
                        onSave(category, index, name, location)
                        formState = null
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (formState == null) {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}

/** Item daftar favorit: tap nama = play; tombol edit & hapus di kanan */
@Composable
private fun FavoriteListItem(
    favorite: FavoritePoint,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPlay)
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = favorite.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format(Locale.US, "%.6f", favorite.location.latitude),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(Locale.US, "%.6f", favorite.location.longitude),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit ${favorite.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Hapus ${favorite.name}",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Form tambah/edit favorit: tab Dari Pin (read-only) / Manual (input bebas) */
@Composable
private fun FavoriteForm(
    category: PointCategory,
    form: FavoriteFormState,
    pin: LocationData,
    onCancel: () -> Unit,
    onSubmit: (index: Int?, name: String, location: LocationData) -> Unit
) {
    // Tambah → default tab "Dari Pin"; Edit → default "Manual" (terisi nilai lama)
    var srcTab by remember { mutableStateOf(if (form.editIndex == null) 0 else 1) }
    var name by remember { mutableStateOf(form.initialName) }
    var latText by remember { mutableStateOf(form.initialLat?.toString() ?: "") }
    var lngText by remember { mutableStateOf(form.initialLng?.toString() ?: "") }

    val manualLat = latText.replace(',', '.').toDoubleOrNull()
    val manualLng = lngText.replace(',', '.').toDoubleOrNull()
    val manualValid = manualLat != null && manualLat in -90.0..90.0 &&
            manualLng != null && manualLng in -180.0..180.0
    val canSave = name.isNotBlank() && (srcTab == 0 || manualValid)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Favorite ${category.name}" +
                    if (form.editIndex != null) " — edit" else " — baru",
            style = MaterialTheme.typography.labelLarge
        )
        TabRow(selectedTabIndex = srcTab) {
            Tab(selected = srcTab == 0, onClick = { srcTab = 0 }, text = { Text("Dari Pin") })
            Tab(selected = srcTab == 1, onClick = { srcTab = 1 }, text = { Text("Manual") })
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama favorit") },
            singleLine = true,
            isError = name.isNotEmpty() && name.isBlank()
        )
        if (srcTab == 0) {
            OutlinedTextField(
                value = String.format(Locale.US, "%.6f", pin.latitude),
                onValueChange = {},
                readOnly = true,
                label = { Text("Latitude (dari pin)") },
                singleLine = true
            )
            OutlinedTextField(
                value = String.format(Locale.US, "%.6f", pin.longitude),
                onValueChange = {},
                readOnly = true,
                label = { Text("Longitude (dari pin)") },
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude (-90 s/d 90)") },
                singleLine = true,
                isError = latText.isNotEmpty() && manualLat == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = lngText,
                onValueChange = { lngText = it },
                label = { Text("Longitude (-180 s/d 180)") },
                singleLine = true,
                isError = lngText.isNotEmpty() && manualLng == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Batal") }
            TextButton(
                enabled = canSave,
                onClick = {
                    if (srcTab == 0) {
                        onSubmit(form.editIndex, name.trim(), pin)
                    } else {
                        onSubmit(
                            form.editIndex, name.trim(),
                            LocationData(manualLat!!, manualLng!!)
                        )
                    }
                }
            ) { Text("Simpan") }
        }
    }
}

// ================== DIALOG JITTER ==================

@Composable
private fun JitterDialog(
    configA: JitterConfig,
    configB: JitterConfig,
    isPointActiveA: Boolean,
    isPointActiveB: Boolean,
    isJitterActiveA: Boolean,
    isJitterActiveB: Boolean,
    onUpdateConfig: (PointCategory, JitterConfig) -> Unit,
    onToggleJitter: (PointCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 = A, 1 = B
    val target = if (tab == 0) PointCategory.A else PointCategory.B
    val config = if (tab == 0) configA else configB
    val pointActive = if (tab == 0) isPointActiveA else isPointActiveB
    val jitterActive = if (tab == 0) isJitterActiveA else isJitterActiveB

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pergerakan Titik (Jitter)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Titik A") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Titik B") })
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Jitter ${target.name} aktif", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = jitterActive,
                        enabled = pointActive,
                        onCheckedChange = { onToggleJitter(target) }
                    )
                }
                if (!pointActive) {
                    Text(
                        text = "Tekan Play ${target.name} terlebih dahulu untuk mengaktifkan jitter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Langkah per jendela: ${config.stepMeters.roundToInt()} m",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = config.stepMeters,
                    onValueChange = { onUpdateConfig(target, config.copy(stepMeters = it)) },
                    valueRange = 1f..20f
                )
                Text(
                    text = "Jendela (interval): ${config.intervalSeconds} detik",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = config.intervalSeconds.toFloat(),
                    onValueChange = {
                        onUpdateConfig(target, config.copy(intervalSeconds = it.roundToInt()))
                    },
                    valueRange = 1f..30f
                )
                Text(
                    text = "Radius maksimal: ${config.radiusMeters.roundToInt()} m",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = config.radiusMeters,
                    onValueChange = { onUpdateConfig(target, config.copy(radiusMeters = it)) },
                    valueRange = 2f..50f
                )
                TextButton(
                    onClick = { onUpdateConfig(target, JitterConfig.DEFAULT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset ke default (3 m / 5 dtk / R4 m)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Selesai") }
        }
    )
}

// ================== PANEL BISA DIGESER (GENERIC) ==================

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

/** Panel 1: A / B / lock / Fav / Jitter (vertikal) */
@Composable
private fun TrackPanelContent(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    isActiveA: Boolean,
    onToggleA: () -> Unit,
    isActiveB: Boolean,
    onToggleB: () -> Unit,
    isFavoriteActive: Boolean,
    onFavorite: () -> Unit,
    isJitterActive: Boolean,
    onJitter: () -> Unit
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
            TrackButton("B", isActiveB, onToggleB)
            PanelDivider()
            LockButton(isLocked, onToggleLock)
            PanelDivider()
            LabeledIconButton("Fav", Icons.Filled.Star, isFavoriteActive, onFavorite)
            PanelDivider()
            LabeledIconButton("Jitter", Icons.Filled.Shuffle, isJitterActive, onJitter)
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

/** Tombol panel dengan label: dipakai Favorite & Jitter */
@Composable
private fun LabeledIconButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
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
                    imageVector = icon,
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
