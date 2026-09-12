package com.sanka1610.reprodroid.data.log

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.local.WritableAuditDestinationProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLogExportManagerTest {
    private lateinit var context: Context
    private lateinit var directory: java.io.File
    private lateinit var store: AppLogStore
    private lateinit var manager: AppLogExportManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = context.cacheDir.resolve("operational-log-export-test").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        store = AppLogStore(directory) { Instant.parse("2026-09-09T00:00:00Z") }
        manager = AppLogExportManager(context, store) { Instant.parse("2026-09-09T01:00:00Z") }
        WritableAuditDestinationProvider.destinationFile(context).delete()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        WritableAuditDestinationProvider.destinationFile(context).delete()
    }

    @Test
    fun exportsExactUtf8SnapshotThroughCreateDocumentCompatibleUri() = runBlocking {
        store.info("EXPORT_TEST", "日本語 operational message")
        val expected = store.snapshot()

        val result = manager.exportTo(WritableAuditDestinationProvider.URI)
        val destination = WritableAuditDestinationProvider.destinationFile(context).toPath()

        assertTrue(result.sizeBytes > expected.sizeBytes)
        assertEquals(expected.recordCount, result.recordCount)
        assertTrue(Files.exists(destination))
        val exported = String(Files.readAllBytes(destination), StandardCharsets.UTF_8)
        assertTrue(exported.contains("# format: reprodroid-operational-log-export@1"))
        assertTrue(exported.contains("# generatedAt: 2026-09-09T01:00:00Z"))
        assertTrue(exported.contains("# rangeStart: 2026-09-09T00:00:00Z"))
        assertTrue(exported.contains("# rangeEnd: 2026-09-09T00:00:00Z"))
        assertTrue(exported.contains("# missing: false"))
        assertTrue(exported.contains("# truncated: false"))
        assertTrue(exported.endsWith(String(expected.bytes, StandardCharsets.UTF_8)))
        assertEquals(
            "日本語 operational message",
            exported.substringAfter("\"message\":\"").substringBefore("\""),
        )
    }

    @Test
    fun emptyLogExportsExplicitUnknownRange() = runBlocking {
        val result = manager.exportTo(WritableAuditDestinationProvider.URI)
        val exported = WritableAuditDestinationProvider.destinationFile(context).readText()

        assertEquals(0, result.recordCount)
        assertTrue(exported.contains("# rangeStart: unknown"))
        assertTrue(exported.contains("# rangeEnd: unknown"))
        assertTrue(exported.contains("# recordCount: 0"))
    }

    @Test
    fun destinationFailureIsSurfacedWithoutACompletedResult() = runBlocking {
        store.info("EXPORT_TEST", "message")

        assertTrue(runCatching { manager.exportTo(WritableAuditDestinationProvider.REJECT_URI) }.isFailure)
    }

    @Test
    fun destinationCapacityFailureIsSurfacedWithoutACompletedResult() = runBlocking {
        store.info("EXPORT_TEST", "message")

        assertTrue(runCatching { manager.exportTo(WritableAuditDestinationProvider.NO_SPACE_URI) }.isFailure)
        assertTrue(!WritableAuditDestinationProvider.destinationFile(context).exists())
    }
}
