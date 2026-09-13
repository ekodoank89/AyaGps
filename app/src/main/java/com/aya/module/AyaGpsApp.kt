package com.aya.module

import android.app.Application
import android.content.Context
import com.aya.module.data.location.LocationDataSource
import com.aya.module.data.repository.LocationRepositoryImpl
import com.aya.module.data.repository.MapStateRepositoryImpl
import com.aya.module.domain.repository.LocationRepository
import com.aya.module.domain.repository.MapStateRepository
import com.aya.module.domain.usecase.GetCurrentLocationUseCase
import com.aya.module.domain.usecase.GetLocationUpdatesUseCase

class AyaGpsApp : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dependency Injection manual (Clean Architecture) */
class AppContainer(appContext: Context) {

    private val locationRepository: LocationRepository =
        LocationRepositoryImpl(LocationDataSource(appContext))

    val mapStateRepository: MapStateRepository =
        MapStateRepositoryImpl(appContext)

    val getLocationUpdates = GetLocationUpdatesUseCase(locationRepository)
    val getCurrentLocation = GetCurrentLocationUseCase(locationRepository)
}
