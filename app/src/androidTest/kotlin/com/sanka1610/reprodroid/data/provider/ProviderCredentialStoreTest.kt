package com.sanka1610.reprodroid.data.provider

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: AndroidKeystoreProviderCredentialStore

    @Before
    fun setUp() {
        store = AndroidKeystoreProviderCredentialStore(context)
        ProviderId.entries.forEach(store::delete)
    }

    @After
    fun tearDown() {
        ProviderId.entries.forEach(store::delete)
    }

    @Test
    fun encryptedCredentialsSurviveReopenAndRemainProviderIsolated() {
        store.write(ProviderId.GITHUB, GITHUB_TOKEN, UPDATED_AT)
        store.write(ProviderId.CODEBERG, CODEBERG_TOKEN, UPDATED_AT)

        val reopened = AndroidKeystoreProviderCredentialStore(context)
        assertEquals(GITHUB_TOKEN, reopened.readToken(ProviderId.GITHUB))
        assertEquals(CODEBERG_TOKEN, reopened.readToken(ProviderId.CODEBERG))
        assertEquals(
            ProviderCredentialStatus(
                ProviderId.GITHUB,
                ProviderCredentialAvailability.CONFIGURED,
                UPDATED_AT.toString(),
            ),
            reopened.inspect(ProviderId.GITHUB),
        )

        reopened.delete(ProviderId.GITHUB)
        assertNull(reopened.readToken(ProviderId.GITHUB))
        assertEquals(CODEBERG_TOKEN, reopened.readToken(ProviderId.CODEBERG))
    }

    @Test
    fun replacementPublishesOnlyTheNewValue() {
        store.write(ProviderId.GITHUB, GITHUB_TOKEN, UPDATED_AT)
        store.write(ProviderId.GITHUB, REPLACEMENT_TOKEN, UPDATED_AT.plusSeconds(1))

        assertEquals(REPLACEMENT_TOKEN, store.readToken(ProviderId.GITHUB))
        assertEquals(UPDATED_AT.plusSeconds(1).toString(), store.inspect(ProviderId.GITHUB).updatedAt)
    }

    @Test
    fun tamperedEnvelopeFailsClosedAndMustBeDeletedBeforeReplacement() {
        store.write(ProviderId.GITHUB, GITHUB_TOKEN, UPDATED_AT)
        val envelope = context.noBackupFilesDir
            .resolve("provider-credentials")
            .resolve("github.credential.bin")
        Files.write(
            envelope.toPath(),
            byteArrayOf(0x52, 0x44, 0x00),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        assertEquals(ProviderCredentialAvailability.UNAVAILABLE, store.inspect(ProviderId.GITHUB).availability)
        assertThrows(ProviderCredentialException::class.java) { store.readToken(ProviderId.GITHUB) }
        assertThrows(ProviderCredentialException::class.java) {
            store.write(ProviderId.GITHUB, REPLACEMENT_TOKEN, UPDATED_AT.plusSeconds(1))
        }

        store.delete(ProviderId.GITHUB)
        store.write(ProviderId.GITHUB, REPLACEMENT_TOKEN, UPDATED_AT.plusSeconds(1))
        assertEquals(REPLACEMENT_TOKEN, store.readToken(ProviderId.GITHUB))
    }

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-09-16T00:00:00Z")
        const val GITHUB_TOKEN = "synthetic-github-instrumentation-token-never-send"
        const val CODEBERG_TOKEN = "synthetic-codeberg-instrumentation-token-never-send"
        const val REPLACEMENT_TOKEN = "synthetic-replacement-instrumentation-token-never-send"
    }
}
