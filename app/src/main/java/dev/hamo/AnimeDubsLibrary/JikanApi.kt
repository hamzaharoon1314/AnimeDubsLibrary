package dev.hamo.AnimeDubsLibrary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

import java.util.concurrent.TimeUnit

@Serializable
data class JikanResponse(
    val data: List<JikanAnime>,
    val pagination: JikanPagination
)

@Serializable
data class AnilistSearchResponse(val data: AnilistData)
@Serializable
data class AnilistData(val Page: AnilistPage)
@Serializable
data class AnilistPage(val media: List<AnilistMedia>)
@Serializable
data class AnilistMedia(val idMal: Int?)

@Serializable
data class JikanDetailResponse(
    val data: JikanAnimeDetail
)

@Serializable
data class JikanPagination(
    val has_next_page: Boolean,
    val current_page: Int
)

@Serializable
data class JikanAnime(
    val mal_id: Int,
    val title: String,
    val images: JikanImages
)

@Serializable
data class JikanAnimeDetail(
    val mal_id: Int,
    val title: String,
    val images: JikanImages,
    val synopsis: String? = null,
    val score: Double? = null,
    val episodes: Int? = null,
    val status: String? = null
)

@Serializable
data class JikanImages(
    val jpg: JikanImageUrls
)

@Serializable
data class JikanImageUrls(
    val image_url: String,
    val large_image_url: String? = null
)

object JikanApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 5,
        delayMillis: Long = 2000,
        block: suspend () -> T
    ): T {
        var currentDelay = delayMillis
        var lastException: Exception? = null
        for (i in 0 until maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (i == maxRetries - 1) break
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * 1.5).toLong()
            }
        }
        throw lastException ?: IOException("Unknown error")
    }

    suspend fun getTopAnime(page: Int = 1): JikanResponse = withContext(Dispatchers.IO) {
        executeWithRetry<JikanResponse> {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/top/anime?page=$page")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                json.decodeFromString<JikanResponse>(responseBody)
            }
        }
    }

    suspend fun searchAnimeMalId(query: String): Int? = withContext(Dispatchers.IO) {
        executeWithRetry<Int?> {
            val queryJson = """
                {
                  "query": "query (${'$'}search: String) { Page(page: 1, perPage: 1) { media(search: ${'$'}search, type: ANIME) { idMal } } }",
                  "variables": {
                    "search": "$query"
                  }
                }
            """.trimIndent()
            
            val requestBody = queryJson.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                val parsed = json.decodeFromString<AnilistSearchResponse>(responseBody)
                parsed.data.Page.media.firstOrNull()?.idMal
            }
        }
    }

    suspend fun getAnimeDetails(malId: Int): JikanDetailResponse = withContext(Dispatchers.IO) {
        executeWithRetry<JikanDetailResponse> {
            val request = Request.Builder()
                .url("https://api.jikan.moe/v4/anime/$malId")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                json.decodeFromString<JikanDetailResponse>(responseBody)
            }
        }
    }
}
