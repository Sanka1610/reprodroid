package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration19To20Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationConvertsProviderIdsToTextAndPreservesDependentRows() {
        helper.createDatabase(DATABASE_NAME, 19).apply {
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job', 'SIMULATED', 'https://github.com/example/project', 'TAG', 'v1',
                    'SUCCEEDED', 100, 0, '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app', 'Example', 'https://github.com/example/project',
                    'https://github.com/example/project', 'GITHUB', 'VERIFICATION',
                    'AVAILABLE', '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_snapshots (
                    releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                    resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                    isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                    fetchedAt, observationSha256, lastObservedAt, selectedProviderAssetId
                ) VALUES (
                    'snapshot', 'app', 9007199254740991, 'v1', '${"a".repeat(40)}', 'Version 1',
                    'https://github.com/example/project/releases/tag/v1', 'main', 0, 0, 1,
                    '2026-08-31T00:00:00Z', '2026-09-01T00:00:00Z',
                    '2026-09-01T01:00:00Z', '${"b".repeat(64)}', '2026-09-01T01:00:00Z', 9007199254740990
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_assets (
                    releaseAssetId, releaseSnapshotId, providerAssetId, assetName,
                    stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                    providerDigestSha256, downloadStatus, comparisonEligibility
                ) VALUES (
                    'asset', 'snapshot', 9007199254740989, 'project.apk',
                    'https://github.com/example/project/releases/download/v1/project.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 1024,
                    '${"c".repeat(64)}', 'NOT_DOWNLOADED', 'NOT_EVALUATED'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_install_attempts (
                    attemptId, registeredAppId, releaseAssetId, packageInstallerSessionId,
                    status, packageInstallerStatus, statusMessage, createdAt, updatedAt
                ) VALUES (
                    'attempt', 'app', 'asset', NULL, 'PREPARING', NULL, NULL,
                    '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO comparison_runs (
                    comparisonRunId, registeredAppId, releaseSnapshotId, referenceAssetId,
                    runnerJobId, expectedCommitSha, expectedRecipeId, expectedVariantName,
                    status, outcome, createdAt, updatedAt
                ) VALUES (
                    'comparison', 'app', 'snapshot', 'asset', 'job', '${"a".repeat(40)}',
                    'recipe-v1', 'release', 'COMPLETED', 'MATCH',
                    '2026-09-01T02:00:00Z', '2026-09-01T02:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO comparison_entries (
                    comparisonRunId, entryName, result, referenceSizeBytes, localSizeBytes,
                    referenceSha256, localSha256
                ) VALUES (
                    'comparison', 'classes.dex', 'MATCH', 10, 10,
                    '${"d".repeat(64)}', '${"d".repeat(64)}'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            20,
            true,
            ReproDroidDatabase.MIGRATION_19_20,
        ).use { migrated ->
            migrated.query(
                """
                SELECT providerReleaseId, typeof(providerReleaseId),
                    selectedProviderAssetId, typeof(selectedProviderAssetId), observationSha256
                FROM release_snapshots WHERE releaseSnapshotId = 'snapshot'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("9007199254740991", cursor.getString(0))
                assertEquals("text", cursor.getString(1))
                assertEquals("9007199254740990", cursor.getString(2))
                assertEquals("text", cursor.getString(3))
                assertEquals("b".repeat(64), cursor.getString(4))
            }
            migrated.query(
                "SELECT providerAssetId, typeof(providerAssetId) FROM release_assets WHERE releaseAssetId = 'asset'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("9007199254740989", cursor.getString(0))
                assertEquals("text", cursor.getString(1))
            }
            migrated.query(
                "SELECT status FROM release_install_attempts WHERE attemptId = 'attempt'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PREPARING", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            migrated.query(
                "SELECT outcome, expectedRecipeId FROM comparison_runs WHERE comparisonRunId = 'comparison'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MATCH", cursor.getString(0))
                assertEquals("recipe-v1", cursor.getString(1))
            }
            migrated.query(
                "SELECT result, referenceSha256, localSha256 FROM comparison_entries " +
                    "WHERE comparisonRunId = 'comparison' AND entryName = 'classes.dex'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MATCH", cursor.getString(0))
                assertEquals("d".repeat(64), cursor.getString(1))
                assertEquals("d".repeat(64), cursor.getString(2))
            }
            val expectedTables = setOf(
                "release_check_settings",
                "app_release_check_overrides",
                "release_schedule_states",
                "release_check_runs",
                "release_candidates",
                "notification_outbox",
                "provider_cooldowns",
                "notification_dedup_headers",
                "provider_representations",
            )
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                val actual = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(actual.containsAll(expectedTables))
            }
            migrated.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun migrationStopsOnNonPositiveLegacyProviderId() {
        helper.createDatabase(INVALID_DATABASE_NAME, 19).apply {
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'invalid-app', 'Invalid', 'https://github.com/example/invalid',
                    'https://github.com/example/invalid', 'GITHUB', 'VERIFICATION',
                    'AVAILABLE', '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_snapshots (
                    releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                    resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                    isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                    fetchedAt, observationSha256, lastObservedAt, selectedProviderAssetId
                ) VALUES (
                    'invalid-snapshot', 'invalid-app', 0, 'v1', '${"a".repeat(40)}', 'Invalid',
                    'https://github.com/example/invalid/releases/tag/v1', 'main', 0, 0, 0,
                    '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z',
                    '2026-09-01T00:00:00Z', '', '2026-09-01T00:00:00Z', NULL
                )
                """.trimIndent(),
            )
            close()
        }

        assertThrows(IllegalStateException::class.java) {
            helper.runMigrationsAndValidate(
                INVALID_DATABASE_NAME,
                20,
                true,
                ReproDroidDatabase.MIGRATION_19_20,
            ).close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-19-20-provider-ids"
        const val INVALID_DATABASE_NAME = "migration-19-20-invalid-provider-id"
    }
}
