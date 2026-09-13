package com.aya.module.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aya.module.AyaGpsApp
import com.aya.module.domain.model.LocationData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
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

    MapContent(
        hasPermission = state.hasPermission,
        location = state.location
    )
}

@Composable
private fun MapContent(hasPermission: Boolean, location: LocationData?) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 15f)
    }

    // Kamera: sekali zoom ke posisi, selanjutnya hanya center (zoom tetap dikendalikan user)
    var hasCentered by remember { mutableStateOf(false) }

    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val target = LatLng(loc.latitude, loc.longitude)
        if (!hasCentered) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f))
            hasCentered = true
        } else {
            cameraPositionState.move(CameraUpdateFactory.newLatLng(target))
        }
    }

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
}

private fun hasLocationPermission(context: Context): Boolean =
    PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
