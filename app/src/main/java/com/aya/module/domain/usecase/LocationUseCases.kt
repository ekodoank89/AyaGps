package com.aya.module.domain.usecase

import com.aya.module.domain.model.LocationData
import com.aya.module.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

/** Aliran update lokasi real-time */
class GetLocationUpdatesUseCase(
    private val repository: LocationRepository
) {
    operator fun invoke(intervalMillis: Long = 3_000L): Flow<LocationData> =
        repository.locationUpdates(intervalMillis)
}

/** Posisi terakhir yang diketahui */
class GetCurrentLocationUseCase(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(): LocationData? = repository.lastKnownLocation()
}
