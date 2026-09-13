package com.aya.module.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.aya.module.domain.model.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationDataSource(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // izin divalidasi di UI layer
    fun locationUpdates(intervalMillis: Long): Flow<LocationData> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        ).setMinUpdateIntervalMillis(intervalMillis / 2).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toLocationData()) }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): LocationData? =
        client.lastLocation.await()?.toLocationData()
}

private fun android.location.Location.toLocationData() = LocationData(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    bearing = bearing,
    accuracy = accuracy,
    timestamp = time
)
