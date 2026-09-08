package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration21To22Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesAllReleaseDependentsAndLegacyObservationHash() {
        helper.createDatabase(DATABASE_NAME, 21).apply {
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-22', 'SIMULATED', 'https://github.com/example/project', 'TAG', 'v1',
                    'SUCCEEDED', 100, 0, '2026-09-08T00:00:00Z', '2026-09-08T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app-22', 'Migration 22 fixture', 'https://github.com/example/project',
                    'https://github.com/example/project', 'GITHUB', 'VERIFICATION',
                    'AVAILABLE', '2026-09-08T00:00:00Z', '2026-09-08T00:00:00Z'
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
                    'snapshot-22', 'app-22', 'release-22', 'v1', '${"a".repeat(40)}',
                    'Version 1', 'https://github.com/example/project/releases/tag/v1', 'main',
                    0, 0, 1, '2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z',
                    '2026-09-08T00:00:00Z', '${"b".repeat(64)}', '2026-09-08T00:00:00Z', NULL
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
                    'asset-22', 'snapshot-22', 'asset-provider-22', 'project.apk',
                    'https://github.com/example/project/releases/download/v1/project.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 1024,
                    '${"c".repeat(64)}', 'VERIFIED', 'READY_FOR_COMPARISON'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_install_attempts (
                    attemptId, registeredAppId, releaseAssetId, packageInstallerSessionId,
                    status, packageInstallerStatus, statusMessage, createdAt, updatedAt
                ) VALUES (
                    'install-22', 'app-22', 'asset-22', NULL, 'SUCCEEDED', NULL, NULL,
                    '2026-09-08T00:01:00Z', '2026-09-08T00:01:00Z'
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
                    'comparison-22', 'app-22', 'snapshot-22', 'asset-22', 'job-22',
                    '${"a".repeat(40)}', 'recipe-22', 'release', 'COMPLETED', 'MATCH',
                    '2026-09-08T00:02:00Z', '2026-09-08T00:02:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO comparison_entries (
                    comparisonRunId, entryName, result, referenceSizeBytes, localSizeBytes,
                    referenceSha256, localSha256
                ) VALUES (
                    'comparison-22', 'classes.dex', 'MATCH', 1, 1, '${"d".repeat(64)}', '${"d".repeat(64)}'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO advanced_comparison_entries (
                    comparisonRunId, axis, entryName, result, leftSizeBytes, rightSizeBytes,
                    leftSha256, rightSha256
                ) VALUES (
                    'comparison-22', 'OFFICIAL_PRIMARY', 'classes.dex', 'MATCH', 1, 1,
                    '${"d".repeat(64)}', '${"d".repeat(64)}'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO apk_entry_evidence (
                    comparisonRunId, axis, entryName, category, result,
                    leftSizeBytes, rightSizeBytes, archiveMetadataChanged
                ) VALUES (
                    'comparison-22', 'OFFICIAL_PRIMARY', 'classes.dex', 'DEX', 'MATCH', 1, 1, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO advanced_comparison_summaries (
                    comparisonRunId, registeredAppId, axis, inventoryOutcome,
                    dexStructuralOutcome, manifestSemanticOutcome, resourceTableSemanticOutcome,
                    reason, entryCount, sameCount, changedCount, addedCount, missingCount,
                    semanticDifferenceCount
                ) VALUES (
                    'comparison-22', 'app-22', 'OFFICIAL_PRIMARY', 'MATCH', 'MATCH', 'MATCH', 'MATCH',
                    NULL, 1, 1, 0, 0, 0, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO semantic_difference_evidence (
                    comparisonRunId, registeredAppId, axis, component, stableKey, result
                ) VALUES (
                    'comparison-22', 'app-22', 'OFFICIAL_PRIMARY', 'MANIFEST', 'versionCode', 'MATCH'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_candidates (
                    candidateId, registeredAppId, provider, instance, providerRepositoryId,
                    providerReleaseId, tagName, resolvedCommitSha, releaseName, releaseUrl,
                    targetCommitishRaw, isPrerelease, isImmutable, releaseCreatedAt,
                    publishedAt, assetsJson, observationSha256, state, unseen, firstSeenAt,
                    lastSeenAt
                ) VALUES (
                    'candidate-22', 'app-22', 'GITHUB', 'github.com', '42', 'release-22',
                    'v1', '${"a".repeat(40)}', 'Version 1',
                    'https://github.com/example/project/releases/tag/v1', 'main', 0, 1,
                    '2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z', '[]', '${"e".repeat(64)}',
                    'NEW_RELEASE_DISCOVERED', 1, '2026-09-08T00:03:00Z', '2026-09-08T00:03:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO notification_outbox (
                    outboxId, candidateId, registeredAppId, notificationType, notificationId,
                    state, createdAt
                ) VALUES (
                    'outbox-22', 'candidate-22', 'app-22', 'NEW_RELEASE', 22,
                    'PENDING', '2026-09-08T00:04:00Z'
                )
                """.trimIndent(),
            )
            assertNoForeignKeyViolations(this)
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            22,
            true,
            ReproDroidDatabase.MIGRATION_21_22,
        ).use { migrated ->
            EXPECTED_TABLES.forEach { table -> assertRowCount(migrated, table, 1) }
            migrated.query(
                "SELECT observationSha256, observationSchemaVersion, metadataObservationSha256 " +
                    "FROM release_snapshots WHERE releaseSnapshotId = 'snapshot-22'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("b".repeat(64), cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
            }
            migrated.query(
                "SELECT contentType, providerCreatedAt, downloadContentType " +
                    "FROM release_assets WHERE releaseAssetId = 'asset-22'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("application/vnd.android.package-archive", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            migrated.query("SELECT savedAssetSelectionJson FROM registered_apps WHERE registeredAppId = 'app-22'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.isNull(0))
                }
            migrated.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
            migrated.query("PRAGMA foreign_key_list(release_assets)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("release_snapshots", cursor.getString(2))
            }
        }
    }

    private fun assertNoForeignKeyViolations(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun assertRowCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        expected: Int,
    ) {
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-21-22-dependent-rows"
        val EXPECTED_TABLES = listOf(
            "release_snapshots",
            "release_assets",
            "release_install_attempts",
            "comparison_runs",
            "comparison_entries",
            "advanced_comparison_entries",
            "apk_entry_evidence",
            "advanced_comparison_summaries",
            "semantic_difference_evidence",
            "release_candidates",
            "notification_outbox",
        )
    }
}
