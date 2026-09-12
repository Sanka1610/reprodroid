package com.sanka1610.reprodroid.data.local

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.storage.AndroidStorageManager
import com.sanka1610.reprodroid.data.storage.AuditExportManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AuditExportScaleTest {
    private lateinit var context: Context
    private lateinit var database: ReproDroidDatabase
    private lateinit var storage: AndroidStorageManager
    private val createdExports = mutableListOf<java.nio.file.Path>()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java).build()
        storage = AndroidStorageManager(context, database)
    }

    @After
    fun tearDown() {
        createdExports.forEach { Files.deleteIfExists(it) }
        database.close()
    }

    @Test
    fun twentyThousandRecordBundleUsesTheRealThirtyTwoMibBoundaryAndRejectsOneMore() = runBlocking {
        val writable = database.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            val insert = writable.compileStatement(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, 'GITHUB', 'VERIFICATION', 'NOT_CHECKED', ?, ?)
                """.trimIndent(),
            )
            repeat(RECORD_LIMIT) { index ->
                val suffix = index.toString(16).padStart(12, '0')
                val id = "00000000-0000-4000-8000-$suffix"
                val url = "https://github.com/example/app${index.toString().padStart(5, '0')}"
                insert.clearBindings()
                insert.bindString(1, id)
                insert.bindString(2, "app-$index-${"x".repeat(DISPLAY_PADDING)}")
                insert.bindString(3, url)
                insert.bindString(4, url)
                insert.bindString(5, FIXED_TIME)
                insert.bindString(6, FIXED_TIME)
                insert.executeInsert()
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }

        val manager = AuditExportManager(context, database, storage)
        val staged = manager.stageAll()
        val path = storage.expectedAuditExportPath(staged.auditExportId)
        createdExports.add(path)
        val bundleBytes = Files.readAllBytes(path)
        val parsed = Json.parseToJsonElement(String(bundleBytes, Charsets.UTF_8)).jsonObject
        val bundleSha256 = MessageDigest.getInstance("SHA-256").digest(bundleBytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(RECORD_LIMIT, staged.recordCount)
        assertEquals(RECORD_LIMIT, parsed.getValue("records").jsonArray.size)
        assertTrue("Expected a near-limit bundle but was ${staged.sizeBytes}", staged.sizeBytes >= MIN_ACCEPTED_BYTES)
        assertTrue(staged.sizeBytes <= MAX_EXPORT_BYTES)
        val evidence = "records=${staged.recordCount} sizeBytes=${staged.sizeBytes} " +
            "payloadSha256=${staged.payloadSha256} bundleSha256=$bundleSha256"
        println("A4.2-23 $evidence")
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("phase48ScaleEvidence", evidence) },
        )
        val originalBytes = Files.size(path)

        writable.execSQL(
            """
            INSERT INTO registered_apps (
                registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
            ) VALUES (
                '00000000-0000-4000-8000-ffffffffffff', 'one-too-many',
                'https://github.com/example/overflow', 'https://github.com/example/overflow',
                'GITHUB', 'VERIFICATION', 'NOT_CHECKED', '$FIXED_TIME', '$FIXED_TIME'
            )
            """.trimIndent(),
        )
        val overflow = runCatching { manager.stageAll() }

        assertTrue(overflow.isFailure)
        assertTrue(overflow.exceptionOrNull()?.message.orEmpty().contains("EXPORT_LIMIT_EXCEEDED"))
        assertEquals(originalBytes, Files.size(path))
    }

    private companion object {
        const val RECORD_LIMIT = 20_000
        const val DISPLAY_PADDING = 1_200
        const val MIN_ACCEPTED_BYTES = 30L * 1024L * 1024L
        const val MAX_EXPORT_BYTES = 32L * 1024L * 1024L
        const val FIXED_TIME = "2026-09-09T00:00:00Z"
    }
}
