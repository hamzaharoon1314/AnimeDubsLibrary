package com.animedubs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DubStatus { 
    YES, 
    NO, 
    PARTIAL, 
    UNKNOWN 
}

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR,
    UNAUTHORIZED
}

data class DubStatusResult(
    val malId: Int, 
    val anilistId: Int? = null, 
    val status: DubStatus
)

// Internal models for network parsing

@Serializable
internal data class DubInfoPayload(
    val yes: List<Int> = emptyList(),
    val partial: List<Int> = emptyList(),
    val no: List<Int> = emptyList()
)

@Serializable
internal data class AnilistGraphQLQuery(
    val query: String,
    val variables: AnilistVariables
)

@Serializable
internal data class AnilistVariables(
    val ids: List<Int>
)

@Serializable
internal data class AnilistGraphQLResponse(
    val data: AnilistData? = null
)

@Serializable
internal data class AnilistData(
    @SerialName("Page") val page: AnilistPage? = null
)

@Serializable
internal data class AnilistPage(
    val media: List<AnilistMedia> = emptyList()
)

@Serializable
internal data class AnilistMedia(
    val id: Int,
    val idMal: Int? = null
)
