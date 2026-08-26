package com.sanka1610.reprodroid.data.artifact

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotationElement
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableArrayPayload
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.UnknownChunk
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AdvancedApkComparatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `all APK entries receive one category and content result`() {
        val entries = linkedMapOf(
            "AndroidManifest.xml" to "manifest".toByteArray(),
            "resources.arsc" to "resources".toByteArray(),
            "classes.dex" to "dex".toByteArray(),
            "lib/arm64-v8a/libsample.so" to "native".toByteArray(),
            "res/drawable/icon.png" to "resource-file".toByteArray(),
            "assets/config.json" to "asset".toByteArray(),
            "META-INF/CERT.RSA" to "signature".toByteArray(),
            "kotlin/metadata.bin" to "other".toByteArray(),
            "empty/" to byteArrayOf(),
        )
        val (left, leftRoot) = apk("category-left", entries)
        val (right, rightRoot) = apk("category-right", entries)

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.MATCH, comparison.inventoryOutcome)
        assertEquals(entries.keys.sorted(), comparison.entries.map { it.entryName })
        assertEquals(
            setOf(
                ApkEntryCategory.MANIFEST,
                ApkEntryCategory.RESOURCE_TABLE,
                ApkEntryCategory.DEX,
                ApkEntryCategory.NATIVE_CODE,
                ApkEntryCategory.RESOURCE_FILE,
                ApkEntryCategory.ASSET,
                ApkEntryCategory.SIGNATURE,
                ApkEntryCategory.OTHER,
            ),
            comparison.entries.map { it.category }.toSet(),
        )
        assertTrue(comparison.entries.all { it.result == "MATCH" })
        assertEquals(ApkEntryCategory.OTHER, comparison.entries.single { it.entryName == "empty/" }.category)
        assertEquals(AdvancedOutcome.NOT_REQUIRED, comparison.dexStructuralOutcome)
    }

    @Test
    fun `added missing and changed entries are retained without changing semantic failures into matches`() {
        val (left, leftRoot) = apk(
            "different-left",
            linkedMapOf(
                "AndroidManifest.xml" to "same-manifest".toByteArray(),
                "classes.dex" to "invalid-left-dex".toByteArray(),
                "assets/missing.txt" to byteArrayOf(1),
            ),
        )
        val (right, rightRoot) = apk(
            "different-right",
            linkedMapOf(
                "AndroidManifest.xml" to "same-manifest".toByteArray(),
                "classes.dex" to "invalid-right-dex".toByteArray(),
                "res/raw/added.bin" to byteArrayOf(2),
            ),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.DIFFERENT, comparison.inventoryOutcome)
        assertEquals(AdvancedOutcome.INCOMPARABLE, comparison.dexStructuralOutcome)
        assertEquals(
            mapOf(
                "AndroidManifest.xml" to "MATCH",
                "assets/missing.txt" to "MISSING",
                "classes.dex" to "HASH_MISMATCH",
                "res/raw/added.bin" to "ADDED",
            ),
            comparison.entries.associate { it.entryName to it.result },
        )
    }

    @Test
    fun `compression metadata is evidence but does not create a content difference`() {
        val common = linkedMapOf(
            "AndroidManifest.xml" to "manifest".toByteArray(),
            "classes.dex" to "dex".toByteArray(),
        )
        val (left, leftRoot) = apk("stored", common, stored = true)
        val (right, rightRoot) = apk("deflated", common, stored = false)

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.MATCH, comparison.inventoryOutcome)
        assertTrue(comparison.entries.all { it.archiveMetadataChanged })
    }

    @Test
    fun `DEX header-only difference is structurally equivalent`() {
        val dex = dex("Lsample/Example;")
        val changedHeader = dex.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "dex-structural-left",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to dex),
        )
        val (right, rightRoot) = apk(
            "dex-structural-right",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to changedHeader),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.DIFFERENT, comparison.inventoryOutcome)
        assertEquals(AdvancedOutcome.MATCH, comparison.dexStructuralOutcome)
    }

    @Test
    fun `DEX class difference is structurally different`() {
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "dex-meaning-left",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to dex("Lsample/Left;")),
        )
        val (right, rightRoot) = apk(
            "dex-meaning-right",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to dex("Lsample/Right;")),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)
        assertEquals(AdvancedOutcome.DIFFERENT, comparison.dexStructuralOutcome)
        assertEquals(
            setOf("Lsample/Left;", "Lsample/Right;"),
            comparison.semanticDifferences.filter { it.component == "DEX_CLASS" }.map { it.stableKey }.toSet(),
        )
    }

    @Test
    fun `multidex placement order is excluded from structural meaning`() {
        val manifest = manifest("com.example")
        val classA = dex("Lsample/A;")
        val classB = dex("Lsample/B;")
        val (left, leftRoot) = apk(
            "multidex-left",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to classA, "classes2.dex" to classB),
        )
        val (right, rightRoot) = apk(
            "multidex-right",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to classB, "classes2.dex" to classA),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.DIFFERENT, comparison.inventoryOutcome)
        assertEquals(AdvancedOutcome.MATCH, comparison.dexStructuralOutcome)
        assertTrue(comparison.semanticDifferences.none { it.component.startsWith("DEX_") })
    }

    @Test
    fun `DEX annotations try blocks and payloads participate in structural evidence`() {
        val leftDex = complexDex("evidence", 1, 1)
        val changedDex = complexDex("changed", 2, 0)
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "complex-dex-left",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to leftDex),
        )
        val (equivalent, equivalentRoot) = apk(
            "complex-dex-equivalent",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to leftDex),
        )
        val (changed, changedRoot) = apk(
            "complex-dex-changed",
            linkedMapOf("AndroidManifest.xml" to manifest, "classes.dex" to changedDex),
        )

        val forced = AdvancedApkComparator(forceSemanticAnalysisForTests = true)
        assertEquals(AdvancedOutcome.MATCH, compare(left, leftRoot, equivalent, equivalentRoot, forced).dexStructuralOutcome)
        val changedComparison = compare(left, leftRoot, changed, changedRoot, forced)
        assertEquals(AdvancedOutcome.DIFFERENT, changedComparison.dexStructuralOutcome)
        assertTrue(changedComparison.semanticDifferences.any { it.component == "DEX_IMPLEMENTATION" })
        assertTrue(changedComparison.semanticDifferences.any { it.component == "DEX_METHOD" })
    }

    @Test
    fun `manifest line metadata does not create a semantic difference`() {
        val leftManifest = manifest("com.example", line = 10)
        val rightManifest = manifest("com.example", line = 200)
        val dex = dex("Lsample/Example;")
        val (left, leftRoot) = apk(
            "manifest-left",
            linkedMapOf("AndroidManifest.xml" to leftManifest, "classes.dex" to dex),
        )
        val (right, rightRoot) = apk(
            "manifest-right",
            linkedMapOf("AndroidManifest.xml" to rightManifest, "classes.dex" to dex),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.DIFFERENT, comparison.inventoryOutcome)
        assertEquals(AdvancedOutcome.MATCH, comparison.manifestSemanticOutcome)
    }

    @Test
    fun `manifest meaning difference remains different`() {
        val dex = dex("Lsample/Example;")
        val (left, leftRoot) = apk(
            "manifest-meaning-left",
            linkedMapOf("AndroidManifest.xml" to manifest("com.example.left"), "classes.dex" to dex),
        )
        val (right, rightRoot) = apk(
            "manifest-meaning-right",
            linkedMapOf("AndroidManifest.xml" to manifest("com.example.right"), "classes.dex" to dex),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)
        assertEquals(AdvancedOutcome.DIFFERENT, comparison.manifestSemanticOutcome)
        assertTrue(comparison.semanticDifferences.any { it.component == "MANIFEST" })
    }

    @Test
    fun `malformed manifest and resource table fail semantic analysis closed`() {
        val dex = dex("Lsample/Example;")
        val validManifest = manifest("com.example")
        val (manifestLeft, manifestLeftRoot) = apk(
            "malformed-manifest-left",
            linkedMapOf("AndroidManifest.xml" to byteArrayOf(1, 2, 3), "classes.dex" to dex),
        )
        val (manifestRight, manifestRightRoot) = apk(
            "malformed-manifest-right",
            linkedMapOf("AndroidManifest.xml" to byteArrayOf(4, 5, 6), "classes.dex" to dex),
        )
        val manifestComparison = compare(manifestLeft, manifestLeftRoot, manifestRight, manifestRightRoot)
        assertEquals(AdvancedOutcome.INCOMPARABLE, manifestComparison.manifestSemanticOutcome)
        assertTrue(manifestComparison.reason.orEmpty().startsWith("MANIFEST:"))

        val (resourcesLeft, resourcesLeftRoot) = apk(
            "malformed-resources-left",
            linkedMapOf(
                "AndroidManifest.xml" to validManifest,
                "resources.arsc" to byteArrayOf(1, 2, 3),
                "classes.dex" to dex,
            ),
        )
        val (resourcesRight, resourcesRightRoot) = apk(
            "malformed-resources-right",
            linkedMapOf(
                "AndroidManifest.xml" to validManifest,
                "resources.arsc" to byteArrayOf(4, 5, 6),
                "classes.dex" to dex,
            ),
        )
        val resourcesComparison = compare(resourcesLeft, resourcesLeftRoot, resourcesRight, resourcesRightRoot)
        assertEquals(AdvancedOutcome.INCOMPARABLE, resourcesComparison.resourceTableSemanticOutcome)
        assertTrue(resourcesComparison.reason.orEmpty().startsWith("RESOURCES:"))
    }

    @Test
    fun `resource table meaning difference remains different`() {
        val dex = dex("Lsample/Example;")
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "resource-left",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to resources(0x7f, "com.example.left", "Left"),
                "classes.dex" to dex,
            ),
        )
        val (right, rightRoot) = apk(
            "resource-right",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to resources(0x7f, "com.example.right", "Right"),
                "classes.dex" to dex,
            ),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)
        assertEquals(comparison.reason, AdvancedOutcome.DIFFERENT, comparison.resourceTableSemanticOutcome)
        assertTrue(comparison.semanticDifferences.any { it.component == "RESOURCE_TABLE" })
    }

    @Test
    fun `resource table differences retain package type name and configuration key`() {
        val dex = dex("Lsample/Example;")
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "resource-config-left",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to configuredResources("English"),
                "classes.dex" to dex,
            ),
        )
        val (right, rightRoot) = apk(
            "resource-config-right",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to configuredResources("Changed"),
                "classes.dex" to dex,
            ),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(comparison.reason, AdvancedOutcome.DIFFERENT, comparison.resourceTableSemanticOutcome)
        assertTrue(
            comparison.semanticDifferences.toString(),
            comparison.semanticDifferences.any {
                it.component == "RESOURCE_TABLE" && it.stableKey == "com.example:string/app_name[en]"
            },
        )
    }

    @Test
    fun `unknown resource table chunks are not treated as semantic matches`() {
        val dex = dex("Lsample/Example;")
        val manifest = manifest("com.example")
        val (left, leftRoot) = apk(
            "resource-unknown-left",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to resources(0x7f, "com.example", "Known"),
                "classes.dex" to dex,
            ),
        )
        val (right, rightRoot) = apk(
            "resource-unknown-right",
            linkedMapOf(
                "AndroidManifest.xml" to manifest,
                "resources.arsc" to resourcesWithUnknownChunk(),
                "classes.dex" to dex,
            ),
        )

        val comparison = compare(left, leftRoot, right, rightRoot)

        assertEquals(AdvancedOutcome.DIFFERENT, comparison.inventoryOutcome)
        assertEquals(AdvancedOutcome.INCOMPARABLE, comparison.resourceTableSemanticOutcome)
        assertEquals("RESOURCES:RESOURCE_TABLE_UNKNOWN_CHUNK", comparison.reason)
    }

    @Test
    fun `traversal duplicate size limit unsupported method and broken zip fail closed`() {
        val baseline = linkedMapOf("AndroidManifest.xml" to byteArrayOf(1), "classes.dex" to byteArrayOf(2))
        val (safe, safeRoot) = apk("negative-safe", baseline)

        val (traversal, traversalRoot) = apk("negative-traversal", baseline + ("../escape" to byteArrayOf(3)))
        assertEquals("ZIP_ENTRY_PATH_UNSAFE", compare(traversal, traversalRoot, safe, safeRoot).reason)

        val (duplicate, duplicateRoot) = apk(
            "negative-duplicate",
            baseline + ("classes2.dex" to byteArrayOf(3)) + ("classes3.dex" to byteArrayOf(4)),
        )
        duplicate.writeBytes(duplicate.readBytes().replaceAscii("classes3.dex", "classes2.dex"))
        assertEquals("ZIP_DUPLICATE_ENTRY", compare(duplicate, duplicateRoot, safe, safeRoot).reason)

        val limited = AdvancedApkComparator(maxEntryUncompressedBytes = 0)
        assertEquals("ZIP_ENTRY_SIZE_LIMIT_EXCEEDED", compare(safe, safeRoot, safe, safeRoot, limited).reason)
        val timedOut = AdvancedApkComparator(maxProcessingNanos = -1)
        assertEquals("ADVANCED_COMPARISON_TIMEOUT", compare(safe, safeRoot, safe, safeRoot, timedOut).reason)
        val semanticLimited = AdvancedApkComparator(maxSemanticTotalBytes = 0)
        assertEquals(
            "SEMANTIC_TOTAL_SIZE_LIMIT_EXCEEDED",
            compare(safe, safeRoot, safe, safeRoot, semanticLimited).reason,
        )
        val symlinkRoot = temporaryFolder.newFolder("negative-symlink")
        val symlink = File(symlinkRoot, "linked.apk")
        Files.createSymbolicLink(symlink.toPath(), safe.toPath())
        assertEquals("LEFT_PATH_UNSAFE", compare(symlink, symlinkRoot, safe, safeRoot).reason)

        val validDex = dex("Lsample/Limit;")
        val validManifest = manifest("com.example")
        val (dexLimitedLeft, dexLimitedLeftRoot) = apk(
            "negative-dex-limit-left",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to validDex),
        )
        val changedDex = validDex.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        val (dexLimitedRight, dexLimitedRightRoot) = apk(
            "negative-dex-limit-right",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to changedDex),
        )
        val semanticItemLimited = AdvancedApkComparator(maxSemanticItems = 0)
        assertEquals(
            "DEX:SEMANTIC_ITEM_LIMIT_EXCEEDED",
            compare(
                dexLimitedLeft,
                dexLimitedLeftRoot,
                dexLimitedRight,
                dexLimitedRightRoot,
                semanticItemLimited,
            ).reason,
        )
        val dexLimited = AdvancedApkComparator(maxDexClasses = 0)
        assertEquals(
            "DEX:DEX_CLASS_LIMIT_EXCEEDED",
            compare(dexLimitedLeft, dexLimitedLeftRoot, dexLimitedRight, dexLimitedRightRoot, dexLimited).reason,
        )

        val longDescriptorPrefix = "L" + "a".repeat(8_192)
        val (longKeyLeft, longKeyLeftRoot) = apk(
            "negative-semantic-key-left",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to dex("$longDescriptorPrefix;")),
        )
        val (longKeyRight, longKeyRightRoot) = apk(
            "negative-semantic-key-right",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to dex("${longDescriptorPrefix}b;")),
        )
        assertEquals(
            "DEX:SEMANTIC_KEY_LIMIT_EXCEEDED",
            compare(longKeyLeft, longKeyLeftRoot, longKeyRight, longKeyRightRoot).reason,
        )

        val opcodeDex = complexDex("opcode", 1, 1)
        val (opcodeLeft, opcodeLeftRoot) = apk(
            "negative-opcode-left",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to opcodeDex),
        )
        val (opcodeRight, opcodeRightRoot) = apk(
            "negative-opcode-right",
            linkedMapOf("AndroidManifest.xml" to validManifest, "classes.dex" to opcodeDex.withUnknownFirstOpcode()),
        )
        assertEquals(
            "DEX:DEX_UNKNOWN_OPCODE",
            compare(opcodeLeft, opcodeLeftRoot, opcodeRight, opcodeRightRoot).reason,
        )

        val (unsupported, unsupportedRoot) = apk("negative-method", baseline)
        unsupported.writeBytes(unsupported.readBytes().replaceZipMethod(8, 99))
        assertEquals("ZIP_METHOD_UNSUPPORTED", compare(unsupported, unsupportedRoot, safe, safeRoot).reason)

        val brokenRoot = temporaryFolder.newFolder("negative-broken")
        val broken = File(brokenRoot, "broken.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        assertEquals("MALFORMED_APK_ZIP", compare(broken, brokenRoot, safe, safeRoot).reason)
    }

    private fun compare(
        left: File,
        leftRoot: File,
        right: File,
        rightRoot: File,
        comparator: AdvancedApkComparator = AdvancedApkComparator(),
    ): AdvancedApkComparison = comparator.compare(
        left,
        leftRoot,
        ExpectedApkFile(left.length(), sha256(left)),
        right,
        rightRoot,
        ExpectedApkFile(right.length(), sha256(right)),
    )

    private fun apk(
        name: String,
        entries: Map<String, ByteArray>,
        stored: Boolean = false,
    ): Pair<File, File> {
        val root = temporaryFolder.newFolder(name)
        val file = File(root, "$name.apk")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (entryName, bytes) ->
                val entry = ZipEntry(entryName)
                if (stored) {
                    val crc = CRC32().apply { update(bytes) }
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = crc.value
                }
                output.putNextEntry(entry)
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file to root
    }

    private fun dex(className: String): ByteArray {
        val builder = DexBuilder(Opcodes.forDexVersion(35))
        builder.internClassDef(
            className,
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet(),
            emptyList(),
            emptyList(),
        )
        val store = MemoryDataStore()
        builder.writeTo(store)
        return store.data
    }

    private fun complexDex(annotationValue: String, payloadValue: Int, handlerAddress: Int): ByteArray {
        val builder = DexBuilder(Opcodes.forDexVersion(35))
        val annotation = ImmutableAnnotation(
            1,
            "Lsample/Evidence;",
            setOf(ImmutableAnnotationElement("value", ImmutableStringEncodedValue(annotationValue))),
        )
        val implementation = ImmutableMethodImplementation(
            1,
            listOf(
                ImmutableInstruction10x(Opcode.RETURN_VOID),
                ImmutableInstruction10x(Opcode.NOP),
                ImmutableArrayPayload(4, listOf(payloadValue)),
            ),
            listOf(ImmutableTryBlock(0, 1, listOf(ImmutableExceptionHandler(null, handlerAddress)))),
            emptyList(),
        )
        val method = builder.internMethod(
            "Lsample/Complex;",
            "run",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            setOf(annotation),
            emptySet(),
            implementation,
        )
        builder.internClassDef(
            "Lsample/Complex;",
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            setOf(annotation),
            emptyList(),
            listOf(method),
        )
        val store = MemoryDataStore()
        builder.writeTo(store)
        return store.data
    }

    private fun manifest(packageName: String, line: Int = 1): ByteArray {
        val manifest = AndroidManifestBlock.empty()
        manifest.packageName = packageName
        manifest.manifestElement.startLineNumber = line
        manifest.refreshFull()
        return manifest.bytes
    }

    private fun resources(packageId: Int, packageName: String, value: String): ByteArray {
        val table = TableBlock().apply {
            initializeAsEmpty()
            setNull(false)
        }
        table.newPackage(packageId, packageName).getOrCreate("", "string", "app_name").valueAsString = value
        table.refreshFull()
        val output = File.createTempFile("resources-", ".arsc", temporaryFolder.root)
        table.writeBytes(output)
        return output.readBytes()
    }

    private fun configuredResources(englishValue: String): ByteArray {
        val table = TableBlock().apply {
            initializeAsEmpty()
            setNull(false)
        }
        val packageBlock = table.newPackage(0x7f, "com.example")
        packageBlock.getOrCreate("", "string", "app_name").valueAsString = "Default"
        packageBlock.getOrCreate("en", "string", "app_name").valueAsString = englishValue
        table.refreshFull()
        val output = File.createTempFile("configured-resources-", ".arsc", temporaryFolder.root)
        table.writeBytes(output)
        return output.readBytes()
    }

    private fun resourcesWithUnknownChunk(): ByteArray {
        val table = TableBlock().apply {
            initializeAsEmpty()
            setNull(false)
        }
        val packageBlock = table.newPackage(0x7f, "com.example")
        packageBlock.getOrCreate("", "string", "app_name").valueAsString = "Known"
        packageBlock.unknownChunkList.add(UnknownChunk().apply {
            headerBlock.setType(0x7777.toShort())
            body.set(byteArrayOf(1, 2, 3, 4))
            setNull(false)
        })
        table.refreshFull()
        val output = File.createTempFile("unknown-resources-", ".arsc", temporaryFolder.root)
        table.writeBytes(output)
        return output.readBytes()
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun ByteArray.replaceAscii(original: String, replacement: String): ByteArray {
        require(original.length == replacement.length)
        val result = copyOf()
        val from = original.toByteArray()
        val to = replacement.toByteArray()
        for (index in 0..result.size - from.size) {
            if (from.indices.all { result[index + it] == from[it] }) to.copyInto(result, index)
        }
        return result
    }

    private fun ByteArray.replaceZipMethod(original: Int, replacement: Int): ByteArray {
        val result = copyOf()
        var index = 0
        while (index <= result.size - 10) {
            val signature = result[index].toInt() and 0xff or
                ((result[index + 1].toInt() and 0xff) shl 8) or
                ((result[index + 2].toInt() and 0xff) shl 16) or
                ((result[index + 3].toInt() and 0xff) shl 24)
            if (signature == 0x04034b50 || signature == 0x02014b50) {
                val methodOffset = if (signature == 0x04034b50) index + 8 else index + 10
                val method = result[methodOffset].toInt() and 0xff or
                    ((result[methodOffset + 1].toInt() and 0xff) shl 8)
                if (method == original) {
                    result[methodOffset] = (replacement and 0xff).toByte()
                    result[methodOffset + 1] = ((replacement ushr 8) and 0xff).toByte()
                }
            }
            index++
        }
        return result
    }

    private fun ByteArray.withUnknownFirstOpcode(): ByteArray {
        val result = copyOf()
        val classDefinitionsOffset = result.readInt32(100)
        var cursor = result.readInt32(classDefinitionsOffset + 24)
        check(cursor > 0) { "Fixture has no class data" }
        fun readUleb128(): Int {
            var value = 0
            var shift = 0
            while (true) {
                val current = result[cursor++].toInt() and 0xff
                value = value or ((current and 0x7f) shl shift)
                if (current and 0x80 == 0) return value
                shift += 7
                check(shift < 35) { "Malformed fixture ULEB128" }
            }
        }
        val staticFields = readUleb128()
        val instanceFields = readUleb128()
        val directMethods = readUleb128()
        val virtualMethods = readUleb128()
        repeat(staticFields + instanceFields) {
            readUleb128()
            readUleb128()
        }
        check(directMethods + virtualMethods > 0) { "Fixture has no method" }
        readUleb128()
        readUleb128()
        val codeOffset = readUleb128()
        check(codeOffset > 0 && codeOffset + 16 < result.size) { "Fixture has no method implementation" }
        result[codeOffset + 16] = 0x3e
        return result
    }

    private fun ByteArray.readInt32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

}
