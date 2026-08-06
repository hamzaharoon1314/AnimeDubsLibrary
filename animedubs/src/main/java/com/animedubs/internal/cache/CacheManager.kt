package com.animedubs.internal.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.animedubs.models.DubStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.animedubs.internal.utils.Logger

import com.animedubs.models.DataSource

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "animedubs_cache")

internal class CacheManager(
    private val context: Context,
    private val dataSource: DataSource,
    private val cacheTTLMillis: Long
) {

    private val json = Json { ignoreUnknownKeys = true }
    
    private val sourceId = when (dataSource) {
        is DataSource.MalDubs -> "maldubs"
        is DataSource.MyDubList -> "mydublist_${dataSource.confidence.value}_${dataSource.language.value}"
    }
    
    private val lastSyncTimeKey = longPreferencesKey("last_sync_time_$sourceId")
    private val dubCacheFile = File(context.cacheDir, "dub_status_cache_$sourceId.json")
    private val anilistMapFile = File(context.cacheDir, "anilist_to_mal_map.json")
    
    @Volatile
    private var yesIds = IntArray(0)
    
    @Volatile
    private var partialIds = IntArray(0)
    
    @Volatile
    private var noIds = IntArray(0)
    
    @Volatile
    private var cacheLoaded = false

    @Volatile
    private var inMemoryAnilistMap: MutableMap<Int, Int?>? = null

    suspend fun isCacheValid(): Boolean = withContext(Dispatchers.IO) {
        val preferences = context.dataStore.data.first()
        val lastSync = preferences[lastSyncTimeKey] ?: 0L
        val currentTime = System.currentTimeMillis()
        (currentTime - lastSync) < cacheTTLMillis && dubCacheFile.exists()
    }

    suspend fun saveDubInfo(yes: List<Int>, partial: List<Int>, no: List<Int>) = withContext(Dispatchers.IO) {
        Logger.d("CacheManager: Saving new dub info to disk cache")
        val payload = com.animedubs.models.DubInfoPayload(yes, partial, no)
        val serialized = json.encodeToString(payload)
        dubCacheFile.writeText(serialized)
        
        yesIds = yes.sorted().toIntArray()
        partialIds = partial.sorted().toIntArray()
        noIds = no.sorted().toIntArray()
        cacheLoaded = true

        context.dataStore.edit { preferences ->
            preferences[lastSyncTimeKey] = System.currentTimeMillis()
        }
    }

    private suspend fun ensureDubCacheLoaded() {
        if (!cacheLoaded) {
            if (dubCacheFile.exists()) {
                try {
                    Logger.d("CacheManager: Loading dub info from disk to memory")
                    val serialized = dubCacheFile.readText()
                    val payload = json.decodeFromString<com.animedubs.models.DubInfoPayload>(serialized)
                    yesIds = payload.yes.sorted().toIntArray()
                    partialIds = payload.partial.sorted().toIntArray()
                    noIds = payload.no.sorted().toIntArray()
                    cacheLoaded = true
                } catch (e: Exception) {
                    Logger.e("CacheManager: Failed to parse dub cache file (might be old format)", e)
                }
            }
        }
    }

    suspend fun getDubStatus(malId: Int): DubStatus = withContext(Dispatchers.IO) {
        ensureDubCacheLoaded()
        if (!cacheLoaded) return@withContext DubStatus.UNKNOWN
        
        if (yesIds.binarySearch(malId) >= 0) return@withContext DubStatus.YES
        if (partialIds.binarySearch(malId) >= 0) return@withContext DubStatus.PARTIAL
        if (noIds.binarySearch(malId) >= 0) return@withContext DubStatus.NO
        
        if (dataSource is DataSource.MyDubList) DubStatus.NO else DubStatus.UNKNOWN
    }

    suspend fun getAllDubbed(): List<Int> = withContext(Dispatchers.IO) {
        ensureDubCacheLoaded()
        if (!cacheLoaded) return@withContext emptyList()
        (yesIds + partialIds).toList()
    }

    suspend fun saveAnilistMappings(newMappings: Map<Int, Int?>) = withContext(Dispatchers.IO) {
        if (newMappings.isEmpty()) return@withContext
        
        Logger.d("CacheManager: Saving ${newMappings.size} AniList mappings to File")
        
        // Ensure memory cache is initialized
        if (inMemoryAnilistMap == null) {
            loadAnilistMapFromFile()
        }
        
        // Update memory cache instantly
        inMemoryAnilistMap?.putAll(newMappings)
        val mapToSave = inMemoryAnilistMap ?: newMappings
        
        // Write to File in background
        try {
            anilistMapFile.writeText(json.encodeToString(mapToSave))
        } catch (e: Exception) {
            Logger.e("CacheManager: Failed to write AniList mappings to File", e)
        }
    }

    private suspend fun loadAnilistMapFromFile(): MutableMap<Int, Int?> {
        val serializedMap = if (anilistMapFile.exists()) {
            try {
                anilistMapFile.readText()
            } catch (e: Exception) {
                "{}"
            }
        } else {
            "{}"
        }
        
        return try {
            json.decodeFromString<MutableMap<Int, Int?>>(serializedMap)
        } catch (e: Exception) {
            mutableMapOf()
        }.also {
            inMemoryAnilistMap = it
        }
    }

    suspend fun getMalIdsForAnilist(anilistIds: List<Int>): Map<Int, Int?> = withContext(Dispatchers.IO) {
        val map = inMemoryAnilistMap ?: loadAnilistMapFromFile()
        
        val result = mutableMapOf<Int, Int?>()
        anilistIds.forEach { id ->
            if (map.containsKey(id)) {
                result[id] = map[id]
            }
        }
        result
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Logger.d("CacheManager: Clearing all dub and AniList caches")
        yesIds = IntArray(0)
        partialIds = IntArray(0)
        noIds = IntArray(0)
        cacheLoaded = false
        inMemoryAnilistMap = null
        
        if (dubCacheFile.exists()) {
            dubCacheFile.delete()
        }
        
        if (anilistMapFile.exists()) {
            anilistMapFile.delete()
        }
        
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
