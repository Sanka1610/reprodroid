package com.sanka1610.reprodroid.data.connection

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class RunnerCertificateStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME).also {
        if ((!it.exists() && !it.mkdirs()) || !it.isDirectory || Files.isSymbolicLink(it.toPath())) {
            throw CredentialStoreException("Runner certificate directory is unavailable.")
        }
    }

    fun write(reference: String, certificateDer: ByteArray): String {
        validateReference(reference)
        if (certificateDer.size !in 1..MAX_CERTIFICATE_BYTES) {
            throw CredentialStoreException("Runner root certificate is invalid or oversized.")
        }
        val target = file(reference)
        val temporary = File.createTempFile(".${target.name}.", ".tmp", directory)
        try {
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use {
                val bytes = ByteBuffer.wrap(certificateDer)
                while (bytes.hasRemaining()) it.write(bytes)
                it.force(true)
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                throw CredentialStoreException("Atomic Runner certificate publication is unavailable.")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return sha256(certificateDer)
    }

    fun read(reference: String, expectedSha256: String): ByteArray {
        validateReference(reference)
        val target = file(reference)
        if (!target.isFile || Files.isSymbolicLink(target.toPath()) || target.length() !in 1..MAX_CERTIFICATE_BYTES.toLong()) {
            throw CredentialStoreException("Runner root certificate is missing or invalid.")
        }
        val bytes = Files.newByteChannel(target.toPath(), setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
            val content = ByteBuffer.allocate(MAX_CERTIFICATE_BYTES + 1)
            while (content.hasRemaining() && channel.read(content) >= 0) { /* Bound actual bytes. */ }
            if (content.position() !in 1..MAX_CERTIFICATE_BYTES) throw CredentialStoreException("Runner root certificate is oversized.")
            content.array().copyOf(content.position())
        }
        if (!MessageDigest.isEqual(sha256(bytes).toByteArray(), expectedSha256.toByteArray())) {
            bytes.fill(0)
            throw CredentialStoreException("Runner root certificate digest does not match Room metadata.")
        }
        return bytes
    }

    fun delete(reference: String) {
        validateReference(reference)
        val target = file(reference)
        if (Files.isSymbolicLink(target.toPath())) throw CredentialStoreException("Refusing a symbolic-link certificate file.")
        if (target.exists() && !target.delete()) throw CredentialStoreException("Runner certificate file could not be deleted.")
    }

    fun pruneUnreferenced(references: Set<String>) {
        if (Files.isSymbolicLink(directory.toPath())) throw CredentialStoreException("Refusing a symbolic-link certificate directory.")
        val entries = directory.listFiles() ?: throw CredentialStoreException("Runner certificate directory could not be read.")
        entries.filter { REFERENCE.matches(it.name) && it.name !in references }.forEach { Files.deleteIfExists(it.toPath()) }
    }

    private fun file(reference: String): File = File(directory, reference).also {
        if (Files.isSymbolicLink(directory.toPath()) || it.parentFile?.canonicalFile != directory.canonicalFile) {
            throw CredentialStoreException("Runner certificate path escapes its private directory.")
        }
    }

    private fun validateReference(reference: String) {
        if (!REFERENCE.matches(reference)) throw CredentialStoreException("Runner certificate reference is invalid.")
    }

    companion object {
        private const val DIRECTORY_NAME = "runner-certificates"
        private const val MAX_CERTIFICATE_BYTES = 16 * 1024
        private val REFERENCE = Regex("[0-9a-f-]{36}\\.(pending-root-ca|root-ca)\\.der")
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
