package com.aya.module.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

private val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val DEFAULT_POSITION = LatLng(-6.2088, 106.8456) // Jakarta

/** Warna pin klasik Google Maps */
private val PinRed = Color(0xFFE53935)

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val viewModel: MapViewModel = viewModel()
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

    MapContent(hasPermission = state.hasPermission)
}

@Composable
private fun MapContent(hasPermission: Boolean) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 16f)
    }

    /** Titik tengah peta = posisi pin (berubah saat peta digeser) */
    val center: LatLng = cameraPositionState.position.target

    Box(modifier = Modifier.fillMaxSize()) {

        // ---- PETA (full-screen) ----
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
        )

        // ---- PIN TETAP DI TENGAH LAYAR ----
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Pin tengah",
            tint = PinRed,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                // Naikkan setengah tinggi ikon agar UJUNG pin tepat di titik tengah
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
                    // Locale.US agar pemisah desimal selalu titik (bukan koma)
                    text = String.format(
                        Locale.US,
                        "%.6f, %.6f",
                        center.latitude,
                        center.longitude
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
