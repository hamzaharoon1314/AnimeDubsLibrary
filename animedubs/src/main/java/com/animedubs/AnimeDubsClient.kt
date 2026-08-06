package com.animedubs

import com.animedubs.models.DubStatusResult
import com.animedubs.models.SyncState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the public API for AnimeDubs.
 * Useful for Dependency Injection (e.g., Hilt, Dagger, Koin) and testing.
 */
interface AnimeDubsClient {
    /**
     * Exposes the current background sync state.
     */
    val syncState: StateFlow<SyncState>

    /**
     * Replaces the manual network token configuration.
     */
    fun setAnilistToken(token: String?)

    suspend fun warmUp()
    suspend fun forceRefresh()
    suspend fun clearCache()

    suspend fun getStatusByMalId(malId: Int): DubStatusResult
    fun observeStatusByMalId(malId: Int): Flow<DubStatusResult>
    
    suspend fun getStatusByAnilistId(anilistId: Int): DubStatusResult
    fun observeStatusByAnilistId(anilistId: Int): Flow<DubStatusResult>
    
    suspend fun getStatusesByAnilistIds(anilistIds: List<Int>): Map<Int, DubStatusResult>
    suspend fun getAllDubbedMalIds(): List<Int>
    
    fun getAttributionText(): String?
}
