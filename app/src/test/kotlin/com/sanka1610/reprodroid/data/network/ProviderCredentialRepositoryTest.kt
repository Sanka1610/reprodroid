package com.sanka1610.reprodroid.data.provider

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderCredentialRepositoryTest {
    @Test
    fun `repository publishes save replacement and deletion without exposing token in status`() {
        val store = MemoryStore()
        val repository = ProviderCredentialRepository(
            store,
            Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(
            ProviderCredentialAvailability.NOT_CONFIGURED,
            repository.statuses.value.getValue(ProviderId.GITHUB).availability,
        )

        repository.save(ProviderId.GITHUB, FIRST_TOKEN)
        assertEquals(FIRST_TOKEN, repository.readToken(ProviderId.GITHUB))
        assertEquals(
            ProviderCredentialStatus(
                ProviderId.GITHUB,
                ProviderCredentialAvailability.CONFIGURED,
                "2026-09-16T00:00:00Z",
            ),
            repository.statuses.value.getValue(ProviderId.GITHUB),
        )

        repository.save(ProviderId.GITHUB, REPLACEMENT_TOKEN)
        assertEquals(REPLACEMENT_TOKEN, repository.readToken(ProviderId.GITHUB))

        repository.delete(ProviderId.GITHUB)
        assertNull(repository.readToken(ProviderId.GITHUB))
        assertEquals(
            ProviderCredentialAvailability.NOT_CONFIGURED,
            repository.statuses.value.getValue(ProviderId.GITHUB).availability,
        )
    }

    private class MemoryStore : ProviderCredentialStore {
        private val values = mutableMapOf<ProviderId, Pair<String, Instant>>()

        override fun write(provider: ProviderId, token: String, updatedAt: Instant) {
            validateProviderToken(token)
            values[provider] = token to updatedAt
        }

        override fun readToken(provider: ProviderId): String? = values[provider]?.first

        override fun inspect(provider: ProviderId): ProviderCredentialStatus = values[provider]?.let { (_, updatedAt) ->
            ProviderCredentialStatus(provider, ProviderCredentialAvailability.CONFIGURED, updatedAt.toString())
        } ?: ProviderCredentialStatus(provider, ProviderCredentialAvailability.NOT_CONFIGURED)

        override fun delete(provider: ProviderId) {
            values.remove(provider)
        }
    }

    private companion object {
        const val FIRST_TOKEN = "synthetic-first-token-never-send"
        const val REPLACEMENT_TOKEN = "synthetic-replacement-token-never-send"
    }
}
