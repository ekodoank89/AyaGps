package com.aya.module.domain.repository

import com.aya.module.domain.model.SavedPointsState

interface MapStateRepository {
    suspend fun save(state: SavedPointsState)
    suspend fun load(): SavedPointsState
}
