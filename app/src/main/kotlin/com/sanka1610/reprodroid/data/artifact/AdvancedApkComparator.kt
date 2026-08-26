package com.sanka1610.reprodroid.data.artifact

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FieldOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.InlineIndexInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VerificationErrorInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VtableIndexInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.instruction.formats.UnknownInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.value.EncodedValue
import com.android.tools.smali.dexlib2.util.EncodedValueUtils
import com.android.tools.smali.dexlib2.util.ReferenceUtil
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

enum class ApkEntryCategory {
    SIGNATURE,
    DEX,
    NATIVE_CODE,
    MANIFEST,
    RESOURCE_TABLE,
    RESOURCE_FILE,
    ASSET,
    OTHER,
}

enum class AdvancedOutcome {
    MATCH,
    DIFFERENT,
    INCOMPARABLE,
    NOT_REQUIRED,
}

data class ApkInventoryEntry(
    val name: String,
    val category: ApkEntryCategory,
    val sizeBytes: Long,
    val crc32: Long,
    val compressionMethod: Int,
    val uncompressedSha256: String,
)

data class ComparedInventoryEntry(
    val entryName: String,
    val category: ApkEntryCategory,
    val result: String,
    val left: ApkInventoryEntry?,
    val right: ApkInventoryEntry?,
) {
    val archiveMetadataChanged: Boolean
        get() = left != null && right != null &&
            (left.compressionMethod != right.compressionMethod || left.crc32 != right.crc32)
}

data class AdvancedApkComparison(
    val inventoryOutcome: AdvancedOutcome,
    val dexStructuralOutcome: AdvancedOutcome,
    val manifestSemanticOutcome: AdvancedOutcome,
    val resourceTableSemanticOutcome: AdvancedOutcome,
    val reason: String?,
    val entries: List<ComparedInventoryEntry>,
    val semanticDifferences: List<SemanticDifference>,
)

data class SemanticDifference(
    val component: String,
    val stableKey: String,
    val result: String,
    val leftSha256: String?,
    val rightSha256: String?,
)

/**
 * Produces explanatory evidence for Phase 2D without changing the exact raw DEX/native outcome.
 * APK entries are streamed from ZipFile and are never extracted to the filesystem.
 */
class AdvancedApkComparator(
    private val maxArchiveEntries: Int = 100_000,
    private val maxEntryUncompressedBytes: Long = 512L * 1024 * 1024,
    private val maxTotalUncompressedBytes: Long = 1024L * 1024 * 1024,
    private val maxDeclaredUncompressedBytes: Long = 4L * 1024 * 1024 * 1024,
    private val maxSemanticEntryBytes: Long = 64L * 1024 * 1024,
    private val maxSemanticTotalBytes: Long = 64L * 1024 * 1024,
    private val maxDexClasses: Int = 200_000,
    private val maxDexMembers: Int = 2_000_000,
    private val maxDexInstructions: Int = 20_000_000,
    private val maxSemanticItems: Int = 2_000_000,
    private val maxProcessingNanos: Long = 180_000_000_000L,
    private val forceSemanticAnalysisForTests: Boolean = false,
) {
    fun compare(
        leftApk: File,
        leftRoot: File,
        expectedLeft: ExpectedApkFile,
        rightApk: File,
        rightRoot: File,
        expectedRight: ExpectedApkFile,
    ): AdvancedApkComparison {
        val startedAt = System.nanoTime()
        return try {
            val left = inventory(verifyInput(leftApk.toPath(), leftRoot.toPath(), expectedLeft, "LEFT"), "LEFT", startedAt)
            val right = inventory(verifyInput(rightApk.toPath(), rightRoot.toPath(), expectedRight, "RIGHT"), "RIGHT", startedAt)
            val entries = (left.entries.keys + right.entries.keys).toSortedSet().map { name ->
                val leftEntry = left.entries[name]
                val rightEntry = right.entries[name]
                val result = when {
                    leftEntry == null -> "ADDED"
                    rightEntry == null -> "MISSING"
                    leftEntry.sizeBytes == rightEntry.sizeBytes &&
                        leftEntry.uncompressedSha256 == rightEntry.uncompressedSha256 -> "MATCH"
                    else -> "HASH_MISMATCH"
                }
                ComparedInventoryEntry(name, leftEntry?.category ?: requireNotNull(rightEntry).category, result, leftEntry, rightEntry)
            }
            val inventoryOutcome = if (entries.all { it.result == "MATCH" }) AdvancedOutcome.MATCH else AdvancedOutcome.DIFFERENT
            val dex = semanticOutcome(entries, ApkEntryCategory.DEX) {
                canonicalDex(left.semanticBytes, startedAt) to canonicalDex(right.semanticBytes, startedAt)
            }
            val manifest = semanticOutcome(entries, ApkEntryCategory.MANIFEST) {
                canonicalManifest(requiredSemantic(left, MANIFEST_ENTRY), startedAt) to
                    canonicalManifest(requiredSemantic(right, MANIFEST_ENTRY), startedAt)
            }
            val resources = semanticOutcome(entries, ApkEntryCategory.RESOURCE_TABLE) {
                canonicalResourceTable(requiredSemantic(left, RESOURCE_TABLE_ENTRY), startedAt) to
                    canonicalResourceTable(requiredSemantic(right, RESOURCE_TABLE_ENTRY), startedAt)
            }
            AdvancedApkComparison(
                inventoryOutcome = inventoryOutcome,
                dexStructuralOutcome = dex.outcome,
                manifestSemanticOutcome = manifest.outcome,
                resourceTableSemanticOutcome = resources.outcome,
                reason = listOfNotNull(
                    dex.reason?.let { "DEX:$it" },
                    manifest.reason?.let { "MANIFEST:$it" },
                    resources.reason?.let { "RESOURCES:$it" },
                ).takeIf { it.isNotEmpty() }?.joinToString(";"),
                entries = entries,
                semanticDifferences = dex.differences + manifest.differences + resources.differences,
            )
        } catch (failure: ApkComparisonException) {
            AdvancedApkComparison(
                inventoryOutcome = AdvancedOutcome.INCOMPARABLE,
                dexStructuralOutcome = AdvancedOutcome.INCOMPARABLE,
                manifestSemanticOutcome = AdvancedOutcome.INCOMPARABLE,
                resourceTableSemanticOutcome = AdvancedOutcome.INCOMPARABLE,
                reason = failure.code,
                entries = emptyList(),
                semanticDifferences = emptyList(),
            )
        } catch (failure: Throwable) {
            AdvancedApkComparison(
                inventoryOutcome = AdvancedOutcome.INCOMPARABLE,
                dexStructuralOutcome = AdvancedOutcome.INCOMPARABLE,
                manifestSemanticOutcome = AdvancedOutcome.INCOMPARABLE,
                resourceTableSemanticOutcome = AdvancedOutcome.INCOMPARABLE,
                reason = "ADVANCED_ANALYSIS_FAILED_${failure.javaClass.simpleName.uppercase()}",
                entries = emptyList(),
                semanticDifferences = emptyList(),
            )
        }
    }

    private fun semanticOutcome(
        entries: List<ComparedInventoryEntry>,
        category: ApkEntryCategory,
        canonical: () -> Pair<CanonicalSemantic, CanonicalSemantic>,
    ): SemanticEvidence {
        val selected = entries.filter { it.category == category }
        if (selected.isEmpty()) return SemanticEvidence(AdvancedOutcome.NOT_REQUIRED)
        if (selected.all { it.result == "MATCH" } && !forceSemanticAnalysisForTests) {
            return SemanticEvidence(AdvancedOutcome.NOT_REQUIRED)
        }
        if (selected.any { it.left == null || it.right == null }) return SemanticEvidence(AdvancedOutcome.DIFFERENT)
        return try {
            val (left, right) = canonical()
            SemanticEvidence(
                if (left.sha256 == right.sha256) AdvancedOutcome.MATCH else AdvancedOutcome.DIFFERENT,
                differences = semanticDifferences(left.items, right.items),
            )
        } catch (failure: Throwable) {
            SemanticEvidence(
                AdvancedOutcome.INCOMPARABLE,
                if (failure is ApkComparisonException) failure.code else
                    "SEMANTIC_ANALYSIS_FAILED_${failure.javaClass.simpleName.uppercase()}",
            )
        }
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
        if (!fileReal.startsWith(rootReal) || !Files.isRegularFile(fileReal, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(fileReal) != expected.sizeBytes
        ) {
            throw ApkComparisonException("${label}_PATH_UNSAFE", "$label APK escaped app-private storage or changed size.")
        }
        if (sha256(fileReal) != expected.sha256.lowercase()) {
            throw ApkComparisonException("${label}_SHA256_MISMATCH", "$label APK changed after verification.")
        }
        return fileReal
    }

    private fun inventory(apk: Path, label: String, startedAt: Long): Inventory {
        try {
            ZipFile(apk.toFile()).use { zip ->
                val seen = HashSet<String>()
                val result = linkedMapOf<String, ApkInventoryEntry>()
                val semantic = linkedMapOf<String, ByteArray>()
                var entryCount = 0
                var declaredTotal = 0L
                var actualTotal = 0L
                var semanticTotal = 0L
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    checkTime(startedAt)
                    val entry = iterator.nextElement()
                    entryCount++
                    if (entryCount > maxArchiveEntries) fail("ZIP_ENTRY_LIMIT_EXCEEDED", "$label APK contains too many entries.")
                    validateEntryName(entry.name, label)
                    if (!seen.add(entry.name)) fail("ZIP_DUPLICATE_ENTRY", "$label APK contains duplicate entry ${entry.name}.")
                    if (entry.size < 0) fail("ZIP_ENTRY_SIZE_UNKNOWN", "$label APK entry ${entry.name} has unknown size.")
                    if (entry.size > maxEntryUncompressedBytes) fail("ZIP_ENTRY_SIZE_LIMIT_EXCEEDED", "$label APK entry ${entry.name} is too large.")
                    if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                        fail("ZIP_METHOD_UNSUPPORTED", "$label APK entry ${entry.name} uses method ${entry.method}.")
                    }
                    declaredTotal = checkedAdd(declaredTotal, entry.size, "ZIP_DECLARED_SIZE_LIMIT_EXCEEDED")
                    if (declaredTotal > maxDeclaredUncompressedBytes) {
                        fail("ZIP_DECLARED_SIZE_LIMIT_EXCEEDED", "$label APK declares excessive expanded content.")
                    }
                    val category = if (entry.isDirectory) ApkEntryCategory.OTHER else classify(entry.name)
                    val capture = category in SEMANTIC_CATEGORIES
                    if (capture && entry.size > maxSemanticEntryBytes) {
                        fail("SEMANTIC_ENTRY_SIZE_LIMIT_EXCEEDED", "$label semantic entry ${entry.name} is too large.")
                    }
                    if (capture) {
                        semanticTotal = checkedAdd(semanticTotal, entry.size, "SEMANTIC_TOTAL_SIZE_LIMIT_EXCEEDED")
                        if (semanticTotal > maxSemanticTotalBytes) {
                            fail("SEMANTIC_TOTAL_SIZE_LIMIT_EXCEEDED", "$label APK semantic inputs are too large.")
                        }
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val crc = CRC32()
                    val captured = if (capture) ByteArray(entry.size.toInt()) else null
                    var count = 0L
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            val captureOffset = count.toInt()
                            count = checkedAdd(count, read.toLong(), "ZIP_ENTRY_SIZE_LIMIT_EXCEEDED")
                            if (count > maxEntryUncompressedBytes ||
                                checkedAdd(actualTotal, count, "ZIP_EXPANSION_LIMIT_EXCEEDED") > maxTotalUncompressedBytes
                            ) {
                                fail("ZIP_EXPANSION_LIMIT_EXCEEDED", "$label APK exceeds the safe expansion limit.")
                            }
                            if (capture && count > captured!!.size) {
                                fail("ZIP_ENTRY_SIZE_MISMATCH", "$label entry ${entry.name} exceeds its declared size.")
                            }
                            digest.update(buffer, 0, read)
                            crc.update(buffer, 0, read)
                            if (capture) buffer.copyInto(captured!!, captureOffset, 0, read)
                            checkTime(startedAt)
                        }
                    }
                    if (count != entry.size) fail("ZIP_ENTRY_SIZE_MISMATCH", "$label entry ${entry.name} size changed while reading.")
                    if (entry.crc < 0 || crc.value != entry.crc) fail("ZIP_ENTRY_CRC_MISMATCH", "$label entry ${entry.name} failed CRC verification.")
                    actualTotal = checkedAdd(actualTotal, count, "ZIP_EXPANSION_LIMIT_EXCEEDED")
                    result[entry.name] = ApkInventoryEntry(
                        entry.name,
                        category,
                        count,
                        crc.value,
                        entry.method,
                        digest.digest().toHex(),
                    )
                    if (capture) {
                        semantic[entry.name] = captured!!
                    }
                }
                if (ROOT_DEX_ENTRY !in result) fail("REQUIRED_DEX_MISSING", "$label APK has no classes.dex.")
                if (MANIFEST_ENTRY !in result) fail("REQUIRED_MANIFEST_MISSING", "$label APK has no AndroidManifest.xml.")
                return Inventory(result, semantic)
            }
        } catch (failure: ApkComparisonException) {
            throw failure
        } catch (failure: ZipException) {
            val code = if (failure.message?.contains("compression method", ignoreCase = true) == true) {
                "ZIP_METHOD_UNSUPPORTED"
            } else {
                "MALFORMED_APK_ZIP"
            }
            fail(code, "$label APK ZIP is malformed: ${failure.message}")
        } catch (failure: IOException) {
            fail("APK_ZIP_READ_FAILED", "$label APK could not be read: ${failure.message}")
        }
    }

    private fun canonicalDex(semantic: Map<String, ByteArray>, startedAt: Long): CanonicalSemantic {
        val dexFiles = semantic.filterKeys { DEX_ENTRY.matches(it) }
        if (ROOT_DEX_ENTRY !in dexFiles) fail("DEX_ROOT_MISSING", "classes.dex is missing.")
        val classes = linkedMapOf<String, ClassDef>()
        dexFiles.toSortedMap().forEach { (_, bytes) ->
            val dex = DexBackedDexFile.fromInputStream(null, ByteArrayInputStream(bytes))
            dex.classes.forEach { classDef ->
                if (classes.put(classDef.type, classDef) != null) fail("DEX_DUPLICATE_CLASS", "Duplicate class ${classDef.type}.")
                if (classes.size > maxDexClasses) fail("DEX_CLASS_LIMIT_EXCEEDED", "DEX class limit exceeded.")
            }
        }
        val items = linkedMapOf<SemanticItemKey, String>()
        var members = 0
        var instructions = 0
        classes.toSortedMap().values.forEach { classDef ->
            checkTime(startedAt)
            putSemanticItem(
                items,
                "DEX_CLASS",
                classDef.type,
                "${classDef.accessFlags}|${classDef.superclass.orEmpty()}|" +
                    "${classDef.interfaces.sorted().joinToString(",")}|${canonicalAnnotations(classDef.annotations)}",
            )
            classDef.fields.sortedBy(::fieldKey).forEach { field ->
                members++
                if (members > maxDexMembers) fail("DEX_MEMBER_LIMIT_EXCEEDED", "DEX member limit exceeded.")
                putSemanticItem(
                    items,
                    "DEX_FIELD",
                    fieldKey(field),
                    "${field.accessFlags}|${canonicalEncoded(field.initialValue)}|${canonicalAnnotations(field.annotations)}",
                )
            }
            classDef.methods.sortedBy(::methodKey).forEach { method ->
                members++
                if (members > maxDexMembers) fail("DEX_MEMBER_LIMIT_EXCEEDED", "DEX member limit exceeded.")
                val methodDescriptor = methodKey(method)
                putSemanticItem(
                    items,
                    "DEX_METHOD",
                    methodDescriptor,
                    buildString {
                        append("${method.accessFlags}|${canonicalAnnotations(method.annotations)}")
                        method.parameters.forEachIndexed { index, parameter ->
                            append("|parameter:$index:${parameter.type}:${canonicalAnnotations(parameter.annotations)}")
                        }
                    },
                )
                method.implementation?.let { implementation ->
                    val implementationDigest = MessageDigest.getInstance("SHA-256")
                    implementationDigest.line("registers|${implementation.registerCount}")
                    val methodInstructions = implementation.instructions.toList()
                    val addressToOrdinal = linkedMapOf<Int, Int>()
                    var nextAddress = 0
                    methodInstructions.forEachIndexed { ordinal, instruction ->
                        addressToOrdinal[nextAddress] = ordinal
                        nextAddress += instruction.codeUnits
                    }
                    addressToOrdinal[nextAddress] = methodInstructions.size
                    val switchBaseByPayload = linkedMapOf<Int, Int>()
                    var sourceAddress = 0
                    methodInstructions.forEach { instruction ->
                        if (instruction is OffsetInstruction) {
                            val targetAddress = sourceAddress + instruction.codeOffset
                            val targetOrdinal = addressToOrdinal[targetAddress]
                                ?: fail("DEX_INVALID_BRANCH_TARGET", "DEX branch target is not an instruction boundary.")
                            if (methodInstructions.getOrNull(targetOrdinal) is SwitchPayload) {
                                val previous = switchBaseByPayload.put(targetAddress, sourceAddress)
                                if (previous != null && previous != sourceAddress) {
                                    fail("DEX_AMBIGUOUS_SWITCH_PAYLOAD", "DEX switch payload has multiple base instructions.")
                                }
                            }
                        }
                        sourceAddress += instruction.codeUnits
                    }
                    implementation.tryBlocks.sortedWith(compareBy({ it.startCodeAddress }, { it.codeUnitCount })).forEach {
                        val startOrdinal = addressToOrdinal[it.startCodeAddress]
                            ?: fail("DEX_INVALID_TRY_RANGE", "DEX try start is not an instruction boundary.")
                        val endOrdinal = addressToOrdinal[it.startCodeAddress + it.codeUnitCount]
                            ?: fail("DEX_INVALID_TRY_RANGE", "DEX try end is not an instruction boundary.")
                        val handlers = it.exceptionHandlers.map { handler ->
                            val handlerOrdinal = addressToOrdinal[handler.handlerCodeAddress]
                                ?: fail("DEX_INVALID_HANDLER_TARGET", "DEX handler target is not an instruction boundary.")
                            "${handler.exceptionType ?: "*"}@$handlerOrdinal"
                        }
                        implementationDigest.line("try|$startOrdinal|$endOrdinal|${handlers.joinToString(",")}")
                    }
                    sourceAddress = 0
                    methodInstructions.forEach { instruction ->
                        if (instruction is UnknownInstruction) fail("DEX_UNKNOWN_OPCODE", "DEX contains an unknown opcode.")
                        instructions++
                        if (instructions > maxDexInstructions) fail("DEX_INSTRUCTION_LIMIT_EXCEEDED", "DEX instruction limit exceeded.")
                        implementationDigest.line(
                            "instruction|${canonicalInstruction(instruction, sourceAddress, addressToOrdinal, switchBaseByPayload)}",
                        )
                        sourceAddress += instruction.codeUnits
                    }
                    items[SemanticItemKey("DEX_IMPLEMENTATION", methodDescriptor)] =
                        implementationDigest.digest().toHex()
                }
            }
        }
        return canonicalSemantic(items)
    }

    private fun fieldKey(field: Field): String = ReferenceUtil.getFieldDescriptor(field)

    private fun methodKey(method: Method): String = ReferenceUtil.getMethodDescriptor(method)

    private fun canonicalAnnotations(annotations: Set<out Annotation>): String = annotations.map { annotation ->
        val elements = annotation.elements.map { "${it.name}=${canonicalEncoded(it.value)}" }.sorted()
        "${annotation.visibility}:${annotation.type}{${elements.joinToString(",")}}"
    }.sorted().joinToString("|")

    private fun canonicalEncoded(value: EncodedValue?): String {
        if (value == null) return "null"
        return StringWriter().also { EncodedValueUtils.writeEncodedValue(it, value) }.toString()
    }

    private fun canonicalInstruction(
        instruction: Instruction,
        sourceAddress: Int,
        addressToOrdinal: Map<Int, Int>,
        switchBaseByPayload: Map<Int, Int>,
    ): String = buildString {
        append(instruction.opcode.name)
        if (instruction is OneRegisterInstruction) append("|a=${instruction.registerA}")
        if (instruction is TwoRegisterInstruction) append("|b=${instruction.registerB}")
        if (instruction is ThreeRegisterInstruction) append("|c=${instruction.registerC}")
        if (instruction is FiveRegisterInstruction) {
            append("|d=${instruction.registerD}|e=${instruction.registerE}")
            append("|f=${instruction.registerF}|g=${instruction.registerG}")
        }
        if (instruction is VariableRegisterInstruction) append("|registerCount=${instruction.registerCount}")
        if (instruction is RegisterRangeInstruction) {
            append("|start=${instruction.startRegister}|rangeCount=${instruction.registerCount}")
        }
        if (instruction is NarrowLiteralInstruction) append("|literal=${instruction.narrowLiteral}")
        if (instruction is WideLiteralInstruction) append("|wideLiteral=${instruction.wideLiteral}")
        if (instruction is OffsetInstruction) {
            val targetOrdinal = addressToOrdinal[sourceAddress + instruction.codeOffset]
                ?: fail("DEX_INVALID_BRANCH_TARGET", "DEX branch target is not an instruction boundary.")
            append("|target=$targetOrdinal")
        }
        if (instruction is FieldOffsetInstruction) append("|fieldOffset=${instruction.fieldOffset}")
        if (instruction is VtableIndexInstruction) append("|vtable=${instruction.vtableIndex}")
        if (instruction is InlineIndexInstruction) append("|inline=${instruction.inlineIndex}")
        if (instruction is VerificationErrorInstruction) append("|verification=${instruction.verificationError}")
        if (instruction is ReferenceInstruction) {
            append("|referenceType=${instruction.referenceType}|reference=${canonicalReference(instruction.reference)}")
        }
        if (instruction is DualReferenceInstruction) {
            append("|referenceType2=${instruction.referenceType2}|reference2=${canonicalReference(instruction.reference2)}")
        }
        if (instruction is SwitchPayload) {
            val switchBase = switchBaseByPayload[sourceAddress]
                ?: fail("DEX_ORPHAN_SWITCH_PAYLOAD", "DEX switch payload has no referring instruction.")
            append("|switch=")
            append(instruction.switchElements.joinToString(",") { element ->
                val targetOrdinal = addressToOrdinal[switchBase + element.offset]
                    ?: fail("DEX_INVALID_SWITCH_TARGET", "DEX switch target is not an instruction boundary.")
                "${element.key}:$targetOrdinal"
            })
        }
        if (instruction is ArrayPayload) {
            append("|elementWidth=${instruction.elementWidth}|array=${instruction.arrayElements.joinToString(",")}")
        }
    }

    private fun canonicalReference(reference: Reference): String = ReferenceUtil.getReferenceString(reference)
        ?: fail("DEX_REFERENCE_UNSUPPORTED", "DEX contains an unsupported reference.")

    private fun canonicalManifest(bytes: ByteArray, startedAt: Long): CanonicalSemantic {
        val manifest = AndroidManifestBlock.load(ByteArrayInputStream(bytes))
        checkTime(startedAt)
        val json = Json.parseToJsonElement(manifest.toJson().toString())
        if (json.toString().contains("\"node_type\":\"unknown\"")) {
            fail("MANIFEST_UNKNOWN_CHUNK", "Manifest contains an unknown binary XML node.")
        }
        return canonicalJsonSemantic(json, MANIFEST_IGNORED_KEYS, "MANIFEST", startedAt)
    }

    private fun canonicalResourceTable(bytes: ByteArray, startedAt: Long): CanonicalSemantic {
        val table = TableBlock.load(ByteArrayInputStream(bytes))
        checkTime(startedAt)
        if (table.getAllPackages().asSequence().any { packageBlock ->
                packageBlock.unknownChunkList.iterator().asSequence().any { !it.isNull }
            }
        ) {
            fail("RESOURCE_TABLE_UNKNOWN_CHUNK", "Resource table contains an unknown chunk.")
        }
        val json = Json.parseToJsonElement(table.toJson().toString())
        val canonicalDocument = canonicalJson(json, RESOURCE_IGNORED_KEYS, startedAt)
        val items = linkedMapOf<SemanticItemKey, String>()
        table.getAllPackages().asSequence().forEach { packageBlock ->
            packageBlock.resources.asSequence().forEach { resource ->
                resource.asSequence().forEach { entry ->
                    checkTime(startedAt)
                    val qualifiers = entry.resConfig.qualifiers.removePrefix("-")
                    val stableKey = "${packageBlock.name}:${resource.type}/${resource.name}[$qualifiers]"
                    val entryJson = Json.parseToJsonElement(entry.toJson().toString())
                    putSemanticItem(
                        items,
                        "RESOURCE_TABLE",
                        stableKey,
                        canonicalJson(entryJson, RESOURCE_IGNORED_KEYS, startedAt),
                    )
                }
            }
        }
        putSemanticItem(items, "RESOURCE_TABLE_DOCUMENT", "/", canonicalDocument)
        return CanonicalSemantic(sha256(canonicalDocument.toByteArray()), items)
    }

    private fun canonicalJson(
        element: JsonElement,
        ignoredKeys: Set<String>,
        startedAt: Long? = null,
    ): String {
        if (startedAt != null) checkTime(startedAt)
        return when (element) {
        JsonNull -> "null"
        is JsonPrimitive -> element.toString()
        is JsonArray -> element.map { canonicalJson(it, ignoredKeys, startedAt) }
            .sorted().joinToString(prefix = "[", postfix = "]")
        is JsonObject -> element.entries.asSequence()
            .filterNot { it.key in ignoredKeys }
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "${JsonPrimitive(key)}:${canonicalJson(value, ignoredKeys, startedAt)}"
            }
        }
    }

    private fun canonicalJsonSemantic(
        element: JsonElement,
        ignoredKeys: Set<String>,
        component: String,
        startedAt: Long,
    ): CanonicalSemantic {
        val items = linkedMapOf<SemanticItemKey, String>()
        collectJsonItems(element, ignoredKeys, component, "", items, startedAt)
        return CanonicalSemantic(
            sha256(canonicalJson(element, ignoredKeys, startedAt).toByteArray()),
            items,
        )
    }

    private fun collectJsonItems(
        element: JsonElement,
        ignoredKeys: Set<String>,
        component: String,
        path: String,
        items: MutableMap<SemanticItemKey, String>,
        startedAt: Long,
    ) {
        checkTime(startedAt)
        checkSemanticItemLimit(items.size)
        when (element) {
            JsonNull, is JsonPrimitive -> putSemanticItem(items, component, path.ifEmpty { "/" }, element.toString())
            is JsonObject -> {
                val children = element.entries.filterNot { it.key in ignoredKeys }.sortedBy { it.key }
                if (children.isEmpty()) putSemanticItem(items, component, path.ifEmpty { "/" }, "{}")
                children.forEach { (key, value) ->
                    collectJsonItems(value, ignoredKeys, component, "$path/${escapeJsonPointer(key)}", items, startedAt)
                }
            }
            is JsonArray -> {
                val children = element.sortedBy { canonicalJson(it, ignoredKeys, startedAt) }
                if (children.isEmpty()) putSemanticItem(items, component, path.ifEmpty { "/" }, "[]")
                children.forEachIndexed { index, value ->
                    collectJsonItems(value, ignoredKeys, component, "$path/$index", items, startedAt)
                }
            }
        }
    }

    private fun escapeJsonPointer(value: String): String = value.replace("~", "~0").replace("/", "~1")

    private fun putSemanticItem(
        items: MutableMap<SemanticItemKey, String>,
        component: String,
        stableKey: String,
        canonicalValue: String,
    ) {
        if (stableKey.length > MAX_SEMANTIC_KEY_LENGTH) {
            fail("SEMANTIC_KEY_LIMIT_EXCEEDED", "Semantic evidence key is too long.")
        }
        checkSemanticItemLimit(items.size)
        val key = SemanticItemKey(component, stableKey)
        if (items.put(key, sha256(canonicalValue.toByteArray())) != null) {
            fail("SEMANTIC_DUPLICATE_KEY", "Semantic evidence contains a duplicate stable key.")
        }
    }

    private fun checkSemanticItemLimit(currentSize: Int) {
        if (currentSize >= maxSemanticItems) {
            fail("SEMANTIC_ITEM_LIMIT_EXCEEDED", "Semantic evidence item limit exceeded.")
        }
    }

    private fun canonicalSemantic(items: Map<SemanticItemKey, String>): CanonicalSemantic {
        val digest = MessageDigest.getInstance("SHA-256")
        items.toSortedMap(compareBy<SemanticItemKey>({ it.component }, { it.stableKey })).forEach { (key, value) ->
            digest.line("${key.component}|${key.stableKey}|$value")
        }
        return CanonicalSemantic(digest.digest().toHex(), items)
    }

    private fun semanticDifferences(
        left: Map<SemanticItemKey, String>,
        right: Map<SemanticItemKey, String>,
    ): List<SemanticDifference> = (left.keys + right.keys)
        .toSortedSet(compareBy<SemanticItemKey>({ it.component }, { it.stableKey }))
        .mapNotNull { key ->
            val leftHash = left[key]
            val rightHash = right[key]
            if (leftHash == rightHash) return@mapNotNull null
            SemanticDifference(
                component = key.component,
                stableKey = key.stableKey,
                result = when {
                    leftHash == null -> "ADDED"
                    rightHash == null -> "MISSING"
                    else -> "DIFFERENT"
                },
                leftSha256 = leftHash,
                rightSha256 = rightHash,
            )
        }

    private fun requiredSemantic(inventory: Inventory, name: String): ByteArray = inventory.semanticBytes[name]
        ?: fail("SEMANTIC_ENTRY_MISSING", "Required semantic entry $name is missing.")

    private fun classify(name: String): ApkEntryCategory = when {
        name == MANIFEST_ENTRY -> ApkEntryCategory.MANIFEST
        name == RESOURCE_TABLE_ENTRY -> ApkEntryCategory.RESOURCE_TABLE
        DEX_ENTRY.matches(name) -> ApkEntryCategory.DEX
        NATIVE_LIBRARY_ENTRY.matches(name) -> ApkEntryCategory.NATIVE_CODE
        isSignatureEntry(name) -> ApkEntryCategory.SIGNATURE
        name.startsWith("res/") -> ApkEntryCategory.RESOURCE_FILE
        name.startsWith("assets/") -> ApkEntryCategory.ASSET
        else -> ApkEntryCategory.OTHER
    }

    private fun isSignatureEntry(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val leaf = name.substringAfterLast('/').uppercase()
        return leaf == "MANIFEST.MF" || leaf.endsWith(".SF") || leaf.endsWith(".RSA") ||
            leaf.endsWith(".DSA") || leaf.endsWith(".EC")
    }

    private fun validateEntryName(name: String, label: String) {
        val noSlash = name.removeSuffix("/")
        val segments = noSlash.split('/')
        if (name.isBlank() || name.length > MAX_ENTRY_NAME_LENGTH || name.startsWith('/') || '\\' in name ||
            name.any(Char::isISOControl) || noSlash.isBlank() || segments.any { it.isBlank() || it == "." || it == ".." }
        ) fail("ZIP_ENTRY_PATH_UNSAFE", "$label APK contains an unsafe entry path.")
    }

    private fun checkTime(startedAt: Long) {
        if (System.nanoTime() - startedAt > maxProcessingNanos) {
            fail("ADVANCED_COMPARISON_TIMEOUT", "Advanced comparison exceeded its processing limit.")
        }
    }

    private fun checkedAdd(left: Long, right: Long, code: String): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        fail(code, "APK size accounting overflowed.")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

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

    private fun MessageDigest.line(value: String) {
        update(value.toByteArray(Charsets.UTF_8))
        update(0)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fail(code: String, message: String): Nothing = throw ApkComparisonException(code, message)

    private data class Inventory(
        val entries: Map<String, ApkInventoryEntry>,
        val semanticBytes: Map<String, ByteArray>,
    )

    private data class SemanticItemKey(val component: String, val stableKey: String)

    private data class CanonicalSemantic(
        val sha256: String,
        val items: Map<SemanticItemKey, String>,
    )

    private data class SemanticEvidence(
        val outcome: AdvancedOutcome,
        val reason: String? = null,
        val differences: List<SemanticDifference> = emptyList(),
    )

    private companion object {
        const val ROOT_DEX_ENTRY = "classes.dex"
        const val MANIFEST_ENTRY = "AndroidManifest.xml"
        const val RESOURCE_TABLE_ENTRY = "resources.arsc"
        const val MAX_ENTRY_NAME_LENGTH = 1_024
        const val MAX_SEMANTIC_KEY_LENGTH = 8_192
        val SHA256 = Regex("[0-9a-f]{64}")
        val DEX_ENTRY = Regex("classes(?:[2-9][0-9]*)?\\.dex")
        val NATIVE_LIBRARY_ENTRY = Regex("lib/[^/]+/[^/]+\\.so")
        val SEMANTIC_CATEGORIES = setOf(ApkEntryCategory.DEX, ApkEntryCategory.MANIFEST, ApkEntryCategory.RESOURCE_TABLE)
        val MANIFEST_IGNORED_KEYS = setOf("line", "line_end", "comment", "prefix", "encoding")
        val RESOURCE_IGNORED_KEYS = setOf("package_id", "id")
    }
}
