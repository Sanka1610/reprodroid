package com.sanka1610.reprodroid.data.log

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class AppLogLevel {
    INFO,
    WARN,
    ERROR,
}

data class AppLogSnapshot(
    val bytes: ByteArray,
    val recordCount: Int,
    val includesRotatedFile: Boolean,
    val firstTimestamp: String?,
    val lastTimestamp: String?,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

/**
 * Best-effort, app-owned operational log storage.
 *
 * The store is deliberately file based so adding diagnostics does not change
 * the Room schema or make a log export part of the product history. Every
 * record is one UTF-8 JSON line. Only one rotated generation is retained.
 */
class AppLogStore(
    private val directory: File,
    private val clock: () -> Instant = { Instant.now() },
) {
    constructor(context: Context) : this(
        directory = context.filesDir.resolve(DIRECTORY_NAME),
        clock = { Instant.now() },
    )

    fun info(event: String, message: String = ""): Boolean = record(AppLogLevel.INFO, event, message)

    fun warn(event: String, message: String = ""): Boolean = record(AppLogLevel.WARN, event, message)

    fun error(event: String, message: String = ""): Boolean = record(AppLogLevel.ERROR, event, message)

    /**
     * App logging must never take down a product operation. Invalid event
     * names and filesystem failures are therefore represented by false.
     */
    fun record(level: AppLogLevel, event: String, message: String = ""): Boolean = synchronized(lock) {
        runCatching {
            val safeEvent = normalizeEvent(event)
            val line = encodeLine(level, safeEvent, sanitizeMessage(message))
            append(line)
        }.isSuccess
    }

    fun snapshot(): AppLogSnapshot = synchronized(lock) {
        val rotated = readFileIfSafe(rotatedFile)
        val current = readFileIfSafe(currentFile)
        val bytes = rotated + current
        val text = String(bytes, StandardCharsets.UTF_8)
        val timestamps = text.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            runCatching {
                Json.parseToJsonElement(line).jsonObject["timestamp"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }.toList()
        AppLogSnapshot(
            bytes = bytes,
            recordCount = timestamps.size,
            includesRotatedFile = rotated.isNotEmpty(),
            firstTimestamp = timestamps.firstOrNull(),
            lastTimestamp = timestamps.lastOrNull(),
        )
    }

    fun clear() = synchronized(lock) {
        listOf(currentFile, rotatedFile).forEach { file ->
            if (Files.isSymbolicLink(file.toPath())) {
                throw IllegalStateException("Operational log path must not be a symbolic link.")
            }
            Files.deleteIfExists(file.toPath())
        }
    }

    private val lock = Any()

    private val currentFile: File
        get() = directory.resolve(CURRENT_FILE_NAME)

    private val rotatedFile: File
        get() = directory.resolve(ROTATED_FILE_NAME)

    private fun append(line: ByteArray) {
        require(line.size.toLong() <= MAX_FILE_BYTES) { "Operational log record exceeds the file limit." }
        ensureDirectory()
        requireRegularOrMissing(currentFile)
        requireRegularOrMissing(rotatedFile)
        if (currentFile.isFile && currentFile.length() + line.size > MAX_FILE_BYTES) {
            rotate()
        }
        FileOutputStream(currentFile, true).use { output ->
            output.write(line)
            output.fd.sync()
        }
    }

    private fun ensureDirectory() {
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IllegalStateException("Operational log directory must not be a symbolic link.")
        }
        check(directory.isDirectory || directory.mkdirs()) {
            "Operational log directory could not be created."
        }
    }

    private fun rotate() {
        if (rotatedFile.exists()) Files.delete(rotatedFile.toPath())
        if (!currentFile.exists()) return
        try {
            Files.move(
                currentFile.toPath(),
                rotatedFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                currentFile.toPath(),
                rotatedFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun readFileIfSafe(file: File): ByteArray {
        if (!file.isFile) return ByteArray(0)
        requireRegularOrMissing(file)
        return FileInputStream(file).use { it.readBytes() }
    }

    private fun requireRegularOrMissing(file: File) {
        check(!Files.isSymbolicLink(file.toPath())) {
            "Operational log path must not be a symbolic link."
        }
        check(!file.exists() || file.isFile) {
            "Operational log path must be a regular file."
        }
    }

    private fun encodeLine(level: AppLogLevel, event: String, message: String): ByteArray =
        (buildJsonObject {
            put("schemaVersion", 1)
            put("timestamp", clock().toString())
            put("level", level.name)
            put("event", event)
            put("message", message)
        }.toString() + "\n").toByteArray(StandardCharsets.UTF_8)

    private fun normalizeEvent(event: String): String {
        val normalized = event.trim().uppercase(Locale.US)
        require(normalized.isNotEmpty() && normalized.length <= MAX_EVENT_LENGTH && EVENT_PATTERN.matches(normalized)) {
            "Operational log event is not a bounded code."
        }
        return normalized
    }

    private fun sanitizeMessage(message: String): String {
        var sanitized = message
            .replace(NEWLINE_PATTERN, " ")
            .trim()
            .replace(BEARER_PATTERN, "Bearer [REDACTED]")
            .replace(URL_PATTERN, "[URL_REDACTED]")
            .replace(SECRET_ASSIGNMENT_PATTERN) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }
            .replace(PATH_PATTERN, "[PATH_REDACTED]")
        if (sanitized.toByteArray(StandardCharsets.UTF_8).size > MAX_MESSAGE_BYTES) {
            sanitized = truncateUtf8(sanitized, MAX_MESSAGE_BYTES)
        }
        return sanitized
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        var end = value.length
        while (end > 0 && value.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
            end -= 1
        }
        return value.substring(0, end)
    }

    companion object {
        const val DIRECTORY_NAME = "operational-logs"
        const val CURRENT_FILE_NAME = "app.log"
        const val ROTATED_FILE_NAME = "app.log.1"
        const val MAX_FILE_BYTES: Long = 4L * 1024L * 1024L
        const val MAX_MESSAGE_BYTES = 4 * 1024

        private const val MAX_EVENT_LENGTH = 80
        private val EVENT_PATTERN = Regex("[A-Z][A-Z0-9_.-]*")
        private val NEWLINE_PATTERN = Regex("[\\r\\n\\t]+")
        private val BEARER_PATTERN = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+")
        private val URL_PATTERN = Regex("(?i)https?://[^\\s?#]+(?:[?#][^\\s]*)?")
        private val SECRET_ASSIGNMENT_PATTERN = Regex(
            "(?i)\\b(token|secret|password|passphrase|credential|authorization|cookie|api[_-]?key|private[_-]?key|pairing(?:[_-]?secret)?)\\b\\s*[:=]\\s*[^\\s,;]+",
        )
        private val PATH_PATTERN = Regex("(?<![A-Za-z0-9])(?:/(?:data|storage|home|tmp|sdcard)(?:/[^\\s]+)+|[A-Za-z]:\\\\[^\\s]+)")
    }
}
