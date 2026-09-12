package com.sanka1610.reprodroid.data.log

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

data class AppLogExportResult(
    val suggestedName: String,
    val sizeBytes: Long,
    val recordCount: Int,
    val includesRotatedFile: Boolean,
)

/** Exports only the app-owned operational log through a user-selected SAF URI. */
class AppLogExportManager(
    private val context: Context,
    private val store: AppLogStore,
    private val clock: () -> Instant = { Instant.now() },
) {
    suspend fun exportTo(destination: Uri): AppLogExportResult = withContext(Dispatchers.IO) {
        val snapshot = store.snapshot()
        val header = buildHeader(snapshot)
        val exportBytes = header + snapshot.bytes
        check(exportBytes.size.toLong() <= MAX_EXPORT_BYTES) { "LOG_EXPORT_LIMIT_EXCEEDED" }
        val descriptor = context.contentResolver.openFileDescriptor(destination, "rwt")
            ?: throw IllegalStateException("The selected log destination cannot be opened.")
        descriptor.use { parcel ->
            FileOutputStream(parcel.fileDescriptor).use { output ->
                output.write(exportBytes)
                output.flush()
                output.fd.sync()
            }
        }
        AppLogExportResult(
            suggestedName = SUGGESTED_FILE_NAME,
            sizeBytes = exportBytes.size.toLong(),
            recordCount = snapshot.recordCount,
            includesRotatedFile = snapshot.includesRotatedFile,
        )
    }

    private fun buildHeader(snapshot: AppLogSnapshot): ByteArray = buildString {
        appendLine("# ReproDroid operational log export")
        appendLine("# format: reprodroid-operational-log-export@1")
        appendLine("# generatedAt: ${clock()}")
        appendLine("# target: android-application")
        appendLine("# rangeStart: ${snapshot.firstTimestamp ?: "unknown"}")
        appendLine("# rangeEnd: ${snapshot.lastTimestamp ?: "unknown"}")
        appendLine("# recordCount: ${snapshot.recordCount}")
        appendLine("# rotatedIncluded: ${snapshot.includesRotatedFile}")
        appendLine("# missing: false")
        appendLine("# truncated: false")
        appendLine("# contentWarning: review before sharing; operational details may be sensitive")
        appendLine()
    }.toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val MAX_EXPORT_BYTES: Long = 32L * 1024L * 1024L
        const val MIME_TYPE = "text/plain"
        const val SUGGESTED_FILE_NAME = "reprodroid-operational-logs.txt"
    }
}
