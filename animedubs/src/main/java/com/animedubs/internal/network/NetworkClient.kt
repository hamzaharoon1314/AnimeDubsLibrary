package com.animedubs.internal.network

import com.animedubs.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.animedubs.internal.utils.Logger

internal class NetworkClient(private val dataSource: DataSource) {
    private val client = OkHttpClient()
    
    @Volatile
    var anilistToken: String? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchDubInfo(): DubInfoPayload = withContext(Dispatchers.IO) {
        Logger.d("NetworkClient: Fetching dub info for source: $dataSource")
        val url = when (dataSource) {
            is DataSource.MalDubs -> "https://raw.githubusercontent.com/MAL-Dubs/MAL-Dubs/main/data/dubInfo.json"
            is DataSource.MyDubList -> {
                "https://raw.githubusercontent.com/Joelis57/MyDubList/main/dubs/confidence/${dataSource.confidence.value}/dubbed_${dataSource.language.value}.json"
            }
        }
        
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch dub info: ${response.code}")
            
            val body = response.body?.string() ?: throw IOException("Empty response body")
            Logger.d("NetworkClient: Successfully fetched dub info")
            
            when (dataSource) {
                is DataSource.MalDubs -> json.decodeFromString<DubInfoPayload>(body)
                is DataSource.MyDubList -> {
                    val payload = json.decodeFromString<MyDubListPayload>(body)
                    DubInfoPayload(
                        yes = payload.dubbed,
                        partial = payload.partial,
                        no = emptyList()
                    )
                }
            }
        }
    }

    suspend fun fetchMalIdsFromAnilist(anilistIds: List<Int>): Map<Int, Int?> = withContext(Dispatchers.IO) {
        if (anilistIds.isEmpty()) return@withContext emptyMap()
        
        Logger.d("NetworkClient: Fetching AniList MAL IDs for batch size: ${anilistIds.size}")
        val queryStr = """
            query(${'$'}ids:[Int]) {
                Page(perPage: 50) {
                    media(id_in: ${'$'}ids, type: ANIME) {
                        id
                        idMal
                    }
                }
            }
        """.trimIndent()
        
        val payload = AnilistGraphQLQuery(query = queryStr, variables = AnilistVariables(ids = anilistIds))
        val requestBody = json.encodeToString(payload).toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody)
            
        anilistToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw UnauthorizedException("AniList OAuth token is expired or invalid")
            }
            if (!response.isSuccessful) throw IOException("Failed to fetch from AniList: ${response.code}")
            
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val result = json.decodeFromString<AnilistGraphQLResponse>(body)
            
            val mediaList = result.data?.page?.media ?: emptyList()
            val map = mutableMapOf<Int, Int?>()
            
            mediaList.forEach { media ->
                map[media.id] = media.idMal
            }
            
            // Fill in any requested IDs that weren't returned by the API with null
            anilistIds.forEach { id ->
                if (!map.containsKey(id)) {
                    map[id] = null
                }
            }
            
            Logger.d("NetworkClient: Successfully fetched ${map.size} AniList mappings")
            map
        }
    }
}
