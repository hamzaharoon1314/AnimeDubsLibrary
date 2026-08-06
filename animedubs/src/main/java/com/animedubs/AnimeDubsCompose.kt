package com.animedubs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animedubs.models.DubStatusResult

/**
 * A Jetpack Compose extension that reactively observes the dub status of an Anime by its MAL ID.
 * 
 * This uses [collectAsStateWithLifecycle] internally to ensure the Flow is only collected
 * when the UI is actually visible on screen, saving battery and background resources.
 * 
 * @param malId The MyAnimeList ID of the anime.
 * @return A Compose [State] containing the [DubStatusResult]. 
 * Recomposes automatically whenever the background sync completes or the cache updates.
 */
@Composable
fun rememberDubStatusByMalId(malId: Int): State<DubStatusResult> {
    return AnimeDubs.observeStatusByMalId(malId).collectAsStateWithLifecycle(
        initialValue = DubStatusResult(malId = malId, anilistId = null, status = com.animedubs.models.DubStatus.UNKNOWN)
    )
}

/**
 * A Jetpack Compose extension that reactively observes the dub status of an Anime by its AniList ID.
 * 
 * @param anilistId The AniList ID of the anime.
 * @return A Compose [State] containing the [DubStatusResult]. 
 */
@Composable
fun rememberDubStatusByAnilistId(anilistId: Int): State<DubStatusResult> {
    return AnimeDubs.observeStatusByAnilistId(anilistId).collectAsStateWithLifecycle(
        initialValue = DubStatusResult(malId = -1, anilistId = anilistId, status = com.animedubs.models.DubStatus.UNKNOWN)
    )
}
