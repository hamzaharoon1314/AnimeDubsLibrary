package com.animedubs

import com.animedubs.models.DubStatusResult
import com.animedubs.models.SyncState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the public API for AnimeDubs.
 * Useful for Dependency Injection (e.g., Hilt, Dagger, Koin) and testing.
 */
interface AnimeDubsClient {
    val syncState: StateFlow<SyncState>
    
    fun setAnilistToken(token: String?)
    suspend fun warmUp()
    suspend fun forceRefresh()
    suspend fun clearCache()
    
    suspend fun getStatusByMalId(malId: Int): DubStatusResult
    suspend fun getStatusByAnilistId(anilistId: Int): DubStatusResult
    suspend fun getStatusesByAnilistIds(anilistIds: List<Int>): Map<Int, DubStatusResult>
}
