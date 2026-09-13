package com.aya.module.domain.repository

import com.aya.module.domain.model.SavedPanelLocks
import com.aya.module.domain.model.SavedPointsState

interface MapStateRepository {
    suspend fun save(state: SavedPointsState)
    suspend fun load(): SavedPointsState
    suspend fun savePanelLocks(state: SavedPanelLocks)
    suspend fun loadPanelLocks(): SavedPanelLocks
}
