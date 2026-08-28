package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesLegacyEvidenceAndBackfillsEmptyDeterminismControls() {
        helper.createDatabase(DATABASE_NAME, 11).apply {
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    effectiveRecipeId, effectiveVariantName, effectiveBuildRoot,
                    effectiveJavaMajor, effectiveBuildTasks, effectiveDependencyPinning,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-a', 'REAL_TRUSTED', 'https://github.com/example/app', 'TAG', '1.0',
                    'recipe', 'release', '.', 21, 'assembleRelease', 'LOCKFILE',
                    'SUCCEEDED', 100, 1, '2026-08-28T00:00:00Z', '2026-08-28T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO build_environment_manifests (
                    jobId, schemaVersion, commitSha, javaVersion, javaVendor,
                    gradleVersion, androidSdkApiLevel, buildToolsVersion,
                    apkSha256, retrievedAt
                ) VALUES (
                    'job-a', 1, '0123456789012345678901234567890123456789',
                    '21.0.12', 'Eclipse Adoptium', '8.14.3', 36, '36.0.0',
                    '${"a".repeat(64)}', '2026-08-28T00:02:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            12,
            true,
            ReproDroidDatabase.MIGRATION_11_12,
        ).use { migrated ->
            migrated.query(
                "SELECT effectiveRecipeId, effectiveDependencyPinning, " +
                    "effectiveSourceDateEpoch, effectiveNoBuildCache, effectiveFixedLocale " +
                    "FROM jobs WHERE jobId = 'job-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("recipe", cursor.getString(0))
                assertEquals("LOCKFILE", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertNull(cursor.getString(4))
            }
            migrated.query(
                "SELECT apkSha256, sourceDateEpoch, noBuildCache, fixedLocale " +
                    "FROM build_environment_manifests WHERE jobId = 'job-a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a".repeat(64), cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertNull(cursor.getString(3))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-3c-determinism-migration-test"
    }
}
