# AnimeDubs Android Library

[![](https://jitpack.io/v/hamzaharoon1314/AnimeDubsLibrary.svg)](https://jitpack.io/#hamzaharoon1314/AnimeDubsLibrary)

A blazing fast, highly optimized, and production-ready native Kotlin Android library that empowers developers to easily check if a specific anime series has an English dub available.

Built as a native Android alternative to the popular [MAL-Dubs script](https://github.com/MAL-Dubs/MAL-Dubs), this library acts as a complete "Dub Engine". It handles aggressive memory caching, file I/O, extreme performance optimization, GraphQL chunking, and background synchronization entirely off the main thread.

---

## ⚡ The Architecture (Why Use This?)

* **Extreme Memory Efficiency:** Under the hood, this library parses JSON into primitive Kotlin `IntArray`s and uses `binarySearch`. This allows it to process tens of thousands of anime dub statuses in **O(log N) time** with zero `Integer` or `Map.Entry` object boxing. It uses practically zero RAM.
* **Jetpack Compose First:** Ships with first-class, lifecycle-aware `@Composable` extensions (`rememberDubStatusByMalId`) that pause execution when the app is backgrounded to save battery.
* **Reactive UI Flows:** Standard Kotlin `Flow`s automatically push UI updates the second a background dub sync succeeds.
* **Resilient Background Syncing:** Equipped with an exponential backoff retry mechanism. If the user drives through a tunnel and drops connection, the library quietly retries in the background (5s, 10s, 20s...) until it connects.
* **Solves the N+1 Query Problem:** Automatically chunks bulk AniList ID lookups into batches of 50 and executes them in parallel to respect API limits while delivering maximum speed.
* **Offline Resilient:** Strictly enforces a configurable cache TTL, but gracefully falls back to stale data if the device is offline or the network fails.
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
    val animeDubsVersion = "2.0.2" // Replace with the latest release tag
    implementation("com.github.hamzaharoon1314:AnimeDubsLibrary:$animeDubsVersion")
}
```

---

## 🚀 1. Initialization & Data Sources

Initialize the library once, preferably in your `Application` class. 

The library supports two different data sources. **MAL-Dubs** is the default (AGPL-3.0 licensed). **MyDubList** is the alternative (CC BY 4.0 licensed) which allows you to filter by Dub Language and Confidence metrics.

```kotlin
import com.animedubs.AnimeDubs
import com.animedubs.models.DataSource
import com.animedubs.models.Confidence
import com.animedubs.models.Language

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Default (Legacy MAL-Dubs source, 24-hour cache TTL)
        AnimeDubs.init(this)
        
        // 2. Custom Cache TTL (e.g. refresh every 6 hours instead of 24h)
        AnimeDubs.init(
            this,
            cacheTTLMillis = 6 * 60 * 60 * 1000L
        )
        
        // 3. MyDubList Source (Defaults to Low Confidence, English)
        AnimeDubs.init(this, dataSource = DataSource.MyDubList())
        
        // 4. Custom MyDubList (e.g. Only High Confidence Spanish Dubs)
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

---

## 🎨 2. Querying Dub Status (Three Ways)

Querying for an anime's dub status returns a `DubStatusResult` containing the enum `DubStatus` (`YES`, `NO`, `PARTIAL`, or `UNKNOWN`). 

You can retrieve this data using three different paradigms depending on your app's architecture:

### A. Jetpack Compose (Modern UI)
We provide highly optimized `@Composable` extension functions! These functions use `collectAsStateWithLifecycle()` under the hood, meaning they instantly update your UI when the background network sync finishes, but they **pause collection when the screen is hidden** to save battery.
```kotlin
import com.animedubs.rememberDubStatusByMalId
import com.animedubs.rememberDubStatusByAnilistId

@Composable
fun AnimeCard(malId: Int) {
    // Recomposes automatically whenever the network sync finishes!
    val result by rememberDubStatusByMalId(malId)
    
    Text(text = "Is it dubbed? ${result.status}")
}
```

### B. Reactive Flow (Standard Android View Architecture)
Perfect for `ViewModel`s or standard Android XML UIs. The flow instantly emits the currently cached value. If the background network sync finishes a few seconds later and the status changed, your UI receives the update automatically without manual polling.
```kotlin
lifecycleScope.launch {
    AnimeDubs.observeStatusByMalId(1).collect { result ->
        textView.text = "Cowboy Bebop is Dubbed: ${result.status}"
    }
}
```

### C. Traditional Suspend Functions (One-Off Checks)
Perfect for when you just need a quick, one-time snapshot of the status (e.g. inside a background worker or a static list adapter).
```kotlin
lifecycleScope.launch {
    // Pass a MyAnimeList (MAL) ID:
    val resultMal = AnimeDubs.getStatusByMalId(1)

    // Pass an AniList ID (Library automatically GraphQL resolves it to a MAL ID!):
    val resultAnilist = AnimeDubs.getStatusByAnilistId(1)
}
```

---

## 🧠 3. Advanced Querying Optimizations

### Bulk AniList Lookups (Solving N+1 Queries)
If you are displaying a `RecyclerView` or `LazyColumn` of 50+ anime and only have their AniList IDs, **do not query them one-by-one in a loop**. Doing so will hit AniList's API rate limits and block the UI. Instead, use the highly optimized bulk lookup API.
```kotlin
viewModelScope.launch {
    val myAnilistIds = listOf(1, 2, 5, 20, 1500) // Pass as many as you want!
    
    // The library automatically chunks missing IDs into concurrent batches of 50, 
    // fetches them via GraphQL, caches the mappings, and returns a combined map.
    val statuses: Map<Int, DubStatusResult> = AnimeDubs.getStatusesByAnilistIds(myAnilistIds)
    
    val myStatus = statuses[1]?.status ?: DubStatus.UNKNOWN
}
```

### Retrieve the Master Dub Catalog
Want to build a dedicated "Dubbed Anime Only" catalog page or add a master "Dubbed" filter to your app? You can easily retrieve a master list of all MyAnimeList IDs that currently have a `YES` or `PARTIAL` dub.
```kotlin
lifecycleScope.launch {
    val allDubbedMalIds: List<Int> = AnimeDubs.getAllDubbedMalIds()
    println("There are ${allDubbedMalIds.size} dubbed anime available!")
}
```

---

## ⚙️ 4. Cache Management & Pre-Fetching

### Pre-Fetching (Warm Up)
By default, the library defers downloading the large `dubInfo.json` file until the very first time you query an ID. To make that first lookup instantly available, call `warmUp()` right after `init()` to trigger the background sync immediately.
```kotlin
AnimeDubs.init(this)
GlobalScope.launch {
    AnimeDubs.warmUp()
}
```

### Manual Cache Controls
If your app includes a "Clear Cache" button in its settings, or a "Pull down to refresh" UI, you can manually override the library's internal logic:
```kotlin
viewModelScope.launch {
    // Force a network sync immediately, completely bypassing your cache TTL parameter
    AnimeDubs.forceRefresh()
    
    // Wipe all in-memory primitive arrays, local files, and DataStore caches
    AnimeDubs.clearCache()
}
```

---

## 🔐 5. Authentication, Errors & Network Resilience

### Bypassing AniList Rate Limits
AniList strictly limits anonymous IP requests to 30 requests per minute. If your user is logged in to AniList in your app, pass their OAuth token to the library to increase the limit to 90 requests per minute!
```kotlin
AnimeDubs.setAnilistToken("eyJ0eXAiOiLCJhbG...")
```
*(To clear the token when a user logs out, simply pass `null`)*.

### Observing Background Sync States
The library exposes a Kotlin `StateFlow` so you can observe the background networking operations in real-time. This is incredibly useful for catching expired tokens or showing "Syncing" spinners.

```kotlin
viewModelScope.launch {
    AnimeDubs.syncState.collect { state ->
        when (state) {
            SyncState.IDLE -> { /* Doing nothing */ }
            SyncState.SYNCING -> { /* Downloading data... */ }
            SyncState.SUCCESS -> { /* Data synced successfully! */ }
            SyncState.ERROR -> {
                // Device is offline, or GitHub is down. 
                // The library has ALREADY triggered a background exponential backoff retry.
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

## 🛠️ 6. Architecture & Dependency Injection (Hilt)

Under the hood, the `AnimeDubs` object acts as a singleton implementing the `AnimeDubsClient` interface. 
If your app uses modern Dependency Injection like **Dagger/Hilt** or **Koin**, you can effortlessly provide this interface to your ViewModels for cleaner architecture and easy mocking in your own tests!

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AnimeDubsModule {
    
    @Provides
    @Singleton
    fun provideAnimeDubsClient(@ApplicationContext context: Context): AnimeDubsClient {
        // Centralize your TTL and Source config here for your entire app!
        AnimeDubs.init(
            context, 
            dataSource = DataSource.MyDubList(),
            cacheTTLMillis = 6 * 60 * 60 * 1000L
        ) 
        return AnimeDubs
    }
}
```

### Debugging
If data isn't showing up as expected, enable internal logging to see exact cache-hits, TTL expirations, Array Sorting performance, exponential backoff retries, and AniList chunking logs directly in Logcat.
```kotlin
AnimeDubs.isDebugLoggingEnabled = true
```

---

## ⚖️ 7. License & Required Attribution

This project is licensed under the [Apache License 2.0](LICENSE). However, the datasets themselves carry distinct licenses. 

To easily comply with these licenses in your app, we have provided a helper function that returns the exact legal attribution string required for whichever source you have configured:
```kotlin
// Displays this in your App's "About" or "Settings" screen:
val attributionText = AnimeDubs.getAttributionText() 
```

### Dataset Licenses Overview

**1. MAL-Dubs (Default Source)**
The default dataset is sourced from [MAL-Dubs](https://github.com/MAL-Dubs/MAL-Dubs). 
* **License:** **GNU Affero General Public License (AGPL-3.0)**. 
* Ensure your usage of this specific dataset complies with this license.

**2. MyDubList (Alternative Source)**
If you initialize the library using `DataSource.MyDubList`, the dataset is sourced from [MyDubList](https://mydublist.com).
* **License:** **Creative Commons Attribution 4.0 International (CC BY 4.0)**. 
* **Requirement:** You **must** provide attribution in your app's UI to comply with CC BY 4.0. The `getAttributionText()` helper is designed to make this easy.

---
*Built with ❤️ for Anime fans and Android developers.*
