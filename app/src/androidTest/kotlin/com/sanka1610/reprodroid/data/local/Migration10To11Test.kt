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
class Migration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationBackfillsLegacyJobsAndBuildSnapshotsWithNone() {
        helper.createDatabase(DATABASE_NAME, 10).apply {
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-a', 'REAL_TRUSTED', 'https://github.com/example/app', 'TAG', '1.0',
                    'SUCCEEDED', 100, 1, '2026-08-28T00:00:00Z', '2026-08-28T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO comparison_runs (
                    comparisonRunId, registeredAppId, releaseSnapshotId, referenceAssetId,
                    runnerJobId, expectedCommitSha, expectedRecipeId, expectedVariantName,
                    status, outcome, repeatOfficialOutcome, repeatabilityOutcome,
                    createdAt, updatedAt
                ) VALUES (
                    'comparison', 'app', 'release', 'asset', 'job-a',
                    '0123456789012345678901234567890123456789', 'recipe', 'release',
                    'COMPLETED', 'MATCH', 'MATCH', 'MATCH',
                    '2026-08-28T00:00:00Z', '2026-08-28T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO build_environment_manifests VALUES (
                    'job-a', 1, '0123456789012345678901234567890123456789',
                    '21.0.12', 'Eclipse Adoptium', '8.14.3', 36, '36.0.0',
                    '${"a".repeat(64)}', '2026-08-28T00:02:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO build_environment_dependencies VALUES " +
                    "('job-a', 0, 'example.jar', '${"b".repeat(64)}')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            11,
            true,
            ReproDroidDatabase.MIGRATION_10_11,
        ).use { migrated ->
            migrated.query(
                "SELECT effectiveDependencyPinning FROM jobs WHERE jobId = 'job-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("NONE", cursor.getString(0))
            }
            migrated.query(
                "SELECT runnerDependencyPinning, repeatRunnerDependencyPinning, " +
                    "outcome, repeatOfficialOutcome, repeatabilityOutcome " +
                    "FROM comparison_runs WHERE comparisonRunId = 'comparison'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("NONE", cursor.getString(0))
                assertEquals("NONE", cursor.getString(1))
                assertEquals("MATCH", cursor.getString(2))
                assertEquals("MATCH", cursor.getString(3))
                assertEquals("MATCH", cursor.getString(4))
            }
            migrated.query(
                "SELECT apkSha256 FROM build_environment_manifests WHERE jobId = 'job-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a".repeat(64), cursor.getString(0))
            }
            migrated.query(
                "SELECT fileName, sha256 FROM build_environment_dependencies WHERE jobId = 'job-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("example.jar", cursor.getString(0))
                assertEquals("b".repeat(64), cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-3b-dependency-pinning-migration-test"
    }
}
