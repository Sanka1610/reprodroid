package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration23To24Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesExistingUsersAndAddsPhase55PlusDefaults() {
        helper.createDatabase(DATABASE_NAME, 23).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, dynamicColorEnabled, showOperationHints,
                    defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi,
                    defaultMaxApkSizeBytes, androidStorageBudgetBytes,
                    storageWarningPercent, updatedAt
                ) VALUES (
                    1, 'DARK', 1, 1, 'VERIFICATION', 'OFFICIAL_RELEASE',
                    'RELEASE', 'ARM64_V8A', 536870912, 4294967296, 80,
                    '2026-09-14T00:00:00Z'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO release_check_settings (
                    singletonId, enabled, scheduleMode, intervalHours,
                    dailyLocalMinute, releaseChannel, networkPolicy,
                    batteryPolicy, revision, updatedAt
                ) VALUES (
                    1, 1, 'INTERVAL', 6, 420, 'STABLE_ONLY',
                    'UNMETERED_ONLY', 'ANY', 1, '2026-09-14T00:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            24,
            true,
            ReproDroidDatabase.MIGRATION_23_24,
        ).use { migrated ->
            migrated.query(
                """
                SELECT showSettingsDividers, showSelectionBoxOutlines,
                       settingsExpandedSections, notificationPermissionPrompted,
                       selfRegistrationInitialized
                FROM global_settings WHERE singletonId = 1
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                // Migration must not inject self-registration into an existing database.
                assertEquals(1, cursor.getInt(4))
            }
            migrated.query(
                "SELECT requiresCharging, releaseNotificationsEnabled FROM release_check_settings WHERE singletonId = 1",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
            }
            migrated.query("PRAGMA table_info(app_release_check_overrides)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "requiresCharging") {
                        assertNull(cursor.getString(defaultIndex))
                        return@use
                    }
                }
                throw AssertionError("requiresCharging column was not added")
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-5-5-plus-migration-test"
    }
}
