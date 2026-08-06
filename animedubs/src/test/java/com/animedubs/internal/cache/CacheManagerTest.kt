package com.animedubs.internal.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.animedubs.models.DataSource
import com.animedubs.models.DubStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cacheManager: CacheManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        
        // Mock Context.cacheDir
        every { context.cacheDir } returns tempFolder.root
        
        // Mock the top-level extension property Context.dataStore
        mockkStatic("com.animedubs.internal.cache.CacheManagerKt")
        every { any<Context>().dataStore } returns dataStore

        cacheManager = CacheManager(context, DataSource.MalDubs, 24 * 60 * 60 * 1000L)
    }

    @Test
    fun `saveDubInfo sorts and allows binary search`() = runTest {
        // We pass unsorted arrays
        val yesList = listOf(10, 2, 5, 1)
        val partialList = listOf(20, 15)
        val noList = listOf(100, 50)

        // Mock datastore edit
        coEvery { dataStore.updateData(any()) } returns preferencesOf()

        cacheManager.saveDubInfo(yesList, partialList, noList)

        assertEquals(DubStatus.YES, cacheManager.getDubStatus(5))
        assertEquals(DubStatus.YES, cacheManager.getDubStatus(1))
        assertEquals(DubStatus.PARTIAL, cacheManager.getDubStatus(15))
        assertEquals(DubStatus.NO, cacheManager.getDubStatus(100))
        assertEquals(DubStatus.UNKNOWN, cacheManager.getDubStatus(999))
    }

    @Test
    fun `isCacheValid returns false when file does not exist`() = runTest {
        coEvery { dataStore.data } returns flowOf(preferencesOf())
        
        assertFalse(cacheManager.isCacheValid())
    }

    @Test
    fun `getAllDubbed combines yes and partial lists`() = runTest {
        val yesList = listOf(1, 2)
        val partialList = listOf(3)
        val noList = listOf(4)

        coEvery { dataStore.updateData(any()) } returns preferencesOf()
        cacheManager.saveDubInfo(yesList, partialList, noList)

        val allDubbed = cacheManager.getAllDubbed()
        assertEquals(3, allDubbed.size)
        assertTrue(allDubbed.containsAll(listOf(1, 2, 3)))
        assertFalse(allDubbed.contains(4))
    }
}
