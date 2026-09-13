package com.aya.module.domain.repository

import com.aya.module.domain.model.SavedCameraState
import com.aya.module.domain.model.SavedJitterState
import com.aya.module.domain.model.SavedPanelLocks
import com.aya.module.domain.model.SavedPanelOffsets
import com.aya.module.domain.model.SavedPointsState

interface MapStateRepository {
    suspend fun save(state: SavedPointsState)
    suspend fun load(): SavedPointsState
    suspend fun savePanelLocks(state: SavedPanelLocks)
    suspend fun loadPanelLocks(): SavedPanelLocks
    suspend fun savePanelOffsets(state: SavedPanelOffsets)
    suspend fun loadPanelOffsets(): SavedPanelOffsets
    suspend fun saveCameraState(state: SavedCameraState)
    suspend fun loadCameraState(): SavedCameraState?
    suspend fun saveJitter(state: SavedJitterState)
    suspend fun loadJitter(): SavedJitterState
}
