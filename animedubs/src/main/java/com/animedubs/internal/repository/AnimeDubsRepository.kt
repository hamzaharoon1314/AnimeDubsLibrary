package com.animedubs.internal.repository

import com.animedubs.AnimeDubsClient
import com.animedubs.internal.cache.CacheManager
import com.animedubs.internal.network.NetworkClient
import com.animedubs.internal.network.UnauthorizedException
import com.animedubs.internal.utils.Logger
import com.animedubs.models.DubStatus
import com.animedubs.models.DubStatusResult
import com.animedubs.models.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal class AnimeDubsRepository(
    private val cacheManager: CacheManager,
    private val networkClient: NetworkClient,
    private val dataSource: com.animedubs.models.DataSource
) : AnimeDubsClient {

    private val syncMutex = Mutex()
    private val graphQlSemaphore = Semaphore(5)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _syncEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    @Volatile
    private var isRetrying = false

    private suspend fun syncDubInfoIfNeeded() {
        if (!cacheManager.isCacheValid()) {
            syncMutex.withLock {
                if (!cacheManager.isCacheValid()) {
                    _syncState.value = SyncState.SYNCING
                    try {
                        val payload = networkClient.fetchDubInfo()
                        cacheManager.saveDubInfo(
                            yes = payload.yes,
                            partial = payload.partial,
                            no = payload.no
                        )
                        _syncState.value = SyncState.SUCCESS
                        _syncEvent.tryEmit(Unit)
                    } catch (e: Exception) {
                        _syncState.value = SyncState.ERROR
                        Logger.e("AnimeDubsRepository: Background sync failed", e)
                        triggerRetry()
                    }
                }
            }
        }
    }

    private fun triggerRetry() {
        if (isRetrying) return
        isRetrying = true
        repoScope.launch {
            var delayTime = 5000L
            while (!cacheManager.isCacheValid()) {
                delay(delayTime)
                Logger.d("AnimeDubsRepository: Retrying background sync (Delay: ${delayTime}ms)")
                syncMutex.withLock {
                    if (cacheManager.isCacheValid()) {
                        isRetrying = false
                        return@launch
                    }
                    try {
                        val payload = networkClient.fetchDubInfo()
                        cacheManager.saveDubInfo(
                            yes = payload.yes,
                            partial = payload.partial,
                            no = payload.no
                        )
                        _syncState.value = SyncState.SUCCESS
                        _syncEvent.tryEmit(Unit)
                        isRetrying = false
                        return@launch
                    } catch (e: Exception) {
                        delayTime = (delayTime * 2).coerceAtMost(60_000L)
                    }
                }
            }
            isRetrying = false
        }
    }

    override suspend fun getStatusByMalId(malId: Int): DubStatusResult = withContext(Dispatchers.IO) {
        syncDubInfoIfNeeded()
        val status = cacheManager.getDubStatus(malId)
        DubStatusResult(malId = malId, anilistId = null, status = status)
    }

    override fun observeStatusByMalId(malId: Int): Flow<DubStatusResult> = flow {
        emit(getStatusByMalId(malId))
        _syncEvent.collect {
            emit(getStatusByMalId(malId))
        }
    }.distinctUntilChanged()

    override suspend fun getStatusByAnilistId(anilistId: Int): DubStatusResult = withContext(Dispatchers.IO) {
        val results = getStatusesByAnilistIds(listOf(anilistId))
        results[anilistId] ?: DubStatusResult(malId = -1, anilistId = anilistId, status = DubStatus.UNKNOWN)
    }

    override fun observeStatusByAnilistId(anilistId: Int): Flow<DubStatusResult> = flow {
        emit(getStatusByAnilistId(anilistId))
        _syncEvent.collect {
            emit(getStatusByAnilistId(anilistId))
        }
    }.distinctUntilChanged()

    override suspend fun getStatusesByAnilistIds(anilistIds: List<Int>): Map<Int, DubStatusResult> = withContext(Dispatchers.IO) {
        if (anilistIds.isEmpty()) return@withContext emptyMap()
        
        syncDubInfoIfNeeded()
        
        // 1. Get cached mappings
        val cachedMappings = cacheManager.getMalIdsForAnilist(anilistIds).toMutableMap()
        val missingIds = anilistIds.filter { !cachedMappings.containsKey(it) }

        // 2. Fetch missing IDs in chunks of 50 concurrently
        if (missingIds.isNotEmpty()) {
            Logger.d("AnimeDubsRepository: Missing AniList mappings for ${missingIds.size} items. Fetching...")
            val chunks = missingIds.chunked(50)
            
            coroutineScope {
                val results = chunks.map { chunk ->
                    async {
                        try {
                            graphQlSemaphore.withPermit {
                                networkClient.fetchMalIdsFromAnilist(chunk)
                            }
                        } catch (e: UnauthorizedException) {
                            Logger.e("AnimeDubsRepository: Token unauthorized", e)
                            _syncState.value = SyncState.UNAUTHORIZED
                            emptyMap<Int, Int?>()
                        } catch (e: Exception) {
                            Logger.e("AnimeDubsRepository: Failed to fetch AniList chunk", e)
                            emptyMap<Int, Int?>()
                        }
                    }
                }.awaitAll()

                results.forEach { fetchedMappings ->
                    if (fetchedMappings.isNotEmpty()) {
                        cacheManager.saveAnilistMappings(fetchedMappings)
                        cachedMappings.putAll(fetchedMappings)
                    }
                }
            }
        }

        // 3. Resolve all to DubStatusResult
        val finalResult = mutableMapOf<Int, DubStatusResult>()
        anilistIds.forEach { anilistId ->
            val malId = cachedMappings[anilistId]
            if (malId != null) {
                val status = cacheManager.getDubStatus(malId)
                finalResult[anilistId] = DubStatusResult(malId = malId, anilistId = anilistId, status = status)
            } else {
                finalResult[anilistId] = DubStatusResult(malId = -1, anilistId = anilistId, status = DubStatus.UNKNOWN)
            }
        }

        finalResult
    }

    override fun setAnilistToken(token: String?) {
        networkClient.anilistToken = token
    }

    override suspend fun warmUp() {
        syncDubInfoIfNeeded()
    }

    override suspend fun forceRefresh() {
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                _syncState.value = SyncState.SYNCING
                try {
                    val payload = networkClient.fetchDubInfo()
                    cacheManager.saveDubInfo(
                        yes = payload.yes,
                        partial = payload.partial,
                        no = payload.no
                    )
                    _syncState.value = SyncState.SUCCESS
                    _syncEvent.tryEmit(Unit)
                } catch (e: Exception) {
                    _syncState.value = SyncState.ERROR
                    Logger.e("AnimeDubsRepository: Force refresh failed", e)
                }
            }
        }
    }

    override suspend fun clearCache() {
        cacheManager.clearCache()
        _syncState.value = SyncState.IDLE
    }

    override fun getAttributionText(): String? {
        return when (dataSource) {
            is com.animedubs.models.DataSource.MyDubList -> "Dub data © MyDubList - https://mydublist.com - (CC BY 4.0)"
            is com.animedubs.models.DataSource.MalDubs -> "Dub data © MAL-Dubs - https://github.com/MAL-Dubs/MAL-Dubs - (AGPL-3.0)"
        }
    }

    override suspend fun getAllDubbedMalIds(): List<Int> = withContext(Dispatchers.IO) {
        syncDubInfoIfNeeded()
        cacheManager.getAllDubbed()
    }
}
