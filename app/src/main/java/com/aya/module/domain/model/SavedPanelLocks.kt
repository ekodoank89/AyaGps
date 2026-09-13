package com.aya.module.domain.model

/** Kondisi lock kedua panel kontrol yang dipersistenkan ke disk */
data class SavedPanelLocks(
    val trackLocked: Boolean = false,
    val zoomLocked: Boolean = false
)
