package com.aya.module.domain.model

/** Posisi kamera/pin terakhir yang dipersistenkan ke disk */
data class SavedCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float
)
