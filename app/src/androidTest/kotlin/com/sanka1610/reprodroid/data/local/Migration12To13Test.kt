package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesJobsAndCreatesEmptySourceScanEvidenceTables() {
        helper.createDatabase(DATABASE_NAME, 12).apply {
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-a', 'REAL_TRUSTED', 'https://github.com/example/app', 'TAG', '1.0',
                    'SUCCEEDED', 100, 1, '2026-08-29T00:00:00Z', '2026-08-29T00:01:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            13,
            true,
            ReproDroidDatabase.MIGRATION_12_13,
        ).use { migrated ->
            migrated.query("SELECT state FROM jobs WHERE jobId = 'job-a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SUCCEEDED", cursor.getString(0))
            }
            listOf("source_scans", "source_scan_detector_counts", "source_scan_findings").forEach { table ->
                migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-3d-source-scan-migration-test"
    }
}
