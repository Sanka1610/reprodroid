package com.sanka1610.reprodroid.data.artifact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkContentComparatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val comparator = ApkContentComparator()

    @Test
    fun `signature entries and ZIP metadata do not affect executable content match`() {
        val referenceRoot = temporaryFolder.newFolder("reference")
        val localRoot = temporaryFolder.newFolder("local")
        val reference = zip(
            File(referenceRoot, "reference.apk"),
            linkedMapOf(
                "META-INF/CERT.RSA" to "official-signature".toByteArray(),
                "classes.dex" to "same-dex".toByteArray(),
                "lib/arm64-v8a/libsame.so" to "same-native".toByteArray(),
            ),
        )
        val local = zip(
            File(localRoot, "local.apk"),
            linkedMapOf(
                "classes.dex" to "same-dex".toByteArray(),
                "lib/arm64-v8a/libsame.so" to "same-native".toByteArray(),
                "META-INF/CERT.RSA" to "local-signature".toByteArray(),
            ),
        )

        val result = compare(reference, referenceRoot, local, localRoot)

        assertTrue(result.isMatch)
        assertEquals(listOf("classes.dex", "lib/arm64-v8a/libsame.so"), result.entries.map { it.entryName })
    }

    @Test
    fun `changed added and missing executable entries are reported as different`() {
        val referenceRoot = temporaryFolder.newFolder("reference-different")
        val localRoot = temporaryFolder.newFolder("local-different")
        val reference = zip(
            File(referenceRoot, "reference.apk"),
            linkedMapOf(
                "classes.dex" to "reference".toByteArray(),
                "classes2.dex" to "missing-local".toByteArray(),
            ),
        )
        val local = zip(
            File(localRoot, "local.apk"),
            linkedMapOf(
                "classes.dex" to "local".toByteArray(),
                "lib/x86_64/libadded.so" to "added-local".toByteArray(),
            ),
        )

        val result = compare(reference, referenceRoot, local, localRoot)

        assertFalse(result.isMatch)
        assertEquals(
            mapOf(
                "classes.dex" to "HASH_MISMATCH",
                "classes2.dex" to "MISSING",
                "lib/x86_64/libadded.so" to "ADDED",
            ),
            result.entries.associate { it.entryName to it.result },
        )
    }

    @Test
    fun `unsafe ZIP path is rejected even when it is outside comparison scope`() {
        val referenceRoot = temporaryFolder.newFolder("reference-traversal")
        val localRoot = temporaryFolder.newFolder("local-traversal")
        val reference = zip(
            File(referenceRoot, "reference.apk"),
            linkedMapOf("classes.dex" to byteArrayOf(1), "../resource.txt" to byteArrayOf(2)),
        )
        val local = zip(File(localRoot, "local.apk"), linkedMapOf("classes.dex" to byteArrayOf(1)))

        val failure = assertThrows(ApkComparisonException::class.java) {
            compare(reference, referenceRoot, local, localRoot)
        }

        assertEquals("ZIP_ENTRY_PATH_UNSAFE", failure.code)
    }

    @Test
    fun `duplicate ZIP entry is rejected`() {
        val referenceRoot = temporaryFolder.newFolder("reference-duplicate")
        val localRoot = temporaryFolder.newFolder("local-duplicate")
        val reference = zip(
            File(referenceRoot, "reference.apk"),
            linkedMapOf(
                "classes.dex" to byteArrayOf(1),
                "classes2.dex" to byteArrayOf(2),
                "classes3.dex" to byteArrayOf(3),
            ),
        )
        reference.writeBytes(reference.readBytes().replaceAscii("classes3.dex", "classes2.dex"))
        val local = zip(File(localRoot, "local.apk"), linkedMapOf("classes.dex" to byteArrayOf(1)))

        val failure = assertThrows(ApkComparisonException::class.java) {
            compare(reference, referenceRoot, local, localRoot)
        }

        assertEquals("ZIP_DUPLICATE_ENTRY", failure.code)
    }

    @Test
    fun `missing root dex is incomparable`() {
        val referenceRoot = temporaryFolder.newFolder("reference-no-dex")
        val localRoot = temporaryFolder.newFolder("local-no-dex")
        val reference = zip(File(referenceRoot, "reference.apk"), linkedMapOf("classes2.dex" to byteArrayOf(1)))
        val local = zip(File(localRoot, "local.apk"), linkedMapOf("classes.dex" to byteArrayOf(1)))

        val failure = assertThrows(ApkComparisonException::class.java) {
            compare(reference, referenceRoot, local, localRoot)
        }

        assertEquals("REQUIRED_DEX_MISSING", failure.code)
    }

    private fun compare(reference: File, referenceRoot: File, local: File, localRoot: File) = comparator.compare(
        referenceApk = reference,
        referenceRoot = referenceRoot,
        expectedReference = expected(reference),
        localApk = local,
        localRoot = localRoot,
        expectedLocal = expected(local),
    )

    private fun zip(file: File, entries: LinkedHashMap<String, ByteArray>): File {
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun expected(file: File) = ExpectedApkFile(file.length(), sha256(file))

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun ByteArray.replaceAscii(original: String, replacement: String): ByteArray {
        require(original.length == replacement.length)
        val from = original.toByteArray()
        val to = replacement.toByteArray()
        val result = copyOf()
        var index = 0
        while (index <= result.size - from.size) {
            if (from.indices.all { offset -> result[index + offset] == from[offset] }) {
                to.copyInto(result, index)
                index += to.size
            } else {
                index++
            }
        }
        return result
    }
}
