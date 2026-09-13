package com.aya.module.domain.repository

import com.aya.module.domain.model.LocationData
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun locationUpdates(intervalMillis: Long): Flow<LocationData>
    suspend fun lastKnownLocation(): LocationData?
}
