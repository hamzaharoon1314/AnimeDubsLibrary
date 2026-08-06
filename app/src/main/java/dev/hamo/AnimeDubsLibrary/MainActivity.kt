package dev.hamo.AnimeDubsLibrary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.animedubs.AnimeDubs
import com.animedubs.models.DubStatus
import com.animedubs.models.SyncState
import com.animedubs.rememberDubStatusByAnilistId
import com.animedubs.rememberDubStatusByMalId
import dagger.hilt.android.AndroidEntryPoint
import dev.hamo.AnimeDubsLibrary.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Store tokens in memory for the sample app
    val anilistTokenFlow = MutableStateFlow<String?>(null)
    val malTokenFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Install Android 12+ Splash Screen API
        installSplashScreen()
        
        // Handle if launched via Deep Link
        processIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(anilistTokenFlow, malTokenFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        lifecycleScope.launch {
            AuthManager.handleIntent(
                intent = intent,
                onAnilistTokenReceived = { token ->
                    anilistTokenFlow.value = token
                    AnimeDubs.setAnilistToken(token) // Pass to library
                    Toast.makeText(this@MainActivity, "AniList Login Success!", Toast.LENGTH_SHORT).show()
                },
                onMalTokenReceived = { token ->
                    malTokenFlow.value = token
                    Toast.makeText(this@MainActivity, "MAL Login Success!", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Search : Screen("search", "Search", Icons.Filled.Search)
    object Explore : Screen("explore", "Explore", Icons.Filled.Star)
    object Profile : Screen("profile", "Profile", Icons.Filled.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    anilistTokenFlow: MutableStateFlow<String?>,
    malTokenFlow: MutableStateFlow<String?>,
    viewModel: SampleViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val syncState by AnimeDubs.syncState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val items = listOf(Screen.Search, Screen.Explore, Screen.Profile)

    // Listen to UI Events for Snackbars
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AnimeDubs Sample") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = syncState.name,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        if (syncState == SyncState.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Search.route) { SearchScreen(viewModel) }
            composable(Screen.Explore.route) { ExploreScreen(viewModel) }
            composable(Screen.Profile.route) { 
                val malToken by malTokenFlow.collectAsStateWithLifecycle()
                val anilistToken by anilistTokenFlow.collectAsStateWithLifecycle()
                
                ProfileScreen(
                    malToken = malToken,
                    anilistToken = anilistToken,
                    onLogoutMal = { malTokenFlow.value = null },
                    onLogoutAnilist = { 
                        anilistTokenFlow.value = null
                        AnimeDubs.setAnilistToken(null)
                    }
                ) 
            }
        }
    }
}

enum class IdType { MAL, ANILIST }
data class SearchQuery(val id: Int, val type: IdType)

@Composable
fun SearchScreen(viewModel: SampleViewModel) {
    val syncState by AnimeDubs.syncState.collectAsStateWithLifecycle()
    var totalDubbedCount by remember { mutableIntStateOf(0) }
    
    var searchInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(IdType.MAL) }
    
    val searchQueries = remember { 
        mutableStateListOf(
            SearchQuery(21, IdType.MAL),
            SearchQuery(16498, IdType.MAL),
            SearchQuery(21459, IdType.ANILIST)
        ) 
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(syncState) {
        if (syncState == SyncState.SUCCESS || syncState == SyncState.IDLE) {
            totalDubbedCount = AnimeDubs.getAllDubbedMalIds().size
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Library Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Known Dubbed Anime: $totalDubbedCount")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.forceRefresh() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Force Refresh") }
                        
                        OutlinedButton(
                            onClick = {
                                viewModel.clearCache()
                                coroutineScope.launch {
                                    totalDubbedCount = AnimeDubs.getAllDubbedMalIds().size
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear Cache") }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Search Anime Status", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == IdType.MAL, onClick = { selectedType = IdType.MAL })
                        Text("MAL ID")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = selectedType == IdType.ANILIST, onClick = { selectedType = IdType.ANILIST })
                        Text("AniList ID")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            label = { Text("Enter ${selectedType.name} ID") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val id = searchInput.toIntOrNull()
                                if (id != null) {
                                    searchQueries.add(0, SearchQuery(id, selectedType))
                                    searchInput = ""
                                }
                            }
                        ) { Text("Add") }
                    }
                }
            }
        }
        
        item { Text("Observed Anime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        items(searchQueries, key = { it.id.toString() + it.type.name }) { query ->
            // Premium Animation: Item slides and fades in
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(300))
            ) {
                AnimeStatusCard(query = query)
            }
        }
    }
}

@Composable
fun ExploreScreen(viewModel: SampleViewModel) {
    val mockTrending = listOf(
        SearchQuery(113415, IdType.ANILIST), // Jujutsu Kaisen
        SearchQuery(101922, IdType.ANILIST), // Demon Slayer
        SearchQuery(11061, IdType.ANILIST),  // Hunter x Hunter
        SearchQuery(1, IdType.MAL),          // Cowboy Bebop (MAL ID)
        SearchQuery(20, IdType.MAL)          // Naruto (MAL ID)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Trending This Week",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Demonstrate Bulk Fetching API",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The AnimeDubs library features a highly optimized batched GraphQL query. " +
                        "Click the button below to fetch 20+ statuses in a single network request and measure its performance.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.runBulkFetchBenchmark() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run Bulk Fetch Benchmark 🚀")
                    }
                }
            }
        }
        items(mockTrending) { query ->
            AnimeStatusCard(query = query)
        }
    }
}

@Composable
fun ProfileScreen(
    malToken: String?,
    anilistToken: String?,
    onLogoutMal: () -> Unit,
    onLogoutAnilist: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = "Profile",
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Switch Profiles & Integrations",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MyAnimeList (MAL)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (malToken != null) {
                    Text("Successfully Authenticated!", color = MaterialTheme.colorScheme.primary)
                    Text("Access Token: ${malToken.take(10)}...", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLogoutMal, modifier = Modifier.fillMaxWidth()) {
                        Text("Logout of MAL")
                    }
                } else {
                    Text("Connect your MAL account using OAuth 2.0 PKCE.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { AuthManager.startMalAuth(context) }, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Login with MAL")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (anilistToken != null) {
                    Text("Successfully Authenticated!", color = MaterialTheme.colorScheme.primary)
                    Text("Access Token: ${anilistToken.take(10)}...", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Anilist OAuth Token injected into AnimeDubsLibrary!", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onLogoutAnilist, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Logout of AniList")
                    }
                } else {
                    Text("Connect your AniList account to bypass rate limits (90 req/min).")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { AuthManager.startAnilistAuth(context) }, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Login with AniList")
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeStatusCard(query: SearchQuery) {
    val statusResult by if (query.type == IdType.MAL) {
        rememberDubStatusByMalId(malId = query.id)
    } else {
        rememberDubStatusByAnilistId(anilistId = query.id)
    }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ID: ${query.id} (${query.type.name})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val resolvedText = buildString {
                if (query.type == IdType.ANILIST && statusResult.malId != -1) {
                    append("Resolved MAL ID: ${statusResult.malId}")
                } else if (query.type == IdType.MAL && statusResult.anilistId != null) {
                    append("Resolved AniList ID: ${statusResult.anilistId}")
                }
            }
            if (resolvedText.isNotEmpty()) {
                Text(
                    text = resolvedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            val targetColor = when (statusResult.status) {
                DubStatus.YES -> MaterialTheme.colorScheme.primary
                DubStatus.PARTIAL -> MaterialTheme.colorScheme.secondary
                DubStatus.NO -> MaterialTheme.colorScheme.error
                DubStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
            }
            
            // Premium Animation: Smoothly crossfade color when state changes
            val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(500))
            
            Text(
                text = "Dub Status: ${statusResult.status.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = animatedColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
