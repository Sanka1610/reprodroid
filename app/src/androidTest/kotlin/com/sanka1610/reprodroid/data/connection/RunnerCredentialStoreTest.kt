package com.sanka1610.reprodroid.data.connection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanka1610.reprodroid.ReproDroidApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the Android Keystore-backed credential envelope.
 *
 * JVM tests cannot provide the AndroidKeyStore provider.  These checks run on
 * the API 36 emulator and exercise the real AES-256-GCM key, provider-generated
 * IV, AAD binding, atomic file publication, and key-loss behavior.
 */
@RunWith(AndroidJUnit4::class)
class RunnerCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: RunnerCredentialStore
    private val references = mutableSetOf<String>()

    @Before
    fun setUp() {
        // App startup prunes unreferenced generations. Finish that recovery
        // before publishing test-only credentials that Room intentionally does not reference.
        runBlocking {
            withTimeout(10_000) {
                (context.applicationContext as ReproDroidApplication).runnerConnectionRepository.status
                    .first { it.phase != RunnerConnectionPhase.INITIALIZING }
            }
        }
        store = AndroidKeystoreRunnerCredentialStore(context)
    }

    @After
    fun tearDown() {
        references.forEach { reference ->
            runCatching { store.delete(reference) }
        }
        deleteCredentialKey()
    }

    @Test
    fun writeReadAndReopenRoundTripUsesEncryptedPrivateEnvelope() {
        val reference = newReference("credential")
        val material = activeMaterial()

        store.write(reference, material)
        val envelope = credentialFile(reference).readBytes()

        assertTrue(credentialFile(reference).isFile)
        assertTrue(envelope.size > MAGIC.size + HEADER_BYTES)
        assertFalse(envelope.decodeToString().contains(material.bearerToken))
        assertTrue(credentialDirectory().listFiles()?.none { it.name.endsWith(".tmp") } == true)
        assertMaterialEquals(material, store.read(reference, RUNNER_ID, PRINCIPAL_ID))

        // A new store instance represents process restart; the same Keystore
        // key and durable envelope must remain readable without plaintext state.
        val reopened = AndroidKeystoreRunnerCredentialStore(context)
        assertMaterialEquals(material, reopened.read(reference, RUNNER_ID, PRINCIPAL_ID))
    }

    @Test
    fun eachWriteUsesAProviderGeneratedIvAndKeepsTheCiphertextOpaque() {
        val firstReference = newReference("credential")
        val secondReference = newReference("credential")
        val material = activeMaterial()

        store.write(firstReference, material)
        store.write(secondReference, material)

        val first = credentialFile(firstReference).readBytes()
        val second = credentialFile(secondReference).readBytes()
        assertFalse(first.contentEquals(second))
        assertFalse(first.decodeToString().contains(material.bearerToken))
        assertFalse(second.decodeToString().contains(material.bearerToken))
    }

    @Test
    fun authenticationTagTamperingAndTruncationFailClosed() {
        val tamperedReference = newReference("credential")
        store.write(tamperedReference, activeMaterial())
        val tampered = credentialFile(tamperedReference).readBytes()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        credentialFile(tamperedReference).writeBytes(tampered)

        assertThrows(CredentialStoreException::class.java) {
            store.read(tamperedReference, RUNNER_ID, PRINCIPAL_ID)
        }

        val truncatedReference = newReference("pending")
        store.write(truncatedReference, pendingMaterial())
        val truncated = credentialFile(truncatedReference).readBytes()
        credentialFile(truncatedReference).writeBytes(truncated.copyOf(truncated.size - 1))

        assertThrows(CredentialStoreException::class.java) {
            store.read(truncatedReference, RUNNER_ID, PENDING_BINDING_ID)
        }
    }

    @Test
    fun missingKeystoreKeyFailsClosedWithoutPlaintextFallback() {
        val reference = newReference("credential")
        store.write(reference, activeMaterial())
        deleteCredentialKey()

        assertThrows(CredentialStoreException::class.java) {
            store.read(reference, RUNNER_ID, PRINCIPAL_ID)
        }
        assertTrue(credentialFile(reference).isFile)
    }

    @Test
    fun aadBindingRejectsWrongRunnerOrPrincipal() {
        val reference = newReference("credential")
        store.write(reference, activeMaterial())

        assertThrows(CredentialStoreException::class.java) {
            store.read(reference, "00000000-0000-4000-8000-000000000099", PRINCIPAL_ID)
        }
        assertThrows(CredentialStoreException::class.java) {
            store.read(reference, RUNNER_ID, "00000000-0000-4000-8000-000000000099")
        }
    }

    @Test
    fun envelopeHeaderTrailingBytesAndPathReferencesAreRejected() {
        val reference = newReference("credential")
        store.write(reference, activeMaterial())
        val file = credentialFile(reference)
        file.appendBytes(byteArrayOf(0x01))

        assertThrows(CredentialStoreException::class.java) {
            store.read(reference, RUNNER_ID, PRINCIPAL_ID)
        }

        assertThrows(CredentialStoreException::class.java) {
            store.write(
                "../escape.credential.bin",
                activeMaterial(),
            )
        }
        assertFalse(File(context.noBackupFilesDir, "escape.credential.bin").exists())
    }

    @Test
    fun deleteRemovesCredentialWithoutTouchingOtherReferences() {
        val first = newReference("credential")
        val second = newReference("credential")
        store.write(first, activeMaterial())
        store.write(second, activeMaterial())

        store.delete(first)

        assertFalse(credentialFile(first).exists())
        assertTrue(credentialFile(second).isFile)
        assertMaterialEquals(activeMaterial(), store.read(second, RUNNER_ID, PRINCIPAL_ID))
    }

    @Test
    fun processDeathOrphanPruningPreservesEveryRoomReferencedCredential() {
        val retainedBeforeTest = credentialDirectory().listFiles().orEmpty().map { it.name }.toSet()
        val active = newReference("credential")
        val pendingOrphan = newReference("pending")
        store.write(active, activeMaterial())
        store.write(pendingOrphan, pendingMaterial())

        AndroidKeystoreRunnerCredentialStore(context).pruneUnreferenced(retainedBeforeTest + active)

        assertFalse(credentialFile(pendingOrphan).exists())
        assertTrue(credentialFile(active).isFile)
        assertMaterialEquals(activeMaterial(), store.read(active, RUNNER_ID, PRINCIPAL_ID))
        retainedBeforeTest.forEach { assertTrue(credentialFile(it).exists()) }
    }

    @Test
    fun keyRetirementRequiresEveryCredentialEnvelopeToBeGone() {
        assertTrue("This key-lifecycle test requires the approved disposable app state",
            credentialDirectory().listFiles().orEmpty().none { it.name.endsWith(".bin") })
        val reference = newReference("credential")
        store.write(reference, activeMaterial())
        store.retireKeyIfUnused()
        assertTrue(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(KEY_ALIAS))
        assertMaterialEquals(activeMaterial(), store.read(reference, RUNNER_ID, PRINCIPAL_ID))

        store.delete(reference)
        store.retireKeyIfUnused()

        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(KEY_ALIAS))
    }

    private fun activeMaterial() = RunnerCredentialMaterial(
        kind = AndroidKeystoreRunnerCredentialStore.KIND_ACTIVE,
        runnerId = RUNNER_ID,
        bindingId = PRINCIPAL_ID,
        bearerToken = "rdb1.$TOKEN_ID.${"A".repeat(43)}",
        continuationCredential = null,
    )

    private fun pendingMaterial() = RunnerCredentialMaterial(
        kind = AndroidKeystoreRunnerCredentialStore.KIND_PENDING,
        runnerId = RUNNER_ID,
        bindingId = PENDING_BINDING_ID,
        bearerToken = "rdb1.$TOKEN_ID.${"A".repeat(43)}",
        continuationCredential = "$CONTINUATION_ID.${"A".repeat(43)}",
    )

    private fun assertMaterialEquals(
        expected: RunnerCredentialMaterial,
        actual: RunnerCredentialMaterial,
    ) {
        // Compare fields without printing the material, because assertion
        // output must never become a credential disclosure channel.
        assertEquals(expected.schemaVersion, actual.schemaVersion)
        assertEquals(expected.kind, actual.kind)
        assertEquals(expected.runnerId, actual.runnerId)
        assertEquals(expected.bindingId, actual.bindingId)
        assertTrue("Bearer round-trip must preserve the encrypted value", expected.bearerToken == actual.bearerToken)
        assertTrue("Continuation round-trip must preserve the encrypted value", expected.continuationCredential == actual.continuationCredential)
    }

    private fun newReference(kind: String): String =
        UUID.randomUUID().toString().also { id -> references += "$id.$kind.bin" }.let { id ->
            "$id.$kind.bin"
        }

    private fun credentialDirectory() = File(context.noBackupFilesDir, "runner-credentials")

    private fun credentialFile(reference: String) = File(credentialDirectory(), reference)

    private fun deleteCredentialKey() {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val RUNNER_ID = "00000000-0000-4000-8000-000000000001"
        const val PRINCIPAL_ID = "00000000-0000-4000-8000-000000000002"
        const val PENDING_BINDING_ID = "00000000-0000-4000-8000-000000000003"
        const val TOKEN_ID = "00000000-0000-4000-8000-000000000004"
        const val CONTINUATION_ID = "00000000-0000-4000-8000-000000000005"
        const val KEY_ALIAS = "reprodroid.runner.credentials.aes256.v1"
        val MAGIC = "RDCRED01".toByteArray(Charsets.US_ASCII)
        const val HEADER_BYTES = 12
    }
}
