package com.aya.module.domain.model

/** Kondisi titik A/B + favorit + visibilitas chip pin yang dipersistenkan */
data class SavedPointsState(
    val isActiveA: Boolean = false,
    val pointA: LocationData? = null,
    val isActiveB: Boolean = false,
    val pointB: LocationData? = null,
    val favoritesA: List<FavoritePoint> = emptyList(),
    val favoritesB: List<FavoritePoint> = emptyList(),
    val isPinChipVisible: Boolean = true
)
