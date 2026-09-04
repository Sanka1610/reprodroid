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
class Migration15To16Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationHashesLegacyObservationAndCreatesStorageTables() {
        helper.createDatabase(DATABASE_NAME, 15).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi, defaultMaxApkSizeBytes, updatedAt
                ) VALUES (1, 'DARK', 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A', 536870912, 'before')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app', 'Example', 'https://github.com/example/app',
                    'https://github.com/example/app', 'GITHUB', 'VERIFICATION',
                    'AVAILABLE', '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO app_repository_bindings (
                    registeredAppId, provider, instance, providerRepositoryId,
                    identityStatus, registrationSlot, verifiedAt
                ) VALUES ('app', 'GITHUB', 'github.com', '42', 'VERIFIED', 'PRIMARY', '2026-09-01T00:00:00Z')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_snapshots (
                    releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                    resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                    isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                    fetchedAt, selectedProviderAssetId
                ) VALUES (
                    'snapshot', 'app', 7, 'v1', '${"a".repeat(40)}', 'Version 1',
                    'https://github.com/example/app/releases/tag/v1', 'main', 0, 0, 1,
                    '2026-08-31T00:00:00Z', '2026-09-01T00:00:00Z',
                    '2026-09-01T01:00:00Z', 9
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_assets (
                    releaseAssetId, releaseSnapshotId, providerAssetId, assetName,
                    stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                    providerDigestSha256, downloadStatus, comparisonEligibility, updateStatus
                ) VALUES (
                    'asset', 'snapshot', 9, 'app.apk',
                    'https://github.com/example/app/releases/download/v1/app.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 123,
                    '${"b".repeat(64)}', 'NOT_DOWNLOADED', 'NOT_EVALUATED', 'NOT_EVALUATED'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            16,
            true,
            ReproDroidDatabase.MIGRATION_15_16,
        ).use { migrated ->
            val expected = ReleaseObservationHasher.sha256(
                ReleaseObservationInput(
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = "42",
                    providerReleaseId = 7,
                    tagName = "v1",
                    resolvedCommitSha = "a".repeat(40),
                    targetCommitishRaw = "main",
                    releaseName = "Version 1",
                    releaseUrl = "https://github.com/example/app/releases/tag/v1",
                    isDraft = false,
                    isPrerelease = false,
                    isImmutable = true,
                    releaseCreatedAt = "2026-08-31T00:00:00Z",
                    publishedAt = "2026-09-01T00:00:00Z",
                    providerAssetId = 9,
                    assetName = "app.apk",
                    stableAssetUrl = "https://github.com/example/app/releases/download/v1/app.apk",
                    contentType = "application/vnd.android.package-archive",
                    providerSizeBytes = 123,
                    providerDigestSha256 = "b".repeat(64),
                    selectionReason = "SINGLE_APK",
                ),
            )
            migrated.query(
                "SELECT observationSha256, lastObservedAt FROM release_snapshots WHERE releaseSnapshotId = 'snapshot'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expected, cursor.getString(0))
                assertEquals("2026-09-01T01:00:00Z", cursor.getString(1))
            }
            migrated.query(
                "SELECT androidStorageBudgetBytes, storageWarningPercent FROM global_settings WHERE singletonId = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4L * 1024 * 1024 * 1024, cursor.getLong(0))
                assertEquals(80, cursor.getInt(1))
            }
            val expectedTables = setOf(
                "resource_availability",
                "storage_reservations",
                "retention_holds",
                "cleanup_runs",
                "cleanup_items",
                "audit_exports",
            )
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                val actual = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(actual.containsAll(expectedTables))
            }
            migrated.query("PRAGMA index_list('release_snapshots')").use { cursor ->
                var oldIndexFound = false
                var observationIndexFound = false
                while (cursor.moveToNext()) {
                    when (cursor.getString(1)) {
                        "index_release_snapshots_registeredAppId_providerReleaseId" -> oldIndexFound = true
                        "index_release_snapshots_registeredAppId_observationSha256" -> {
                            observationIndexFound = true
                            assertEquals(1, cursor.getInt(2))
                        }
                    }
                }
                assertFalse(oldIndexFound)
                assertTrue(observationIndexFound)
            }
        }
    }

    @Test
    fun fullMigrationChainReachesSchemaSixteen() {
        helper.createDatabase("phase-4-storage-full-chain", 1).close()
        helper.runMigrationsAndValidate(
            "phase-4-storage-full-chain",
            16,
            true,
            ReproDroidDatabase.MIGRATION_1_2,
            ReproDroidDatabase.MIGRATION_2_3,
            ReproDroidDatabase.MIGRATION_3_4,
            ReproDroidDatabase.MIGRATION_4_5,
            ReproDroidDatabase.MIGRATION_5_6,
            ReproDroidDatabase.MIGRATION_6_7,
            ReproDroidDatabase.MIGRATION_7_8,
            ReproDroidDatabase.MIGRATION_8_9,
            ReproDroidDatabase.MIGRATION_9_10,
            ReproDroidDatabase.MIGRATION_10_11,
            ReproDroidDatabase.MIGRATION_11_12,
            ReproDroidDatabase.MIGRATION_12_13,
            ReproDroidDatabase.MIGRATION_13_14,
            ReproDroidDatabase.MIGRATION_14_15,
            ReproDroidDatabase.MIGRATION_15_16,
        ).close()
    }

    @Test
    fun migrationPreservesLegacyObservationWithoutSelectedAsset() {
        val databaseName = "migration-15-16-no-selected-asset"
        helper.createDatabase(databaseName, 15).apply {
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'assetless-app', 'Assetless', 'https://github.com/example/assetless',
                    'https://github.com/example/assetless', 'GITHUB', 'VERIFICATION',
                    'NO_MATCHING_ASSET', '2026-09-01T00:00:00Z', '2026-09-01T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_snapshots (
                    releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                    resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                    isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                    fetchedAt, selectedProviderAssetId
                ) VALUES (
                    'assetless-snapshot', 'assetless-app', 8, 'v2', '${"c".repeat(40)}', 'Version 2',
                    'https://github.com/example/assetless/releases/tag/v2', 'main', 0, 0, 0,
                    '2026-09-01T00:00:00Z', '2026-09-01T00:30:00Z',
                    '2026-09-01T01:00:00Z', NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            16,
            true,
            ReproDroidDatabase.MIGRATION_15_16,
        ).use { migrated ->
            val expected = ReleaseObservationHasher.sha256(
                ReleaseObservationInput(
                    provider = "GITHUB",
                    instance = "github.com",
                    providerRepositoryId = "https://github.com/example/assetless",
                    providerReleaseId = 8,
                    tagName = "v2",
                    resolvedCommitSha = "c".repeat(40),
                    targetCommitishRaw = "main",
                    releaseName = "Version 2",
                    releaseUrl = "https://github.com/example/assetless/releases/tag/v2",
                    isDraft = false,
                    isPrerelease = false,
                    isImmutable = false,
                    releaseCreatedAt = "2026-09-01T00:00:00Z",
                    publishedAt = "2026-09-01T00:30:00Z",
                    providerAssetId = null,
                    assetName = null,
                    stableAssetUrl = null,
                    contentType = null,
                    providerSizeBytes = null,
                    providerDigestSha256 = null,
                    selectionReason = null,
                ),
            )
            migrated.query(
                "SELECT observationSha256, lastObservedAt, selectedProviderAssetId " +
                    "FROM release_snapshots WHERE releaseSnapshotId = 'assetless-snapshot'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expected, cursor.getString(0))
                assertEquals("2026-09-01T01:00:00Z", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-15-16"
    }
}
