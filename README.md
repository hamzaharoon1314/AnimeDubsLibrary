# AnimeDubs Android Library

[![](https://jitpack.io/v/hamzaharoon1314/AnimeDubsLibrary.svg)](https://jitpack.io/#hamzaharoon1314/AnimeDubsLibrary)

A blazing fast, highly optimized, and production-ready native Kotlin Android library that allows developers to easily check if a specific anime series has an English dub available.

Built as a native Android alternative to the popular [MAL-Dubs script](https://github.com/MAL-Dubs/MAL-Dubs), this library takes care of aggressive memory caching, file I/O, GraphQL chunking, and background synchronization entirely off the main thread.

---

## ⚡ Key Features

* **Lightning Fast:** Uses an O(1) in-memory cache architecture. Lookups happen instantly without blocking the UI.
* **Solves the N+1 Query Problem:** Automatically chunks bulk AniList ID lookups into batches of 50 and executes them in parallel to respect API limits while delivering maximum speed.
* **Offline Resilient:** Strictly enforces a 24-hour cache TTL, but gracefully falls back to stale data if the device is offline or the network fails.
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
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

**2. Add the dependency to your app-level `build.gradle.kts`:**
```kotlin
dependencies {
    implementation("com.github.hamzaharoon1314:AnimeDubsLibrary:1.0.0")
}
```

---

## 🚀 Basic Usage

### 1. Initialization
Initialize the library once, preferably in your `Application` class.
```kotlin
import com.animedubs.AnimeDubs

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnimeDubs.init(this)
    }
}
```

### 2. Querying Dub Status
Querying for an anime's dub status returns a `DubStatusResult` containing the enum `DubStatus` (`YES`, `NO`, `PARTIAL`, or `UNKNOWN`).

```kotlin
lifecycleScope.launch {
    // 1. If you already have a MyAnimeList (MAL) ID:
    val resultMal = AnimeDubs.getStatusByMalId(1)
    println("Cowboy Bebop (MAL): ${resultMal.status}") // YES

    // 2. If you only have an AniList ID:
    // The library will automatically resolve it to a MAL ID via GraphQL!
    val resultAnilist = AnimeDubs.getStatusByAnilistId(1)
    println("Cowboy Bebop (AniList): ${resultAnilist.status}") // YES
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
    // Force a network sync immediately, completely bypassing the 24-hour TTL check
    AnimeDubs.forceRefresh()
    
    // Wipe all in-memory maps, local files, and DataStore caches to free up disk space
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
                // The library is gracefully falling back to cached data.
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
        AnimeDubs.init(context)
        return AnimeDubs
    }
}

// In your ViewModel:
@HiltViewModel
class AnimeViewModel @Inject constructor(
    private val dubsClient: AnimeDubsClient
) : ViewModel() {
    // Use dubsClient.warmUp(), dubsClient.getStatusByMalId(), etc.
}
```

### Debugging
If data isn't showing up as expected, enable internal logging to see exact cache-hits, TTL checks, and AniList chunking logs in Logcat.
```kotlin
AnimeDubs.isDebugLoggingEnabled = true
```

---
*Built with ❤️ for Anime fans and Android developers.*
