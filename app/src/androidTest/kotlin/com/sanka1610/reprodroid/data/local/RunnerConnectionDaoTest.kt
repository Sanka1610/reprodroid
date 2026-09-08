package com.sanka1610.reprodroid.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunnerConnectionDaoTest {
    private lateinit var database: ReproDroidDatabase
    private lateinit var dao: RunnerConnectionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ReproDroidDatabase::class.java,
        ).build()
        dao = database.runnerConnectionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun activationDeactivatesPreviousRunnerAndObserveOrderIsStable() = runBlocking {
        val first = connection("runner-a", active = true, updatedAt = "2026-09-07T00:01:00Z")
        val second = connection("runner-b", active = false, updatedAt = "2026-09-07T00:02:00Z")
        dao.upsertConnection(first)
        dao.upsertConnection(second)

        dao.activate(second)

        assertEquals(listOf("runner-b"), dao.getActiveConnections().map { it.runnerId })
        assertFalse(requireNotNull(dao.getConnection("runner-a")).active)
        assertTrue(requireNotNull(dao.getConnection("runner-b")).active)
        assertEquals(
            listOf("runner-b", "runner-a"),
            dao.observeConnections().first().map { it.runnerId },
        )
    }

    @Test
    fun upsertAndDeletePreserveOnlyNonSecretConnectionMetadata() = runBlocking {
        val pending = connection(
            runnerId = "runner-pending",
            principalId = null,
            transportMode = RunnerTransportMode.PAIRED_HTTPS.name,
            pairingState = RunnerPairingState.PENDING_APPROVAL.name,
            revocationKnowledge = RunnerRevocationKnowledge.UNKNOWN.name,
            pairingRequestId = "request-46",
            confirmationFingerprint = "fingerprint-46",
            pairingExpiresAt = "2026-09-07T00:05:00Z",
        )
        dao.upsertConnection(pending)

        assertEquals(pending, dao.getConnection("runner-pending"))
        assertNull(requireNotNull(dao.getConnection("runner-pending")).principalId)

        val columns = buildSet {
            database.openHelper.readableDatabase.query("PRAGMA table_info('runner_connections')").use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertFalse(columns.any { it.contains("Secret", ignoreCase = true) })
        assertFalse(columns.any { it.contains("Token", ignoreCase = true) })
        assertFalse(columns.any { it.contains("Invitation", ignoreCase = true) })

        dao.deleteConnection("runner-pending")
        assertNull(dao.getConnection("runner-pending"))
    }

    private fun connection(
        runnerId: String,
        endpoint: String = "https://127.0.0.1:8443",
        principalId: String? = "principal-$runnerId",
        displayName: String = runnerId,
        transportMode: String = RunnerTransportMode.PAIRED_HTTPS.name,
        pairingRequestId: String? = null,
        pairingState: String = RunnerPairingState.APPROVED.name,
        confirmationFingerprint: String? = null,
        pairingExpiresAt: String? = null,
        revocationKnowledge: String = RunnerRevocationKnowledge.ACTIVE.name,
        active: Boolean = false,
        updatedAt: String = "2026-09-07T00:00:00Z",
    ) = RunnerConnectionEntity(
        runnerId = runnerId,
        endpoint = endpoint,
        rootSpkiSha256 = "a".repeat(64),
        caCertificateFileReference = "runner/$runnerId/root.der",
        caCertificateSha256 = "b".repeat(64),
        credentialFileReference = "runner/$runnerId/credential.bin",
        principalId = principalId,
        displayName = displayName,
        transportMode = transportMode,
        pairingRequestId = pairingRequestId,
        pairingState = pairingState,
        confirmationFingerprint = confirmationFingerprint,
        pairingExpiresAt = pairingExpiresAt,
        revocationKnowledge = revocationKnowledge,
        active = active,
        createdAt = "2026-09-07T00:00:00Z",
        updatedAt = updatedAt,
    )
}
