package com.aya.module.data.repository

import com.aya.module.data.location.LocationDataSource
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class LocationRepositoryImpl(
    private val dataSource: LocationDataSource
) : LocationRepository {

    override fun locationUpdates(intervalMillis: Long): Flow<LocationData> =
        dataSource.locationUpdates(intervalMillis)

    override suspend fun lastKnownLocation(): LocationData? =
        dataSource.lastKnownLocation()
}
