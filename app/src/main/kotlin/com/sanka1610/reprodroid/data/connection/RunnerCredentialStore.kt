package com.sanka1610.reprodroid.data.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

@Serializable
internal class RunnerCredentialMaterial(
    val schemaVersion: Int = 1,
    val kind: String,
    val runnerId: String,
    val bindingId: String,
    val bearerToken: String,
    val continuationCredential: String? = null,
)

internal interface RunnerCredentialStore {
    fun write(reference: String, material: RunnerCredentialMaterial)
    fun read(reference: String, expectedRunnerId: String, expectedBindingId: String): RunnerCredentialMaterial
    fun delete(reference: String)
    fun pruneUnreferenced(references: Set<String>)
    fun retireKeyIfUnused()
}

internal class AndroidKeystoreRunnerCredentialStore(context: Context) : RunnerCredentialStore {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME).also { directory ->
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory || Files.isSymbolicLink(directory.toPath())) {
            throw CredentialStoreException("Runner credential directory is unavailable.")
        }
    }
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    override fun write(reference: String, material: RunnerCredentialMaterial) {
        validateReference(reference)
        val plaintext = json.encodeToString(material).toByteArray(Charsets.UTF_8)
        check(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Runner credential material exceeds its bound." }
        var iv = ByteArray(0)
        val encrypted = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                // Android Keystore must generate the IV when randomized encryption is required.
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                iv = this.iv?.clone() ?: throw CredentialStoreException("Runner credential IV was not generated.")
                if (iv.size != GCM_IV_BYTES) throw CredentialStoreException("Runner credential IV has an invalid size.")
                updateAAD(aad(material.runnerId, material.bindingId))
                doFinal(plaintext)
            }
        } catch (failure: Exception) {
            plaintext.fill(0)
            throw CredentialStoreException("Runner credential encryption failed.", failure)
        } finally {
            plaintext.fill(0)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(ENVELOPE_VERSION)
                data.writeInt(iv.size)
                data.writeInt(encrypted.size)
                data.write(iv)
                data.write(encrypted)
            }
            output.toByteArray()
        }
        writeAtomically(file(reference), bytes)
        encrypted.fill(0)
        bytes.fill(0)
    }

    override fun read(
        reference: String,
        expectedRunnerId: String,
        expectedBindingId: String,
    ): RunnerCredentialMaterial {
        validateReference(reference)
        val source = file(reference)
        if (!source.isFile || Files.isSymbolicLink(source.toPath()) || source.length() !in 1..MAX_ENVELOPE_BYTES.toLong()) {
            throw CredentialStoreException("Runner credential envelope is missing or invalid.")
        }
        val (iv, encrypted) = try {
            val content = Files.newByteChannel(source.toPath(), setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
                val bytes = ByteBuffer.allocate(MAX_ENVELOPE_BYTES + 1)
                while (bytes.hasRemaining() && channel.read(bytes) >= 0) { /* Count actual bytes, not a raced length. */ }
                if (bytes.position() > MAX_ENVELOPE_BYTES) throw CredentialStoreException("Runner credential envelope is oversized.")
                bytes.array().copyOf(bytes.position())
            }
            DataInputStream(ByteArrayInputStream(content)).use { data ->
                val magic = ByteArray(MAGIC.size).also(data::readFully)
                val version = data.readInt()
                val ivSize = data.readInt()
                val encryptedSize = data.readInt()
                if (!magic.contentEquals(MAGIC) || version != ENVELOPE_VERSION || ivSize != GCM_IV_BYTES ||
                    encryptedSize !in (GCM_TAG_BITS / 8)..MAX_ENVELOPE_BYTES
                ) {
                    throw CredentialStoreException("Runner credential envelope header is invalid.")
                }
                val iv = ByteArray(ivSize).also(data::readFully)
                val encrypted = ByteArray(encryptedSize).also(data::readFully)
                if (data.read() != -1) throw CredentialStoreException("Runner credential envelope has trailing data.")
                iv to encrypted
            }
        } catch (failure: CredentialStoreException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialStoreException("Runner credential envelope is truncated.", failure)
        }
        val plaintext = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, requireExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(aad(expectedRunnerId, expectedBindingId))
                doFinal(encrypted)
            }
        } catch (failure: Exception) {
            throw CredentialStoreException("Runner credential authentication failed.", failure)
        } finally {
            encrypted.fill(0)
        }
        return try {
            if (plaintext.size > MAX_PLAINTEXT_BYTES) throw CredentialStoreException("Runner credential plaintext is oversized.")
            json.decodeFromString<RunnerCredentialMaterial>(plaintext.toString(Charsets.UTF_8)).also { material ->
                if (
                    material.schemaVersion != 1 || material.runnerId != expectedRunnerId ||
                    material.bindingId != expectedBindingId || material.kind !in setOf(KIND_PENDING, KIND_ACTIVE) ||
                    !BEARER.matches(material.bearerToken) ||
                    !canonicalSecret(material.bearerToken.substringAfterLast('.')) ||
                    (material.kind == KIND_PENDING && material.continuationCredential?.matches(CONTINUATION) != true) ||
                    (material.kind == KIND_PENDING && !canonicalSecret(material.continuationCredential.orEmpty().substringAfterLast('.'))) ||
                    (material.kind == KIND_ACTIVE && material.continuationCredential != null)
                ) {
                    throw CredentialStoreException("Runner credential binding does not match Room metadata.")
                }
            }
        } catch (failure: CredentialStoreException) {
            throw failure
        } catch (failure: Exception) {
            throw CredentialStoreException("Runner credential plaintext is invalid.", failure)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun delete(reference: String) {
        validateReference(reference)
        val target = file(reference)
        if (Files.isSymbolicLink(target.toPath())) throw CredentialStoreException("Refusing a symbolic-link credential file.")
        if (target.exists() && !target.delete()) throw CredentialStoreException("Runner credential file could not be deleted.")
    }

    override fun pruneUnreferenced(references: Set<String>) {
        if (Files.isSymbolicLink(directory.toPath())) throw CredentialStoreException("Refusing a symbolic-link credential directory.")
        val entries = directory.listFiles() ?: throw CredentialStoreException("Runner credential directory could not be read.")
        entries.filter { REFERENCE.matches(it.name) && it.name !in references }.forEach {
            // Files.delete unlinks a symlink itself and never follows it to another credential or directory.
            Files.deleteIfExists(it.toPath())
        }
    }

    private fun canonicalSecret(secret: String): Boolean = runCatching {
        val decoded = Base64.getUrlDecoder().decode(secret)
        decoded.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == secret
    }.getOrDefault(false)

    override fun retireKeyIfUnused() = synchronized(KEY_LOCK) {
        if (Files.isSymbolicLink(directory.toPath())) throw CredentialStoreException("Refusing a symbolic-link credential directory.")
        val entries = directory.listFiles() ?: throw CredentialStoreException("Runner credential directory could not be read.")
        if (entries.any { it.name.endsWith(".bin") }) return@synchronized
        // Only explicit final local deletion calls this. Removing an unusable alias permits a fresh
        // pairing key, and the empty-envelope gate prevents invalidating any retained credential.
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val existing = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@synchronized existing
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private fun requireExistingKey(): SecretKey =
        keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw CredentialStoreException("Runner credential key is missing; re-pairing is required.")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun aad(runnerId: String, bindingId: String): ByteArray =
        "$AAD_VERSION\u0000$runnerId\u0000$bindingId".toByteArray(Charsets.UTF_8)

    private fun file(reference: String): File = File(directory, reference).also { target ->
        if (Files.isSymbolicLink(directory.toPath()) || target.parentFile?.canonicalFile != directory.canonicalFile) {
            throw CredentialStoreException("Runner credential path escapes its private directory.")
        }
    }

    private fun validateReference(reference: String) {
        if (!REFERENCE.matches(reference)) throw CredentialStoreException("Runner credential reference is invalid.")
    }

    private fun writeAtomically(target: File, content: ByteArray) {
        val temporary = File.createTempFile(".${target.name}.", ".tmp", directory)
        try {
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { output ->
                val bytes = ByteBuffer.wrap(content)
                while (bytes.hasRemaining()) output.write(bytes)
                output.force(true)
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                throw CredentialStoreException("Atomic credential publication is unavailable.")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    companion object {
        const val KIND_PENDING = "PENDING"
        const val KIND_ACTIVE = "ACTIVE"
        private const val DIRECTORY_NAME = "runner-credentials"
        private const val KEY_ALIAS = "reprodroid.runner.credentials.aes256.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val ENVELOPE_VERSION = 1
        private const val AAD_VERSION = "reprodroid-runner-credential-v1"
        private const val MAX_PLAINTEXT_BYTES = 2 * 1024
        private const val MAX_ENVELOPE_BYTES = 4 * 1024
        private val MAGIC = "RDCRED01".toByteArray(Charsets.US_ASCII)
        private val KEY_LOCK = Any()
        private val REFERENCE = Regex("[0-9a-f-]{36}\\.(pending|credential)\\.bin")
        private val BEARER = Regex("rdb1\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}")
        private val CONTINUATION = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}")
    }
}
