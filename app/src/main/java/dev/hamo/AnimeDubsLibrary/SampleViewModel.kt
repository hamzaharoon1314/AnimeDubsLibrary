package dev.hamo.AnimeDubsLibrary

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animedubs.AnimeDubs
import com.animedubs.AnimeDubsClient
import com.animedubs.models.Confidence
import com.animedubs.models.DataSource
import com.animedubs.models.Language
import com.animedubs.models.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.measureTimeMillis

enum class DatasetType { MAL, MY_DUB_LIST }

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val app: Application
) : ViewModel() {

    // UI Events like Snackbars
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    // Active Dataset selection
    private val _activeDataset = MutableStateFlow(DatasetType.MAL)
    val activeDataset = _activeDataset.asStateFlow()

    // Config for MyDubList
    private val _selectedLanguage = MutableStateFlow(Language.ENGLISH)
    val selectedLanguage = _selectedLanguage.asStateFlow()

    private val _selectedConfidence = MutableStateFlow(Confidence.LOW)
    val selectedConfidence = _selectedConfidence.asStateFlow()

    // Clients
    val malClient: AnimeDubsClient = AnimeDubs // Singleton
    
    private val _myDubListClient = MutableStateFlow(AnimeDubs.createClient(app, DataSource.MyDubList()))
    val myDubListClient = _myDubListClient.asStateFlow()

    val activeClient: AnimeDubsClient
        get() = if (activeDataset.value == DatasetType.MAL) malClient else myDubListClient.value

    private val _topAnime = MutableStateFlow<List<JikanAnime>>(emptyList())
    val topAnime = _topAnime.asStateFlow()

    private val _isLoadingTopAnime = MutableStateFlow(true)
    val isLoadingTopAnime = _isLoadingTopAnime.asStateFlow()

    private val _topAnimeError = MutableStateFlow<String?>(null)
    val topAnimeError = _topAnimeError.asStateFlow()

    private var currentPage = 1
    private var hasNextPage = true
    
    private val _isFetchingMore = MutableStateFlow(false)
    val isFetchingMore = _isFetchingMore.asStateFlow()

    init {
        // Observe network state for Error Handling (Offline Mode)
        // We'll just observe the MAL client for global errors to keep it simple,
        // or you could observe both and merge.
        malClient.syncState
            .onEach { state ->
                when (state) {
                    SyncState.ERROR -> emitSnackbar("Network Error: Failed to sync MAL dub status.")
                    SyncState.UNAUTHORIZED -> emitSnackbar("Auth Error: AniList Token is invalid.")
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
            
        fetchTopAnime()
    }

    fun fetchTopAnime() {
        viewModelScope.launch {
            _isLoadingTopAnime.value = true
            _topAnimeError.value = null
            currentPage = 1
            try {
                val response = JikanApi.getTopAnime(page = currentPage)
                _topAnime.value = response.data
                hasNextPage = response.pagination.has_next_page
            } catch (e: Exception) {
                e.printStackTrace()
                _topAnimeError.value = e.message ?: "Unknown Error"
                emitSnackbar("Failed to fetch top anime: ${e.message}")
            } finally {
                _isLoadingTopAnime.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isFetchingMore.value || !hasNextPage || _isLoadingTopAnime.value) return
        
        viewModelScope.launch {
            _isFetchingMore.value = true
            try {
                val nextPage = currentPage + 1
                val response = JikanApi.getTopAnime(page = nextPage)
                
                // Deduplicate to prevent Compose key crashes (Jikan top list can shift)
                val currentIds = _topAnime.value.map { it.mal_id }.toSet()
                val newUniqueAnime = response.data.filter { it.mal_id !in currentIds }
                
                _topAnime.value = _topAnime.value + newUniqueAnime
                currentPage = nextPage
                hasNextPage = response.pagination.has_next_page
            } catch (e: Exception) {
                e.printStackTrace()
                emitSnackbar("Failed to load more anime: ${e.message}")
            } finally {
                _isFetchingMore.value = false
            }
        }
    }

    // --- Search & Details ---

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    fun searchAnimeByTitle(query: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // Jikan's search endpoint is too flaky (often 504 on popular titles like "One Piece").
                // We use Anilist's GraphQL API as a highly reliable fallback to find the MAL ID.
                val malId = JikanApi.searchAnimeMalId(query)
                if (malId != null) {
                    onResult(malId)
                } else {
                    emitSnackbar("No anime found for \"$query\"")
                    onResult(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emitSnackbar("Search failed: ${e.message}")
                onResult(null)
            } finally {
                _isSearching.value = false
            }
        }
    }

    private val _animeDetail = MutableStateFlow<JikanAnimeDetail?>(null)
    val animeDetail = _animeDetail.asStateFlow()
    
    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail = _isLoadingDetail.asStateFlow()

    fun fetchAnimeDetails(malId: Int) {
        viewModelScope.launch {
            _isLoadingDetail.value = true
            _animeDetail.value = null
            try {
                val response = JikanApi.getAnimeDetails(malId)
                _animeDetail.value = response.data
            } catch (e: Exception) {
                e.printStackTrace()
                emitSnackbar("Failed to fetch details: ${e.message}")
            } finally {
                _isLoadingDetail.value = false
            }
        }
    }

    fun setDatasetType(type: DatasetType) {
        _activeDataset.value = type
    }

    fun setMyDubListConfig(language: Language, confidence: Confidence) {
        _selectedLanguage.value = language
        _selectedConfidence.value = confidence
        _myDubListClient.value = AnimeDubs.createClient(
            context = app,
            dataSource = DataSource.MyDubList(confidence, language)
        )
    }

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
                val results = activeClient.getStatusesByAnilistIds(mockTrendingIds)
                resultCount = results.size
            }
            
            emitSnackbar("Bulk Fetch: Retrieved $resultCount statuses in ${timeTaken}ms using ${activeDataset.value}! 🚀")
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            activeClient.forceRefresh()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            activeClient.clearCache()
        }
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(message)
        }
    }
}
