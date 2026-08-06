package dev.hamo.AnimeDubsLibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animedubs.AnimeDubs
import com.animedubs.models.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.measureTimeMillis

import com.animedubs.AnimeDubsClient

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val animeDubsClient: AnimeDubsClient
) : ViewModel() {

    // UI Events like Snackbars
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        // Observe network state for Error Handling (Offline Mode)
        animeDubsClient.syncState
            .onEach { state ->
                when (state) {
                    SyncState.ERROR -> emitSnackbar("Network Error: Failed to sync dub status.")
                    SyncState.UNAUTHORIZED -> emitSnackbar("Auth Error: AniList Token is invalid.")
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Demonstrates the Bulk Fetch API capabilities.
     * Fetches 20 trending AniList IDs in a single batched query, 
     * measures the exact time, and shows the result in a Snackbar.
     */
    fun runBulkFetchBenchmark() {
        viewModelScope.launch {
            val mockTrendingIds = listOf(
                113415, 101922, 11061, 16498, 21459, 
                21, 20, 1, 1535, 5114, 
                9253, 28851, 31964, 32281, 11757, 
                30, 2001, 1575, 199, 170
            )
            
            var resultCount = 0
            val timeTaken = measureTimeMillis {
                val results = animeDubsClient.getStatusesByAnilistIds(mockTrendingIds)
                resultCount = results.size
            }
            
            emitSnackbar("Bulk Fetch: Retrieved $resultCount statuses in ${timeTaken}ms! 🚀")
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            animeDubsClient.forceRefresh()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            animeDubsClient.clearCache()
        }
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(message)
        }
    }
}
