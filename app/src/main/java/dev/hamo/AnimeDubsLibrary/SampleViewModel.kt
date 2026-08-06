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
