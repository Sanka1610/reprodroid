package com.sanka1610.reprodroid.data.provider

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ProviderId {
    GITHUB,
    CODEBERG,
}

enum class ProviderCredentialAvailability {
    NOT_CONFIGURED,
    CONFIGURED,
    UNAVAILABLE,
}

data class ProviderCredentialStatus(
    val provider: ProviderId,
    val availability: ProviderCredentialAvailability,
    val updatedAt: String? = null,
)

class ProviderCredentialException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal fun interface ProviderCredentialSource {
    fun readToken(provider: ProviderId): String?
}

internal interface ProviderCredentialStore : ProviderCredentialSource {
    fun write(provider: ProviderId, token: String, updatedAt: Instant)
    fun inspect(provider: ProviderId): ProviderCredentialStatus
    fun delete(provider: ProviderId)
}

@Serializable
private data class ProviderCredentialMaterial(
    val schemaVersion: Int = 1,
    val provider: String,
    val updatedAt: String,
    val token: String,
)

internal class AndroidKeystoreProviderCredentialStore(context: Context) : ProviderCredentialStore {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    override fun write(provider: ProviderId, token: String, updatedAt: Instant) = synchronized(lockFor(provider)) {
        validateProviderToken(token)
        ensureDirectory()
        val existing = inspect(provider)
        if (existing.availability == ProviderCredentialAvailability.UNAVAILABLE) {
            throw ProviderCredentialException("Delete the unavailable provider credential before replacing it.")
        }
        val material = ProviderCredentialMaterial(
            provider = provider.name,
            updatedAt = updatedAt.toString(),
            token = token,
        )
        val plaintext = json.encodeToString(material).toByteArray(Charsets.UTF_8)
        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            plaintext.fill(0)
            throw ProviderCredentialException("Provider credential material exceeds its size limit.")
        }
        var iv = ByteArray(0)
        val encrypted = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey(provider))
                iv = this.iv?.clone() ?: throw ProviderCredentialException("Provider credential IV was not generated.")
                if (iv.size != GCM_IV_BYTES) throw ProviderCredentialException("Provider credential IV has an invalid size.")
                updateAAD(aad(provider))
                doFinal(plaintext)
            }
        } catch (failure: ProviderCredentialException) {
            throw failure
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential encryption failed.", failure)
        } finally {
            plaintext.fill(0)
        }
        val envelope = ByteArrayOutputStream().use { output ->
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
        try {
            writeAtomically(file(provider), envelope)
        } finally {
            encrypted.fill(0)
            envelope.fill(0)
        }
    }

    override fun readToken(provider: ProviderId): String? = synchronized(lockFor(provider)) {
        val target = file(provider)
        if (!envelopeExists(target)) return@synchronized null
        readMaterial(provider).token
    }

    override fun inspect(provider: ProviderId): ProviderCredentialStatus = synchronized(lockFor(provider)) {
        val target = runCatching { file(provider) }.getOrElse {
            return@synchronized ProviderCredentialStatus(provider, ProviderCredentialAvailability.UNAVAILABLE)
        }
        if (!envelopeExists(target)) {
            return@synchronized ProviderCredentialStatus(provider, ProviderCredentialAvailability.NOT_CONFIGURED)
        }
        runCatching { readMaterial(provider) }.fold(
            onSuccess = {
                ProviderCredentialStatus(
                    provider = provider,
                    availability = ProviderCredentialAvailability.CONFIGURED,
                    updatedAt = it.updatedAt,
                )
            },
            onFailure = { ProviderCredentialStatus(provider, ProviderCredentialAvailability.UNAVAILABLE) },
        )
    }

    override fun delete(provider: ProviderId) = synchronized(lockFor(provider)) {
        ensureDirectory()
        val target = file(provider)
        try {
            Files.deleteIfExists(target.toPath())
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential file could not be deleted.", failure)
        }
        runCatching { keyStore().deleteEntry(keyAlias(provider)) }
        Unit
    }

    private fun readMaterial(provider: ProviderId): ProviderCredentialMaterial {
        ensureDirectory()
        val source = file(provider)
        if (!source.isFile || Files.isSymbolicLink(source.toPath())) {
            throw ProviderCredentialException("Provider credential envelope is missing or invalid.")
        }
        val content = try {
            Files.newByteChannel(source.toPath(), setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
                val buffer = ByteBuffer.allocate(MAX_ENVELOPE_BYTES + 1)
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* Bound the actual read. */ }
                if (buffer.position() > MAX_ENVELOPE_BYTES) {
                    throw ProviderCredentialException("Provider credential envelope is oversized.")
                }
                buffer.array().copyOf(buffer.position())
            }
        } catch (failure: ProviderCredentialException) {
            throw failure
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential envelope could not be read.", failure)
        }
        val (iv, encrypted) = try {
            DataInputStream(ByteArrayInputStream(content)).use { data ->
                val magic = ByteArray(MAGIC.size).also(data::readFully)
                val version = data.readInt()
                val ivSize = data.readInt()
                val encryptedSize = data.readInt()
                if (
                    !magic.contentEquals(MAGIC) || version != ENVELOPE_VERSION || ivSize != GCM_IV_BYTES ||
                    encryptedSize !in (GCM_TAG_BITS / 8)..MAX_ENVELOPE_BYTES
                ) {
                    throw ProviderCredentialException("Provider credential envelope header is invalid.")
                }
                val parsedIv = ByteArray(ivSize).also(data::readFully)
                val parsedEncrypted = ByteArray(encryptedSize).also(data::readFully)
                if (data.read() != -1) throw ProviderCredentialException("Provider credential envelope has trailing data.")
                parsedIv to parsedEncrypted
            }
        } catch (failure: ProviderCredentialException) {
            throw failure
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential envelope is truncated.", failure)
        } finally {
            content.fill(0)
        }
        val plaintext = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, requireExistingKey(provider), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(aad(provider))
                doFinal(encrypted)
            }
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential authentication failed.", failure)
        } finally {
            iv.fill(0)
            encrypted.fill(0)
        }
        return try {
            if (plaintext.size > MAX_PLAINTEXT_BYTES) {
                throw ProviderCredentialException("Provider credential plaintext is oversized.")
            }
            json.decodeFromString<ProviderCredentialMaterial>(plaintext.toString(Charsets.UTF_8)).also { material ->
                if (
                    material.schemaVersion != MATERIAL_VERSION || material.provider != provider.name ||
                    runCatching { Instant.parse(material.updatedAt) }.isFailure
                ) {
                    throw ProviderCredentialException("Provider credential metadata is invalid.")
                }
                validateProviderToken(material.token)
            }
        } catch (failure: ProviderCredentialException) {
            throw failure
        } catch (failure: Exception) {
            throw ProviderCredentialException("Provider credential plaintext is invalid.", failure)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun ensureDirectory() {
        if (Files.isSymbolicLink(directory.toPath())) {
            throw ProviderCredentialException("Provider credential directory must not be a symbolic link.")
        }
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
            throw ProviderCredentialException("Provider credential directory is unavailable.")
        }
    }

    private fun file(provider: ProviderId): File = File(directory, "${provider.name.lowercase()}.credential.bin").also { target ->
        if (
            Files.isSymbolicLink(directory.toPath()) || (directory.exists() && !directory.isDirectory) ||
            target.parentFile?.canonicalFile != directory.canonicalFile
        ) {
            throw ProviderCredentialException("Provider credential path escapes its private directory.")
        }
    }

    private fun envelopeExists(target: File): Boolean =
        Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun getOrCreateKey(provider: ProviderId): SecretKey {
        val existing = keyStore().getKey(keyAlias(provider), null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias(provider),
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

    private fun requireExistingKey(provider: ProviderId): SecretKey =
        keyStore().getKey(keyAlias(provider), null) as? SecretKey
            ?: throw ProviderCredentialException("Provider credential key is missing; delete and configure the token again.")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keyAlias(provider: ProviderId): String = "$KEY_ALIAS_PREFIX.${provider.name.lowercase()}.v1"

    private fun aad(provider: ProviderId): ByteArray = "$AAD_VERSION\u0000${provider.name}".toByteArray(Charsets.UTF_8)

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
                throw ProviderCredentialException("Atomic provider credential publication is unavailable.")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun lockFor(provider: ProviderId): Any = LOCKS.getValue(provider)

    companion object {
        private const val DIRECTORY_NAME = "provider-credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "reprodroid.provider.credentials"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AAD_VERSION = "reprodroid-provider-credential-v1"
        private const val MATERIAL_VERSION = 1
        private const val ENVELOPE_VERSION = 1
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_PLAINTEXT_BYTES = 4 * 1024
        private const val MAX_ENVELOPE_BYTES = 8 * 1024
        private val MAGIC = "RDPROV01".toByteArray(Charsets.US_ASCII)
        private val LOCKS = ProviderId.entries.associateWith { Any() }
    }
}

internal class ProviderCredentialRepository(
    private val store: ProviderCredentialStore,
    private val clock: Clock = Clock.systemUTC(),
) : ProviderCredentialSource {
    private val mutableStatuses = MutableStateFlow(loadStatuses())
    val statuses = mutableStatuses.asStateFlow()

    override fun readToken(provider: ProviderId): String? = store.readToken(provider)

    fun save(provider: ProviderId, token: String) {
        store.write(provider, token, clock.instant())
        refresh(provider)
    }

    fun delete(provider: ProviderId) {
        store.delete(provider)
        refresh(provider)
    }

    private fun loadStatuses(): Map<ProviderId, ProviderCredentialStatus> =
        ProviderId.entries.associateWith(store::inspect)

    private fun refresh(provider: ProviderId) {
        val status = store.inspect(provider)
        mutableStatuses.update { current -> current + (provider to status) }
    }
}

internal fun validateProviderToken(token: String) {
    val byteCount = token.toByteArray(Charsets.UTF_8).size
    if (
        byteCount !in 1..MAX_PROVIDER_TOKEN_BYTES || token != token.trim() ||
        token.any { it.code !in 0x21..0x7e }
    ) {
        throw ProviderCredentialException("Provider token must contain only bounded printable ASCII without whitespace.")
    }
}

internal const val MAX_PROVIDER_TOKEN_BYTES = 2 * 1024
