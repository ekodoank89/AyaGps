package com.aya.module.domain.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L
)
