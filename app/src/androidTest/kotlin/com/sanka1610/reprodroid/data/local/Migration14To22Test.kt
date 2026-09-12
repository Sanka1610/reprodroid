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
class Migration14To22Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun phaseThreeBaselinePreservesRegistrationAndSettingsThroughRoomTwentyTwo() {
        helper.createDatabase(DATABASE_NAME, 14).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi, defaultMaxApkSizeBytes, updatedAt
                ) VALUES (1, 'LIGHT', 'ACQUISITION', 'LOCAL_BUILD', 'PREVIEW', 'X86_64', 268435456,
                    '2026-08-30T00:00:00Z')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, installationSource, releaseVariantPreference,
                    preferredAbi, maxApkSizeBytes, useGlobalReleaseVariant, useGlobalPreferredAbi,
                    useGlobalMaxApkSize, releaseDiscoveryStatus, releaseMetadataEtag,
                    lastReleaseCheckedAt, createdAt, updatedAt
                ) VALUES (
                    '00000000-0000-4000-8000-000000000014', 'Phase 3 baseline',
                    'https://github.com/example/baseline', 'https://github.com/example/baseline',
                    'PUBLIC_GITHUB_RELEASES', 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE',
                    'ARM64_V8A', 134217728, 0, 0, 0, 'AVAILABLE', 'legacy-etag',
                    '2026-08-30T01:00:00Z', '2026-08-30T00:00:00Z', '2026-08-30T01:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            22,
            true,
            ReproDroidDatabase.MIGRATION_14_15,
            ReproDroidDatabase.MIGRATION_15_16,
            ReproDroidDatabase.MIGRATION_16_17,
            ReproDroidDatabase.MIGRATION_17_18,
            ReproDroidDatabase.MIGRATION_18_19,
            ReproDroidDatabase.MIGRATION_19_20,
            ReproDroidDatabase.MIGRATION_20_21,
            ReproDroidDatabase.MIGRATION_21_22,
        ).use { migrated ->
            migrated.query(
                """
                SELECT displayName, canonicalRepositoryUrl, managementMode, installationSource,
                       releaseVariantPreference, preferredAbi, maxApkSizeBytes,
                       releaseDiscoveryStatus, releaseMetadataEtag, lastReleaseCheckedAt,
                       trackingState, note
                FROM registered_apps
                WHERE registeredAppId = '00000000-0000-4000-8000-000000000014'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Phase 3 baseline", cursor.getString(0))
                assertEquals("https://github.com/example/baseline", cursor.getString(1))
                assertEquals("VERIFICATION", cursor.getString(2))
                assertEquals("OFFICIAL_RELEASE", cursor.getString(3))
                assertEquals("RELEASE", cursor.getString(4))
                assertEquals("ARM64_V8A", cursor.getString(5))
                assertEquals(134217728L, cursor.getLong(6))
                assertEquals("AVAILABLE", cursor.getString(7))
                assertEquals("legacy-etag", cursor.getString(8))
                assertEquals("2026-08-30T01:00:00Z", cursor.getString(9))
                assertEquals("ACTIVE", cursor.getString(10))
                assertEquals("", cursor.getString(11))
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
                assertEquals("LIGHT", cursor.getString(0))
                assertEquals("ACQUISITION", cursor.getString(1))
                assertEquals("LOCAL_BUILD", cursor.getString(2))
                assertEquals("PREVIEW", cursor.getString(3))
                assertEquals("X86_64", cursor.getString(4))
                assertEquals(268435456L, cursor.getLong(5))
            }
            migrated.query("SELECT COUNT(*) FROM release_check_settings").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query(
                "SELECT provider, instance, identityStatus FROM app_repository_bindings " +
                    "WHERE registeredAppId = '00000000-0000-4000-8000-000000000014'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("GITHUB", cursor.getString(0))
                assertEquals("github.com", cursor.getString(1))
                assertEquals("LEGACY_UNRESOLVED", cursor.getString(2))
            }
            migrated.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
            migrated.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(22, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-4-room-14-to-22"
    }
}
