# AnimeDubs Android Library

[![](https://jitpack.io/v/hamzaharoon1314/AnimeDubsLibrary.svg)](https://jitpack.io/#hamzaharoon1314/AnimeDubsLibrary)

A blazing fast, highly optimized, and production-ready native Kotlin Android library that allows developers to easily check if a specific anime series has an English dub available.

Built as a native Android alternative to the popular [MAL-Dubs script](https://github.com/MAL-Dubs/MAL-Dubs), this library takes care of aggressive memory caching, extreme performance optimization, GraphQL chunking, and background synchronization entirely off the main thread.

---

## ⚡ Key Features

* **Extreme Memory Efficiency:** Uses `IntArray`s and `binarySearch` to process tens of thousands of anime dub statuses in `O(log N)` time. No bloated `Map`s or `Integer` objects here—just raw, lightweight speed!
* **Reactive UI Flows:** Provides modern Kotlin `Flow`s that automatically push UI updates the moment background dub syncing succeeds, alongside traditional `suspend` functions for simple snapshot lookups.
* **Resilient Background Syncing:** Equipped with an exponential backoff retry mechanism. If the network drops, the library quietly retries in the background until it connects.
* **Solves the N+1 Query Problem:** Automatically chunks bulk AniList ID lookups into batches of 50 and executes them in parallel to respect API limits while delivering maximum speed.
* **Offline Resilient:** Strictly enforces a configurable cache TTL, but gracefully falls back to stale data if the device is offline or the network fails.
* **Coroutine First:** Fully asynchronous API built on Kotlin Coroutines and `Dispatchers.IO`. Safe to call directly from the UI thread!
* **Rate Limit Protection:** Incorporates Coroutine `Semaphore` limits and OAuth token injection to protect your app from hitting `HTTP 429` API bans on AniList.

---

## 📦 Installation

This library is distributed via JitPack.

**1. Add the JitPack repository to your root `settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Add the dependency to your app-level `build.gradle.kts`:**
```kotlin
dependencies {
    implementation("com.github.hamzaharoon1314:AnimeDubsLibrary:Tag")
}
```

---

## 🚀 Basic Usage

### 1. Initialization
Initialize the library once, preferably in your `Application` class. By default, it uses the **MAL-Dubs** data source, which provides comprehensive coverage for English dubs.

```kotlin
import com.animedubs.AnimeDubs
import com.animedubs.models.DataSource
import com.animedubs.models.Confidence
import com.animedubs.models.Language

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Default Initialization (Legacy MAL-Dubs source, 24-hour cache TTL)
        AnimeDubs.init(this)
        
        // 2. Custom Cache TTL (e.g. refresh every 6 hours)
        AnimeDubs.init(
            this,
            cacheTTLMillis = 6 * 60 * 60 * 1000L
        )
        
        // 3. Use MyDubList Source (Defaults to Low Confidence, English)
        AnimeDubs.init(this, dataSource = DataSource.MyDubList())
        
        // 4. Custom MyDubList Configuration (e.g. High Confidence, Spanish)
        AnimeDubs.init(
            this, 
            dataSource = DataSource.MyDubList(
                confidence = Confidence.HIGH, 
                language = Language.SPANISH
            )
        )
    }
}
```

### 2. Querying Dub Status (Suspend vs. Flow)
Querying for an anime's dub status returns a `DubStatusResult` containing the enum `DubStatus` (`YES`, `NO`, `PARTIAL`, or `UNKNOWN`).

You have two choices on how to retrieve the data:

#### A. Traditional Suspend Functions
Perfect for when you just need a quick, one-time snapshot of the status.
```kotlin
lifecycleScope.launch {
    val resultMal = AnimeDubs.getStatusByMalId(1)
    println("Cowboy Bebop (MAL): ${resultMal.status}") // YES

    val resultAnilist = AnimeDubs.getStatusByAnilistId(1)
    println("Cowboy Bebop (AniList): ${resultAnilist.status}") // YES
}
```

#### B. Reactive Flow (Recommended)
Perfect for dynamic UIs. The flow instantly emits the currently cached value. If the background network sync finishes a few seconds later and the status changed, your UI automatically receives the update!
```kotlin
lifecycleScope.launch {
    AnimeDubs.observeStatusByMalId(1).collect { result ->
        // This updates dynamically without manual polling!
        textView.text = "Cowboy Bebop is Dubbed: ${result.status}"
    }
}
```

---

## 🧠 Advanced Usage & Optimizations

### Bulk AniList Lookups (For Lists & Grids)
If you are displaying a `RecyclerView` or `LazyColumn` of 50+ anime and only have their AniList IDs, **do not query them one-by-one in a loop**. Use the highly optimized bulk lookup API.
```kotlin
viewModelScope.launch {
    val myAnilistIds = listOf(1, 2, 5, 20, 1500) // Pass as many as you want!
    
    // The library automatically chunks missing IDs into batches of 50, 
    // fetches them concurrently via GraphQL, caches them, and returns a combined map.
    val statuses: Map<Int, DubStatusResult> = AnimeDubs.getStatusesByAnilistIds(myAnilistIds)
    
    val myStatus = statuses[1]?.status ?: DubStatus.UNKNOWN
}
```

### Retrieve All Dubbed Anime
Want to build a dedicated "Dubbed Anime Only" catalog page or add a master "Dubbed" filter to your app? You can easily retrieve a master list of all MyAnimeList IDs that currently have a `YES` or `PARTIAL` dub.
```kotlin
lifecycleScope.launch {
    val allDubbedMalIds: List<Int> = AnimeDubs.getAllDubbedMalIds()
    println("There are ${allDubbedMalIds.size} dubbed anime available!")
}
```

### Pre-Fetching (Warm Up)
By default, the library defers downloading the large `dubInfo.json` file until the very first time you query an ID. To make that first lookup instant, call `warmUp()` right after `init()` to trigger the background sync immediately.
```kotlin
AnimeDubs.init(this)
GlobalScope.launch {
    AnimeDubs.warmUp()
}
```

### Manual Cache Controls
```kotlin
viewModelScope.launch {
    // Force a network sync immediately, completely bypassing your cache TTL parameter
    AnimeDubs.forceRefresh()
    
    // Wipe all in-memory arrays, local files, and DataStore caches to free up disk space
    AnimeDubs.clearCache()
}
```

---

## 🔐 Authentication & Error Handling

### Bypassing AniList Rate Limits
AniList strictly limits anonymous IP requests to 30 requests per minute. If your user is logged in to AniList in your app, pass their OAuth token to the library to increase the limit to 90 requests per minute.
```kotlin
AnimeDubs.setAnilistToken("eyJ0eXAiOiLCJhbG...")
```
*(To clear the token when a user logs out, simply pass `null`)*.

### Observing Background Sync States
The library exposes a Kotlin `StateFlow` so you can observe the background networking operations in real-time. This is incredibly useful for catching expired tokens!

```kotlin
viewModelScope.launch {
    AnimeDubs.syncState.collect { state ->
        when (state) {
            SyncState.IDLE -> { /* Doing nothing */ }
            SyncState.SYNCING -> { /* Downloading data... */ }
            SyncState.SUCCESS -> { /* Data synced successfully! */ }
            SyncState.ERROR -> {
                // Device is offline, or GitHub is down. 
                // The library has already triggered a background exponential backoff retry.
            }
            SyncState.UNAUTHORIZED -> {
                // The AniList OAuth token you provided via setAnilistToken() 
                // is expired or revoked. Prompt your user to log in again!
                AnimeDubs.setAnilistToken(null)
            }
        }
    }
}
```

---

## 🛠️ Architecture & Dependency Injection (Hilt)

Under the hood, the `AnimeDubs` object acts as a singleton implementing the `AnimeDubsClient` interface. 
If your app uses modern Dependency Injection like **Dagger/Hilt**, you can effortlessly provide this interface to your ViewModels for cleaner architecture and mocking in your own tests!

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AnimeDubsModule {
    
    @Provides
    @Singleton
    fun provideAnimeDubsClient(@ApplicationContext context: Context): AnimeDubsClient {
        // Tip: Set your TTL and Source here for your entire app!
        AnimeDubs.init(context, cacheTTLMillis = 6 * 60 * 60 * 1000L) 
        return AnimeDubs
    }
}

// In your ViewModel:
@HiltViewModel
class AnimeViewModel @Inject constructor(
    private val dubsClient: AnimeDubsClient
) : ViewModel() {
    // Use dubsClient.observeStatusByMalId(), dubsClient.getAllDubbedMalIds(), etc.
}
```

### Debugging
If data isn't showing up as expected, enable internal logging to see exact cache-hits, TTL checks, Array Sorting, and AniList chunking logs in Logcat.
```kotlin
AnimeDubs.isDebugLoggingEnabled = true
```

## ⚖️ License & Credits

This project is licensed under the [Apache License 2.0](LICENSE).

### Attribution & Dataset Licenses

**MAL-Dubs (Default Source)**
The default dataset is sourced from [MAL-Dubs](https://github.com/MAL-Dubs/MAL-Dubs). A huge thanks to the maintainers of MAL-Dubs for curating and providing this invaluable data. 
**Important:** If you use the `DataSource.MalDubs`, the dataset is licensed under the **GNU Affero General Public License (AGPL-3.0)**. Ensure your usage complies with this license.

To make this easy, the library provides a helper method that returns the exact required attribution string for whichever source you have configured:
```kotlin
val text = AnimeDubs.getAttributionText() 
```

**MyDubList (Alternative Source)**
If you initialize the library using `DataSource.MyDubList`, the dataset is sourced from [MyDubList](https://mydublist.com) and is licensed under **Creative Commons Attribution 4.0 International (CC BY 4.0)**. 
**Important:** If you use the `DataSource.MyDubList`, you **must** provide attribution in your app's UI (e.g., About screen, Settings, or Footer) to comply with the CC BY 4.0 license.

---
*Built with ❤️ for Anime fans and Android developers.*
