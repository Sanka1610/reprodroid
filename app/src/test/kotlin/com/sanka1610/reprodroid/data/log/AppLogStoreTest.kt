package com.sanka1610.reprodroid.data.log

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogStoreTest {
    @Test
    fun recordsStructuredUtf8MessagesAndRedactsSecretsAndPaths() {
        val directory = Files.createTempDirectory("reprodroid-app-log-").toFile()
        try {
            val store = AppLogStore(directory) { Instant.parse("2026-09-09T00:00:00Z") }

            assertTrue(
                store.info(
                    " runner_request ",
                    "token=top-secret password:very-secret Bearer abc123 https://runner.example /data/user/0/com.example/private 日本語",
                ),
            )

            val text = String(store.snapshot().bytes, StandardCharsets.UTF_8)
            assertTrue(text.contains("\"schemaVersion\":1"))
            assertTrue(text.contains("\"level\":\"INFO\""))
            assertTrue(text.contains("\"event\":\"RUNNER_REQUEST\""))
            assertTrue(text.contains("日本語"))
            assertTrue(text.contains("[REDACTED]"))
            assertTrue(text.contains("[URL_REDACTED]"))
            assertTrue(text.contains("[PATH_REDACTED]"))
            assertFalse(text.contains("top-secret"))
            assertFalse(text.contains("very-secret"))
            assertFalse(text.contains("abc123"))
            assertFalse(text.contains("runner.example"))
            assertFalse(text.contains("/data/user/0"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rotatesAtFourMiBAndKeepsBothGenerationsBounded() {
        val directory = Files.createTempDirectory("reprodroid-app-log-rotate-").toFile()
        try {
            val store = AppLogStore(directory) { Instant.parse("2026-09-09T00:00:00Z") }
            val message = "x".repeat(AppLogStore.MAX_MESSAGE_BYTES)

            repeat(1_100) { assertTrue(store.info("ROTATION", message)) }

            val current = directory.resolve(AppLogStore.CURRENT_FILE_NAME)
            val rotated = directory.resolve(AppLogStore.ROTATED_FILE_NAME)
            assertTrue(rotated.isFile)
            assertTrue(current.length() <= AppLogStore.MAX_FILE_BYTES)
            assertTrue(rotated.length() <= AppLogStore.MAX_FILE_BYTES)
            assertTrue(store.snapshot().includesRotatedFile)
            assertTrue(store.snapshot().recordCount > 0)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidEventDoesNotCreateAnOperationalLog() {
        val directory = Files.createTempDirectory("reprodroid-app-log-invalid-").toFile()
        try {
            val store = AppLogStore(directory)

            assertFalse(store.warn("not a bounded event", "should not be written"))
            assertFalse(directory.resolve(AppLogStore.CURRENT_FILE_NAME).exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
