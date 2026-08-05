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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "animedubs_cache")

internal class CacheManager(private val context: Context) {

    companion object {
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val ANILIST_TO_MAL_MAP = stringPreferencesKey("anilist_to_mal_map")
        
        const val TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val dubCacheFile = File(context.cacheDir, "dub_status_cache.json")
    
    @Volatile
    private var inMemoryDubCache: Map<Int, DubStatus>? = null

    @Volatile
    private var inMemoryAnilistMap: MutableMap<Int, Int?>? = null

    suspend fun isCacheValid(): Boolean = withContext(Dispatchers.IO) {
        val preferences = context.dataStore.data.first()
        val lastSync = preferences[LAST_SYNC_TIME] ?: 0L
        val currentTime = System.currentTimeMillis()
        (currentTime - lastSync) < TTL_MILLIS && dubCacheFile.exists()
    }

    suspend fun saveDubInfo(yes: List<Int>, partial: List<Int>, no: List<Int>) = withContext(Dispatchers.IO) {
        Logger.d("CacheManager: Saving new dub info to disk cache")
        val map = mutableMapOf<Int, DubStatus>()
        yes.forEach { map[it] = DubStatus.YES }
        partial.forEach { map[it] = DubStatus.PARTIAL }
        no.forEach { map[it] = DubStatus.NO }

        val serializedMap = json.encodeToString(map)
        dubCacheFile.writeText(serializedMap)
        inMemoryDubCache = map

        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun getDubStatus(malId: Int): DubStatus? = withContext(Dispatchers.IO) {
        if (inMemoryDubCache == null) {
            if (dubCacheFile.exists()) {
                try {
                    Logger.d("CacheManager: Loading dub info from disk to memory")
                    val serializedMap = dubCacheFile.readText()
                    inMemoryDubCache = json.decodeFromString<Map<Int, DubStatus>>(serializedMap)
                } catch (e: Exception) {
                    Logger.e("CacheManager: Failed to parse dub cache file", e)
                    return@withContext null
                }
            }
        }
        inMemoryDubCache?.get(malId)
    }

    suspend fun saveAnilistMappings(newMappings: Map<Int, Int?>) = withContext(Dispatchers.IO) {
        if (newMappings.isEmpty()) return@withContext
        
        Logger.d("CacheManager: Saving ${newMappings.size} AniList mappings to DataStore")
        
        // Ensure memory cache is initialized
        if (inMemoryAnilistMap == null) {
            loadAnilistMapFromDataStore()
        }
        
        // Update memory cache instantly
        inMemoryAnilistMap?.putAll(newMappings)
        
        // Write to DataStore in background
        context.dataStore.edit { prefs ->
            prefs[ANILIST_TO_MAL_MAP] = json.encodeToString(inMemoryAnilistMap ?: newMappings)
        }
    }

    private suspend fun loadAnilistMapFromDataStore(): MutableMap<Int, Int?> {
        val preferences = context.dataStore.data.first()
        val serializedMap = preferences[ANILIST_TO_MAL_MAP] ?: "{}"
        
        return try {
            json.decodeFromString<MutableMap<Int, Int?>>(serializedMap)
        } catch (e: Exception) {
            mutableMapOf()
        }.also {
            inMemoryAnilistMap = it
        }
    }

    suspend fun getMalIdsForAnilist(anilistIds: List<Int>): Map<Int, Int?> = withContext(Dispatchers.IO) {
        val map = inMemoryAnilistMap ?: loadAnilistMapFromDataStore()
        
        val result = mutableMapOf<Int, Int?>()
        anilistIds.forEach { id ->
            if (map.containsKey(id)) {
                result[id] = map[id]
            }
        }
        result
    }
}
