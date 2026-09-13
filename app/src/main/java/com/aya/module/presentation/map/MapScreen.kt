package com.aya.module.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aya.module.AyaGpsApp
import com.aya.module.domain.model.LocationData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val DEFAULT_POSITION = LatLng(-6.2088, 106.8456) // Jakarta

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AyaGpsApp
    val viewModel: MapViewModel = viewModel {
        MapViewModel(
            getLocationUpdates = app.container.getLocationUpdates,
            getCurrentLocation = app.container.getCurrentLocation
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) viewModel.onIntent(MapIntent.PermissionGranted)
        else viewModel.onIntent(MapIntent.PermissionDenied)
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) viewModel.onIntent(MapIntent.PermissionGranted)
        else permissionLauncher.launch(PERMISSIONS)
    }

    Scaffold(
        topBar = { AyaTopBar() },
        floatingActionButton = {
            if (state.hasPermission) {
                TrackingFab(
                    isTracking = state.isTracking,
                    onClick = {
                        viewModel.onIntent(
                            if (state.isTracking) MapIntent.StopTracking
                            else MapIntent.StartTracking
                        )
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.hasPermission) {
                MapContent(state = state)
            } else {
                PermissionRationale(onRequest = { permissionLauncher.launch(PERMISSIONS) })
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            state.location?.let { loc ->
                LocationInfoCard(
                    location = loc,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MapContent(state: MapUiState) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)
    }

    // Kamera mengikuti lokasi saat mode tracking aktif
    LaunchedEffect(state.location, state.isTracking) {
        val loc = state.location ?: return@LaunchedEffect
        if (state.isTracking) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true
        )
    ) {
        state.location?.let { loc ->
            Marker(
                state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                title = "Posisi Saat Ini",
                snippet = "%.6f, %.6f".format(loc.latitude, loc.longitude)
            )
        }
    }
}

@Composable
private fun LocationInfoCard(location: LocationData, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Latitude", "%.6f".format(location.latitude))
            InfoRow("Longitude", "%.6f".format(location.longitude))
            InfoRow("Kecepatan", "%.1f km/j".format(location.speed * 3.6f))
            InfoRow("Akurasi", "± %.0f m".format(location.accuracy))
            InfoRow("Altitude", "%.0f m".format(location.altitude))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrackingFab(isTracking: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null
            )
        },
        text = { Text(if (isTracking) "Hentikan" else "Mulai Lacak") },
        containerColor = if (isTracking) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer
    )
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Izin Lokasi Diperlukan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "AyaGps membutuhkan izin lokasi untuk menampilkan posisi Anda di peta secara real-time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Berikan Izin") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyaTopBar() {
    CenterAlignedTopAppBar(
        title = { Text("AyaGps") },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
