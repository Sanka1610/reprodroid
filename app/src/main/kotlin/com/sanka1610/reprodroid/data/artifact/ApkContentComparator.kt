package com.sanka1610.reprodroid.data.artifact

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ApkComparisonException(
    val code: String,
    message: String,
) : RuntimeException(message)

data class ExpectedApkFile(
    val sizeBytes: Long,
    val sha256: String,
)

data class ComparedApkEntry(
    val entryName: String,
    val referenceSizeBytes: Long?,
    val localSizeBytes: Long?,
    val referenceSha256: String?,
    val localSha256: String?,
) {
    val result: String
        get() = when {
            referenceSha256 == null -> "ADDED"
            localSha256 == null -> "MISSING"
            referenceSizeBytes == localSizeBytes && referenceSha256 == localSha256 -> "MATCH"
            else -> "HASH_MISMATCH"
        }
}

data class ApkContentComparison(
    val isMatch: Boolean,
    val entries: List<ComparedApkEntry>,
)

class ApkContentComparator(
    private val maxArchiveEntries: Int = 100_000,
    private val maxComparedEntries: Int = 4_096,
    private val maxEntryUncompressedBytes: Long = 512L * 1024 * 1024,
    private val maxComparedUncompressedBytes: Long = 1024L * 1024 * 1024,
    private val maxArchiveDeclaredUncompressedBytes: Long = 4L * 1024 * 1024 * 1024,
) {
    fun compare(
        referenceApk: File,
        referenceRoot: File,
        expectedReference: ExpectedApkFile,
        localApk: File,
        localRoot: File,
        expectedLocal: ExpectedApkFile,
    ): ApkContentComparison {
        val reference = verifyInput(referenceApk.toPath(), referenceRoot.toPath(), expectedReference, "REFERENCE")
        val local = verifyInput(localApk.toPath(), localRoot.toPath(), expectedLocal, "LOCAL")
        val referenceEntries = readComparedEntries(reference, "REFERENCE")
        val localEntries = readComparedEntries(local, "LOCAL")
        if (ROOT_DEX_ENTRY !in referenceEntries || ROOT_DEX_ENTRY !in localEntries) {
            throw ApkComparisonException(
                "REQUIRED_DEX_MISSING",
                "Both APKs must contain classes.dex before their executable contents can be compared.",
            )
        }
        val compared = (referenceEntries.keys + localEntries.keys).toSortedSet().map { name ->
            val referenceEntry = referenceEntries[name]
            val localEntry = localEntries[name]
            ComparedApkEntry(
                entryName = name,
                referenceSizeBytes = referenceEntry?.sizeBytes,
                localSizeBytes = localEntry?.sizeBytes,
                referenceSha256 = referenceEntry?.sha256,
                localSha256 = localEntry?.sha256,
            )
        }
        return ApkContentComparison(
            isMatch = compared.all { it.result == "MATCH" },
            entries = compared,
        )
    }

    private fun verifyInput(path: Path, allowedRoot: Path, expected: ExpectedApkFile, label: String): Path {
        if (expected.sizeBytes <= 0 || !SHA256.matches(expected.sha256)) {
            throw ApkComparisonException("${label}_METADATA_INVALID", "$label APK metadata is invalid.")
        }
        if (Files.isSymbolicLink(path)) {
            throw ApkComparisonException("${label}_PATH_UNSAFE", "$label APK must not be a symbolic link.")
        }
        val rootReal = runCatching { allowedRoot.toRealPath() }.getOrNull()
            ?: throw ApkComparisonException("${label}_PATH_UNSAFE", "$label APK storage root does not exist.")
        val fileReal = runCatching { path.toRealPath() }.getOrNull()
            ?: throw ApkComparisonException("${label}_PATH_UNSAFE", "$label APK does not exist.")
        if (
            !fileReal.startsWith(rootReal) ||
            !Files.isRegularFile(fileReal, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(fileReal) != expected.sizeBytes
        ) {
            throw ApkComparisonException("${label}_PATH_UNSAFE", "$label APK escaped app-private storage or changed size.")
        }
        if (sha256(fileReal) != expected.sha256.lowercase()) {
            throw ApkComparisonException("${label}_SHA256_MISMATCH", "$label APK changed after it was verified.")
        }
        return fileReal
    }

    private fun readComparedEntries(apk: Path, label: String): Map<String, EntryDigest> {
        try {
            ZipFile(apk.toFile()).use { zip ->
                val seenNames = HashSet<String>()
                val selected = LinkedHashMap<String, EntryDigest>()
                var archiveEntryCount = 0
                var archiveDeclaredBytes = 0L
                var selectedActualBytes = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    archiveEntryCount++
                    if (archiveEntryCount > maxArchiveEntries) {
                        throw ApkComparisonException("ZIP_ENTRY_LIMIT_EXCEEDED", "$label APK contains too many ZIP entries.")
                    }
                    validateEntryName(entry.name, label)
                    if (!seenNames.add(entry.name)) {
                        throw ApkComparisonException("ZIP_DUPLICATE_ENTRY", "$label APK contains duplicate entry ${entry.name}.")
                    }
                    if (!entry.isDirectory) {
                        if (entry.size < 0) {
                            throw ApkComparisonException("ZIP_ENTRY_SIZE_UNKNOWN", "$label APK entry ${entry.name} has no declared size.")
                        }
                        archiveDeclaredBytes = checkedAdd(archiveDeclaredBytes, entry.size, "ZIP_DECLARED_SIZE_LIMIT_EXCEEDED")
                        if (archiveDeclaredBytes > maxArchiveDeclaredUncompressedBytes) {
                            throw ApkComparisonException(
                                "ZIP_DECLARED_SIZE_LIMIT_EXCEEDED",
                                "$label APK declares an excessive total uncompressed size.",
                            )
                        }
                    }
                    if (!isComparedEntry(entry)) continue
                    if (selected.size >= maxComparedEntries) {
                        throw ApkComparisonException("COMPARED_ENTRY_LIMIT_EXCEEDED", "$label APK contains too many executable entries.")
                    }
                    if (entry.size > maxEntryUncompressedBytes) {
                        throw ApkComparisonException("ZIP_ENTRY_SIZE_LIMIT_EXCEEDED", "$label APK entry ${entry.name} is too large.")
                    }
                    val entryDigest = digestEntry(zip, entry, label, selectedActualBytes)
                    selectedActualBytes = checkedAdd(
                        selectedActualBytes,
                        entryDigest.sizeBytes,
                        "COMPARED_SIZE_LIMIT_EXCEEDED",
                    )
                    if (selectedActualBytes > maxComparedUncompressedBytes) {
                        throw ApkComparisonException(
                            "COMPARED_SIZE_LIMIT_EXCEEDED",
                            "$label APK executable entries exceed the comparison expansion limit.",
                        )
                    }
                    selected[entry.name] = entryDigest
                }
                return selected
            }
        } catch (failure: ApkComparisonException) {
            throw failure
        } catch (failure: ZipException) {
            throw ApkComparisonException("MALFORMED_APK_ZIP", "$label APK ZIP structure is invalid: ${failure.message}")
        } catch (failure: IOException) {
            throw ApkComparisonException("APK_ZIP_READ_FAILED", "$label APK could not be read safely: ${failure.message}")
        }
    }

    private fun digestEntry(zip: ZipFile, entry: ZipEntry, label: String, previouslyRead: Long): EntryDigest {
        if (entry.method !in setOf(ZipEntry.STORED, ZipEntry.DEFLATED)) {
            throw ApkComparisonException("ZIP_METHOD_UNSUPPORTED", "$label APK entry ${entry.name} uses an unsupported method.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count = checkedAdd(count, read.toLong(), "ZIP_ENTRY_SIZE_LIMIT_EXCEEDED")
                if (count > maxEntryUncompressedBytes || previouslyRead + count > maxComparedUncompressedBytes) {
                    throw ApkComparisonException(
                        "ZIP_ENTRY_SIZE_LIMIT_EXCEEDED",
                        "$label APK entry ${entry.name} exceeded its safe expansion limit.",
                    )
                }
                digest.update(buffer, 0, read)
            }
        }
        if (count != entry.size) {
            throw ApkComparisonException("ZIP_ENTRY_SIZE_MISMATCH", "$label APK entry ${entry.name} did not match its declared size.")
        }
        return EntryDigest(count, digest.digest().toHex())
    }

    private fun validateEntryName(name: String, label: String) {
        val withoutDirectorySuffix = name.removeSuffix("/")
        val segments = withoutDirectorySuffix.split('/')
        if (
            name.isBlank() || name.length > MAX_ENTRY_NAME_LENGTH || name.startsWith('/') ||
            '\\' in name || name.any(Char::isISOControl) ||
            withoutDirectorySuffix.isBlank() || segments.any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw ApkComparisonException("ZIP_ENTRY_PATH_UNSAFE", "$label APK contains an unsafe ZIP entry path.")
        }
    }

    private fun isComparedEntry(entry: ZipEntry): Boolean =
        !entry.isDirectory && (DEX_ENTRY.matches(entry.name) || NATIVE_LIBRARY_ENTRY.matches(entry.name))

    private fun checkedAdd(left: Long, right: Long, code: String): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        throw ApkComparisonException(code, "APK ZIP size accounting overflowed.")
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class EntryDigest(val sizeBytes: Long, val sha256: String)

    private companion object {
        const val ROOT_DEX_ENTRY = "classes.dex"
        const val MAX_ENTRY_NAME_LENGTH = 1_024
        val SHA256 = Regex("[0-9a-f]{64}")
        val DEX_ENTRY = Regex("classes(?:[2-9][0-9]*)?\\.dex")
        val NATIVE_LIBRARY_ENTRY = Regex("lib/[^/]+/[^/]+\\.so")
    }
}
