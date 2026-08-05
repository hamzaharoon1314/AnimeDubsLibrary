package com.animedubs.internal.repository

import com.animedubs.internal.cache.CacheManager
import com.animedubs.internal.network.NetworkClient
import com.animedubs.internal.network.UnauthorizedException
import com.animedubs.models.DubInfoPayload
import com.animedubs.models.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeDubsRepositoryTest {

    private lateinit var cacheManager: CacheManager
    private lateinit var networkClient: NetworkClient
    private lateinit var repository: AnimeDubsRepository

    @Before
    fun setup() {
        cacheManager = mockk(relaxed = true)
        networkClient = mockk(relaxed = true)
        repository = AnimeDubsRepository(cacheManager, networkClient)
    }

    @Test
    fun `warmUp triggers network sync when cache is invalid`() = runTest {
        coEvery { cacheManager.isCacheValid() } returns false
        coEvery { networkClient.fetchDubInfo() } returns DubInfoPayload(yes = listOf(1, 2))

        repository.warmUp()

        coVerify(exactly = 1) { networkClient.fetchDubInfo() }
        coVerify(exactly = 1) { cacheManager.saveDubInfo(any(), any(), any()) }
        assertEquals(SyncState.SUCCESS, repository.syncState.value)
    }

    @Test
    fun `warmUp skips network sync when cache is valid`() = runTest {
        coEvery { cacheManager.isCacheValid() } returns true

        repository.warmUp()

        coVerify(exactly = 0) { networkClient.fetchDubInfo() }
    }

    @Test
    fun `unauthorized exception from network client updates sync state`() = runTest {
        coEvery { cacheManager.isCacheValid() } returns true
        coEvery { cacheManager.getMalIdsForAnilist(any()) } returns emptyMap()
        coEvery { networkClient.fetchMalIdsFromAnilist(any()) } throws UnauthorizedException("Expired token")

        repository.getStatusesByAnilistIds(listOf(100))

        assertEquals(SyncState.UNAUTHORIZED, repository.syncState.value)
    }
}
