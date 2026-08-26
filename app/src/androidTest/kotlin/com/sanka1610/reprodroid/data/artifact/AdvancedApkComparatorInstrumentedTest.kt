package com.sanka1610.reprodroid.data.artifact

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class AdvancedApkComparatorInstrumentedTest {
    @Test
    fun realCompiledApkSupportsDexManifestAndResourceSemanticEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceApk = File(context.applicationInfo.sourceDir)
        val root = File(context.cacheDir, "phase-2d-parser-e2e").apply { mkdirs() }
        val left = File(root, "left.apk")
        val right = File(root, "right.apk")
        rewriteApk(sourceApk, left, mutate = false)
        rewriteApk(sourceApk, right, mutate = true)

        val result = AdvancedApkComparator(
            maxProcessingNanos = 180_000_000_000L,
            forceSemanticAnalysisForTests = true,
        ).compare(
            left,
            root,
            ExpectedApkFile(left.length(), sha256(left)),
            right,
            root,
            ExpectedApkFile(right.length(), sha256(right)),
        )

        assertEquals(result.reason, AdvancedOutcome.DIFFERENT, result.inventoryOutcome)
        assertEquals(result.reason, AdvancedOutcome.MATCH, result.dexStructuralOutcome)
        assertEquals(result.reason, AdvancedOutcome.MATCH, result.manifestSemanticOutcome)
        assertEquals(result.reason, AdvancedOutcome.DIFFERENT, result.resourceTableSemanticOutcome)
        assertTrue(result.entries.isNotEmpty())
    }

    private fun rewriteApk(source: File, destination: File, mutate: Boolean) {
        ZipFile(source).use { zip ->
            ZipOutputStream(destination.outputStream()).use { output ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val sourceEntry = entries.nextElement()
                    output.putNextEntry(java.util.zip.ZipEntry(sourceEntry.name))
                    if (!sourceEntry.isDirectory) {
                        var bytes = zip.getInputStream(sourceEntry).use { it.readBytes() }
                        if (mutate) {
                            bytes = when (sourceEntry.name) {
                                "AndroidManifest.xml" -> bytes.changeFirstElementLineNumber()
                                "resources.arsc" -> bytes.replaceEncodedText("ReproDroid", "XeproDroid")
                                else -> bytes
                            }
                        }
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
            }
        }
    }

    private fun ByteArray.replaceAscii(original: String, replacement: String): ByteArray {
        require(original.length == replacement.length)
        val result = copyOf()
        val from = original.toByteArray()
        val to = replacement.toByteArray()
        var replacements = 0
        for (index in 0..result.size - from.size) {
            if (from.indices.all { result[index + it] == from[it] }) {
                to.copyInto(result, index)
                replacements++
            }
        }
        check(replacements > 0) { "Fixture string $original was not found" }
        return result
    }

    private fun ByteArray.replaceEncodedText(original: String, replacement: String): ByteArray =
        runCatching { replaceAscii(original, replacement) }.getOrElse {
            replaceBytes(original.toByteArray(Charsets.UTF_16LE), replacement.toByteArray(Charsets.UTF_16LE))
        }

    private fun ByteArray.replaceBytes(original: ByteArray, replacement: ByteArray): ByteArray {
        require(original.size == replacement.size)
        val result = copyOf()
        var replacements = 0
        for (index in 0..result.size - original.size) {
            if (original.indices.all { result[index + it] == original[it] }) {
                replacement.copyInto(result, index)
                replacements++
            }
        }
        check(replacements > 0) { "Encoded fixture text was not found" }
        return result
    }

    private fun ByteArray.changeFirstElementLineNumber(): ByteArray {
        val result = copyOf()
        var offset = readUInt16(2)
        while (offset + 8 <= result.size) {
            val chunkType = readUInt16(offset)
            val headerSize = readUInt16(offset + 2)
            val chunkSize = readUInt32(offset + 4)
            check(headerSize >= 8 && chunkSize >= headerSize && offset + chunkSize <= result.size) {
                "Malformed binary XML fixture chunk"
            }
            if (chunkType == 0x0102) {
                check(headerSize >= 16)
                result[offset + 8] = (result[offset + 8].toInt() xor 1).toByte()
                return result
            }
            offset += chunkSize
        }
        error("Binary XML start element was not found")
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readUInt32(offset: Int): Int =
        readUInt16(offset) or (readUInt16(offset + 2) shl 16)

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                update(buffer, 0, read)
            }
        }
        digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
