package com.aya.module.domain.model

/** Offset posisi panel relatif terhadap jangkar awalnya */
data class PanelOffset(val x: Float, val y: Float)

/** Posisi hasil drag kedua panel yang dipersistenkan ke disk */
data class SavedPanelOffsets(
    val track: PanelOffset? = null,
    val zoom: PanelOffset? = null
)
