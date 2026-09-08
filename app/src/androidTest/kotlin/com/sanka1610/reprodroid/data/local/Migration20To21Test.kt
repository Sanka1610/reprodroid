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

/**
 * Room 20 -> 21 is metadata-only for the existing product data.  A paired
 * Runner is not synthesized from the build-time URL during this migration.
 */
@RunWith(AndroidJUnit4::class)
class Migration20To21Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesExistingProductDataAndCreatesOnlyConnectionMetadata() {
        helper.createDatabase(DATABASE_NAME, 20).apply {
            execSQL("PRAGMA foreign_keys = OFF")
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-46', 'REAL_TRUSTED', 'https://github.com/example/app', 'TAG', 'v1',
                    'SUCCEEDED', 100, 7, '2026-09-07T00:00:00Z', '2026-09-07T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app-46', 'Phase 4.6 fixture', 'https://github.com/example/app',
                    'https://github.com/example/app', 'GITHUB', 'VERIFICATION',
                    'AVAILABLE', '2026-09-07T00:00:00Z', '2026-09-07T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi, defaultMaxApkSizeBytes,
                    androidStorageBudgetBytes, storageWarningPercent, updatedAt
                ) VALUES (
                    1, 'SYSTEM', 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A',
                    536870912, 4294967296, 80, '2026-09-07T00:01:30Z'
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
                    'snapshot-46', 'app-46', '9007199254740993', 'v1', '${"a".repeat(40)}',
                    'Version 1', 'https://github.com/example/app/releases/tag/v1', 'main',
                    0, 0, 1, '2026-09-06T00:00:00Z', '2026-09-06T01:00:00Z',
                    '2026-09-07T00:00:00Z', '${"b".repeat(64)}', '2026-09-07T00:00:00Z', NULL
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
                    'asset-46', 'snapshot-46', '9007199254740994', 'app-release.apk',
                    'https://github.com/example/app/releases/download/v1/app-release.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 1024,
                    '${"c".repeat(64)}', 'NOT_DOWNLOADED', 'NOT_EVALUATED'
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
                    'comparison-46', 'app-46', 'snapshot-46', 'asset-46', 'job-46',
                    '${"a".repeat(40)}', 'recipe-46', 'release', 'COMPLETED', 'MATCH',
                    '2026-09-07T00:02:00Z', '2026-09-07T00:03:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_check_settings (
                    singletonId, enabled, scheduleMode, intervalHours, dailyLocalMinute,
                    releaseChannel, networkPolicy, batteryPolicy, revision, updatedAt
                ) VALUES (1, 1, 'INTERVAL', 24, 60, 'STABLE', 'ANY', 'ANY', 3,
                    '2026-09-07T00:04:00Z')
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
                    'candidate-46', 'app-46', 'GITHUB', 'github.com', '42', '9007199254740993',
                    'v1', '${"a".repeat(40)}', 'Version 1',
                    'https://github.com/example/app/releases/tag/v1', 'main', 0, 1,
                    '2026-09-06T00:00:00Z', '2026-09-06T01:00:00Z', '[]', '${"d".repeat(64)}',
                    'NEW_RELEASE_DISCOVERED', 1, '2026-09-07T00:05:00Z', '2026-09-07T00:05:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO notification_outbox (
                    outboxId, candidateId, registeredAppId, notificationType, notificationId,
                    state, createdAt
                ) VALUES (
                    'outbox-46', 'candidate-46', 'app-46', 'NEW_RELEASE', 46,
                    'PENDING', '2026-09-07T00:06:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            21,
            true,
            ReproDroidDatabase.MIGRATION_20_21,
        ).use { migrated ->
            assertRowCount(migrated, "jobs", 1)
            assertRowCount(migrated, "registered_apps", 1)
            assertRowCount(migrated, "release_snapshots", 1)
            assertRowCount(migrated, "release_assets", 1)
            assertRowCount(migrated, "comparison_runs", 1)
            assertRowCount(migrated, "global_settings", 1)
            assertRowCount(migrated, "release_check_settings", 1)
            assertRowCount(migrated, "release_candidates", 1)
            assertRowCount(migrated, "notification_outbox", 1)

            migrated.query("SELECT runnerId FROM jobs WHERE jobId = 'job-46'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                // Room20 history has no authenticated Runner binding.  The
                // nullable migration column must not synthesize one from the
                // build-time URL or any other local default.
                assertTrue(cursor.isNull(0))
            }
            migrated.query("PRAGMA table_info('jobs')").use { cursor ->
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue(columns.contains("runnerId"))
            }

            migrated.query(
                "SELECT providerReleaseId, observationSha256 FROM release_snapshots " +
                    "WHERE releaseSnapshotId = 'snapshot-46'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("9007199254740993", cursor.getString(0))
                assertEquals("b".repeat(64), cursor.getString(1))
            }
            migrated.query(
                "SELECT state, notificationId FROM notification_outbox WHERE outboxId = 'outbox-46'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING", cursor.getString(0))
                assertEquals(46, cursor.getInt(1))
            }

            assertRowCount(migrated, "runner_connections", 0)
            migrated.query("PRAGMA table_info('runner_connections')").use { cursor ->
                val columns = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue(columns.containsAll(EXPECTED_CONNECTION_COLUMNS))
                assertFalse(columns.any { it in FORBIDDEN_SECRET_COLUMNS })
            }
            migrated.query("PRAGMA index_list('runner_connections')").use { cursor ->
                val indices = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue(indices.contains("index_runner_connections_active"))
                assertTrue(indices.contains("index_runner_connections_pairingState"))
                assertTrue(indices.contains("index_runner_connections_updatedAt"))
            }
        }
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
        const val DATABASE_NAME = "phase-4-6-room-20-21"
        val EXPECTED_CONNECTION_COLUMNS = setOf(
            "runnerId",
            "endpoint",
            "rootSpkiSha256",
            "caCertificateFileReference",
            "caCertificateSha256",
            "credentialFileReference",
            "principalId",
            "displayName",
            "transportMode",
            "pairingRequestId",
            "pairingState",
            "confirmationFingerprint",
            "pairingExpiresAt",
            "revocationKnowledge",
            "active",
            "createdAt",
            "updatedAt",
        )
        val FORBIDDEN_SECRET_COLUMNS = setOf(
            "invitationSecret",
            "bearerSecret",
            "continuationSecret",
            "rawToken",
        )
    }
}
