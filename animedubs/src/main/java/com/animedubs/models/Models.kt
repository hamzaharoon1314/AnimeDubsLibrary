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

enum class Confidence(val value: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    VERY_HIGH("very-high")
}

enum class Language(val value: String) {
    ENGLISH("english"),
    SPANISH("spanish"),
    GERMAN("german"),
    FRENCH("french"),
    ITALIAN("italian"),
    PORTUGUESE("portuguese"),
    KOREAN("korean"),
    CHINESE("chinese"),
    TAGALOG("tagalog"),
    ARABIC("arabic"),
    POLISH("polish"),
    HINDI("hindi"),
    HUNGARIAN("hungarian"),
    SWEDISH("swedish"),
    NORWEGIAN("norwegian"),
    HEBREW("hebrew"),
    DUTCH("dutch"),
    RUSSIAN("russian"),
    INDONESIAN("indonesian"),
    DANISH("danish"),
    THAI("thai")
}

sealed class DataSource {
    object MalDubs : DataSource()
    data class MyDubList(
        val confidence: Confidence = Confidence.LOW,
        val language: Language = Language.ENGLISH
    ) : DataSource()
}

// Internal models for network parsing

@Serializable
internal data class DubInfoPayload(
    @SerialName("dubbed") val yes: List<Int> = emptyList(),
    @SerialName("incomplete") val partial: List<Int> = emptyList(),
    val no: List<Int> = emptyList()
)

@Serializable
internal data class MyDubListPayload(
    val language: String? = null,
    val dubbed: List<Int> = emptyList(),
    val partial: List<Int> = emptyList()
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
