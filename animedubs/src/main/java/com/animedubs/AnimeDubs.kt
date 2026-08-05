package com.animedubs

import android.annotation.SuppressLint
import android.content.Context
import com.animedubs.internal.cache.CacheManager
import com.animedubs.internal.network.NetworkClient
import com.animedubs.internal.repository.AnimeDubsRepository
import com.animedubs.internal.utils.Logger
import com.animedubs.models.DubStatusResult
import com.animedubs.models.SyncState
import kotlinx.coroutines.flow.StateFlow

@SuppressLint("StaticFieldLeak")
object AnimeDubs : AnimeDubsClient {
    
    @Volatile
    private var repository: AnimeDubsRepository? = null

    /**
     * Enable this to see debug logs for cache hits, network requests, and errors.
     */
    var isDebugLoggingEnabled: Boolean
        get() = Logger.isEnabled
        set(value) { Logger.isEnabled = value }

    /**
     * Initializes the AnimeDubs library. Must be called once, preferably in Application.onCreate().
     */
    fun init(context: Context) {
        if (repository == null) {
            synchronized(this) {
                if (repository == null) {
                    val appContext = context.applicationContext
                    val cacheManager = CacheManager(appContext)
                    val networkClient = NetworkClient()
                    repository = AnimeDubsRepository(cacheManager, networkClient)
                }
            }
        }
    }

    private fun getRepo(): AnimeDubsClient {
        return repository ?: throw IllegalStateException("AnimeDubs has not been initialized. Call AnimeDubs.init(context) first.")
    }

    /**
     * Checks the dub status for a given MyAnimeList (MAL) ID.
     */
    override suspend fun getStatusByMalId(malId: Int): DubStatusResult {
        return getRepo().getStatusByMalId(malId)
    }

    /**
     * Checks the dub status for a given AniList ID.
     * This will internally resolve the AniList ID to a MAL ID.
     */
    override suspend fun getStatusByAnilistId(anilistId: Int): DubStatusResult {
        return getRepo().getStatusByAnilistId(anilistId)
    }

    /**
     * Fetches dub statuses for a list of AniList IDs in bulk.
     * This is highly optimized and will query the AniList GraphQL API in chunks of 50 for any missing IDs.
     */
    override suspend fun getStatusesByAnilistIds(anilistIds: List<Int>): Map<Int, DubStatusResult> {
        return getRepo().getStatusesByAnilistIds(anilistIds)
    }

    /**
     * Sets an optional OAuth token for AniList to bypass the strict anonymous rate limit (30 req/min).
     * With a token, the limit is 90 req/min.
     * @param token The user's AniList OAuth access token. Pass null to clear.
     */
    override fun setAnilistToken(token: String?) {
        getRepo().setAnilistToken(token)
    }

    /**
     * Exposes the current background sync state (IDLE, SYNCING, SUCCESS, ERROR).
     */
    override val syncState: StateFlow<SyncState>
        get() = getRepo().syncState

    /**
     * Triggers an immediate background sync if the cache is expired.
     * Call this right after init() to pre-fetch the data.
     */
    override suspend fun warmUp() {
        getRepo().warmUp()
    }

    /**
     * Forces an immediate network sync, completely bypassing the 24-hour TTL cache limit.
     */
    override suspend fun forceRefresh() {
        getRepo().forceRefresh()
    }

    /**
     * Completely wipes all dub data and AniList mappings from memory, DataStore, and local disk.
     */
    override suspend fun clearCache() {
        getRepo().clearCache()
    }
}
