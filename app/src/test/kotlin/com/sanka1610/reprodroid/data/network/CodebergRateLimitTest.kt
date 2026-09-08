package com.sanka1610.reprodroid.data.provider

import io.ktor.http.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CodebergRateLimitTest {
    @Test
    fun `only matching well formed quota headers establish exhaustion`() {
        val now = Instant.parse("2026-09-08T00:00:00Z")
        val exhausted = codebergRateLimitEvidence(
            headersOf(
                "ratelimit-policy" to listOf("\"api\";q=100;w=60, \"burst\";q=10;w=1"),
                "ratelimit" to listOf("\"api\";r=0;t=17, \"burst\";r=4;t=1"),
            ),
            now,
        )
        assertTrue(exhausted.exhausted)
        assertEquals(0L, exhausted.remaining)
        assertEquals(Instant.parse("2026-09-08T00:00:17Z"), exhausted.retryNotBefore)

        val malformed = codebergRateLimitEvidence(
            headersOf(
                "ratelimit-policy" to listOf("\"api\";q=100;w=60"),
                "ratelimit" to listOf("X-RateLimit-Remaining: 0"),
            ),
            now,
        )
        assertFalse(malformed.exhausted)
        assertNull(malformed.remaining)
        assertNull(malformed.retryNotBefore)
    }

    @Test
    fun `unknown parameters are ignored while duplicates and overflow invalidate only their item`() {
        val now = Instant.parse("2026-09-08T00:00:00Z")
        val evidence = codebergRateLimitEvidence(
            headersOf(
                "ratelimit-policy" to listOf(
                    "\"bad\";q=10;q=11;w=60, \"api\";q=100;w=60;unknown=value",
                ),
                "ratelimit" to listOf(
                    "\"overflow\";r=999999999999999999999;t=1, \"api\";r=0;t=19;extension=yes",
                ),
            ),
            now,
        )
        assertTrue(evidence.exhausted)
        assertEquals(0L, evidence.remaining)
        assertEquals(now.plusSeconds(19), evidence.retryNotBefore)
    }
}
