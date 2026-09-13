package com.aya.module.domain.model

/** Kondisi titik A/B + favorit yang dipersistenkan ke disk */
data class SavedPointsState(
    val isActiveA: Boolean = false,
    val pointA: LocationData? = null,
    val isActiveB: Boolean = false,
    val pointB: LocationData? = null,
    val favorite: FavoritePoint? = null
)
