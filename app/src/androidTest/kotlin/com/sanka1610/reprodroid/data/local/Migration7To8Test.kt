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
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesLegacyComparisonAndAddsRepeatEvidenceTables() {
        helper.createDatabase(DATABASE_NAME, 7).apply {
            execSQL(
                """
                INSERT INTO jobs (
                    jobId, executionMode, repositoryUrl, revisionType, revisionValue,
                    state, progressPercent, latestLogSequence, createdAt, updatedAt
                ) VALUES (
                    'job-a', 'REAL_TRUSTED', 'https://github.com/example/app', 'TAG', '1.0',
                    'SUCCEEDED', 100, 1, '2026-08-24T00:00:00Z', '2026-08-24T00:01:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, installationSource, releaseVariantPreference,
                    preferredAbi, maxApkSizeBytes, useGlobalReleaseVariant,
                    useGlobalPreferredAbi, useGlobalMaxApkSize, releaseDiscoveryStatus,
                    createdAt, updatedAt
                ) VALUES (
                    'app', 'Example', 'https://github.com/example/app',
                    'https://github.com/example/app', 'PUBLIC_GITHUB_RELEASES', 'VERIFICATION',
                    'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A', 536870912, 0, 0, 1,
                    'AVAILABLE', '2026-08-24T00:00:00Z', '2026-08-24T00:00:00Z'
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
                    'release', 'app', 1, '1.0', '0123456789012345678901234567890123456789',
                    '1.0', 'https://github.com/example/app/releases/tag/1.0', 'main',
                    0, 0, 0, '2026-08-24T00:00:00Z', '2026-08-24T00:00:00Z',
                    '2026-08-24T00:00:00Z', 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_assets (
                    releaseAssetId, releaseSnapshotId, providerAssetId, assetName,
                    stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                    downloadStatus, comparisonEligibility, updateStatus
                ) VALUES (
                    'asset', 'release', 1, 'example.apk',
                    'https://github.com/example/app/releases/download/1.0/example.apk',
                    'SINGLE_APK', 'application/vnd.android.package-archive', 123,
                    'VERIFIED', 'READY_FOR_COMPARISON', 'NOT_EVALUATED'
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
                    'comparison', 'app', 'release', 'asset', 'job-a',
                    '0123456789012345678901234567890123456789', 'recipe', 'release',
                    'COMPLETED', 'MATCH', '2026-08-24T00:00:00Z', '2026-08-24T00:01:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            8,
            true,
            ReproDroidDatabase.MIGRATION_7_8,
        ).use { migrated ->
            migrated.query(
                """
                SELECT protocolVersion, repeatRunnerJobId, repeatOfficialOutcome,
                       repeatabilityOutcome, repeatIncomparableReason
                FROM comparison_runs WHERE comparisonRunId = 'comparison'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
                assertEquals("NOT_EVALUATED", cursor.getString(2))
                assertEquals("NOT_EVALUATED", cursor.getString(3))
                assertTrue(cursor.isNull(4))
            }
            migrated.query("SELECT COUNT(*) FROM advanced_comparison_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-2d-migration-test"
    }
}
