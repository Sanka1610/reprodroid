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
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesManagedAppAndAppliesExplicitDefaults() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseVariantPreference, preferredAbi,
                    releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app', 'Example', 'https://github.com/example/app',
                    'https://github.com/example/app', 'PUBLIC_GITHUB_RELEASES', 'VERIFICATION',
                    'PREVIEW', 'X86_64', 'AVAILABLE',
                    '2026-08-24T00:00:00Z', '2026-08-24T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_snapshots (
                    releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                    resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                    isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt, fetchedAt,
                    selectedProviderAssetId
                ) VALUES (
                    'release', 'app', 1, '1.0', '0123456789012345678901234567890123456789',
                    '1.0', 'https://github.com/example/app/releases/tag/1.0', 'main',
                    0, 0, 0, '2026-08-24T00:00:00Z', '2026-08-24T00:00:00Z',
                    '2026-08-24T00:00:00Z', 2
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_assets (
                    releaseAssetId, releaseSnapshotId, providerAssetId, assetName,
                    stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                    downloadStatus, comparisonEligibility
                ) VALUES (
                    'asset', 'release', 2, 'example.apk',
                    'https://github.com/example/app/releases/download/1.0/example.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 123,
                    'VERIFIED', 'READY_FOR_COMPARISON'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            ReproDroidDatabase.MIGRATION_6_7,
        ).use { migrated ->
            migrated.query(
                """
                SELECT installationSource, releaseVariantPreference, preferredAbi,
                       useGlobalReleaseVariant, useGlobalPreferredAbi, useGlobalMaxApkSize,
                       maxApkSizeBytes
                FROM registered_apps WHERE registeredAppId = 'app'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("OFFICIAL_RELEASE", cursor.getString(0))
                assertEquals("PREVIEW", cursor.getString(1))
                assertEquals("X86_64", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(536_870_912L, cursor.getLong(6))
            }
            migrated.query(
                "SELECT updateStatus FROM release_assets WHERE releaseAssetId = 'asset'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("NOT_EVALUATED", cursor.getString(0))
            }
            migrated.query(
                """
                SELECT themeMode, defaultManagementMode, defaultInstallationSource,
                       defaultReleaseVariantPreference, defaultPreferredAbi,
                       defaultMaxApkSizeBytes
                FROM global_settings WHERE singletonId = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DARK", cursor.getString(0))
                assertEquals("VERIFICATION", cursor.getString(1))
                assertEquals("OFFICIAL_RELEASE", cursor.getString(2))
                assertEquals("RELEASE", cursor.getString(3))
                assertEquals("ARM64_V8A", cursor.getString(4))
                assertEquals(536_870_912L, cursor.getLong(5))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-2c-migration-test"
    }
}
