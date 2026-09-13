package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesSettingsAndEnablesHintsByDefault() {
        helper.createDatabase(DATABASE_NAME, 22).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, dynamicColorEnabled, defaultManagementMode,
                    defaultInstallationSource, defaultReleaseVariantPreference, defaultPreferredAbi,
                    defaultMaxApkSizeBytes, androidStorageBudgetBytes, storageWarningPercent, updatedAt
                ) VALUES (
                    1, 'LIGHT', 0, 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A',
                    536870912, 4294967296, 80, '2026-09-13T00:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            23,
            true,
            ReproDroidDatabase.MIGRATION_22_23,
        ).use { migrated ->
            migrated.query(
                "SELECT themeMode, dynamicColorEnabled, showOperationHints FROM global_settings WHERE singletonId = 1",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("LIGHT", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "phase-5-2-migration-test"
    }
}
