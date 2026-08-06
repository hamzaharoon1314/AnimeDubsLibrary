package com.animedubs.internal.network

import com.animedubs.models.DubInfoPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses correctly formatted dub info payload`() {
        val jsonString = """
            {
                "yes": [1, 2, 3],
                "partial": [4, 5],
                "no": [6]
            }
        """.trimIndent()

        val payload = json.decodeFromString<DubInfoPayload>(jsonString)
        
        assertEquals(3, payload.yes.size)
        assertTrue(payload.yes.contains(2))
        assertEquals(2, payload.partial.size)
        assertEquals(1, payload.no.size)
    }

    @Test
    fun `gracefully ignores unexpected fields in payload`() {
        val jsonString = """
            {
                "yes": [1],
                "partial": [],
                "no": [],
                "future_dubs": [99, 100],
                "metadata": {
                    "last_updated": "2026-08-05"
                }
            }
        """.trimIndent()

        // Should not crash because of ignoreUnknownKeys = true
        val payload = json.decodeFromString<DubInfoPayload>(jsonString)
        
        assertEquals(1, payload.yes.size)
        assertTrue(payload.partial.isEmpty())
    }
}
