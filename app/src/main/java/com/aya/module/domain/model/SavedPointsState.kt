package com.aya.module.domain.model

/** Kondisi titik A/B + daftar favorit yang dipersistenkan ke disk */
data class SavedPointsState(
    val isActiveA: Boolean = false,
    val pointA: LocationData? = null,
    val isActiveB: Boolean = false,
    val pointB: LocationData? = null,
    val favoritesA: List<FavoritePoint> = emptyList(),
    val favoritesB: List<FavoritePoint> = emptyList()
)
