package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration18To19Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesAppsAndAddsUiRDefaults() {
        helper.createDatabase(DATABASE_NAME, 18).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi, defaultMaxApkSizeBytes,
                    androidStorageBudgetBytes, storageWarningPercent, updatedAt
                ) VALUES (
                    1, 'SYSTEM', 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A',
                    536870912, 4294967296, 80, '2026-09-05T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, installationSource, releaseVariantPreference,
                    preferredAbi, maxApkSizeBytes, useGlobalReleaseVariant, useGlobalPreferredAbi,
                    useGlobalMaxApkSize, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'app', 'Original', 'https://github.com/example/app',
                    'https://github.com/example/app', 'GITHUB', 'VERIFICATION',
                    'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A', 536870912, 1, 1, 1,
                    'NOT_CHECKED', '2026-09-05T00:00:00Z', '2026-09-05T00:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            19,
            true,
            ReproDroidDatabase.MIGRATION_18_19,
        ).use { migrated ->
            migrated.query(
                """
                SELECT displayName, displayNameOverride, authorDisplayOverride, note, groupId,
                    trackingState, trackingStoppedAt
                FROM registered_apps WHERE registeredAppId = 'app'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Original", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertNull(cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertNull(cursor.getString(4))
                assertEquals(AppTrackingState.ACTIVE.name, cursor.getString(5))
                assertNull(cursor.getString(6))
            }
            migrated.query(
                "SELECT dynamicColorEnabled FROM global_settings WHERE singletonId = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM app_groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("PRAGMA index_list('registered_apps')").use { cursor ->
                val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertTrue("index_registered_apps_groupId" in names)
                assertTrue("index_registered_apps_trackingState" in names)
            }
        }
    }

    @Test
    fun groupDeleteRequiresAssignmentsToBeClearedByRepositoryTransaction() {
        helper.createDatabase("ui-r-group-delete", 18).close()
        helper.runMigrationsAndValidate(
            "ui-r-group-delete",
            19,
            true,
            ReproDroidDatabase.MIGRATION_18_19,
        ).use { migrated ->
            migrated.execSQL(
                """
                INSERT INTO app_groups(groupId, displayName, sortOrder, createdAt, updatedAt)
                VALUES('00000000-0000-0000-0000-000000000001', 'Tools', 0, 'now', 'now')
                """.trimIndent(),
            )
            migrated.query("SELECT displayName FROM app_groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Tools", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "ui-r-18-19"
    }
}
