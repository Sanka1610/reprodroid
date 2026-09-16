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
class Migration24To25Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationKeepsSystemInstallerDefaultsAndAddsAttemptAuditColumns() {
        helper.createDatabase(DATABASE_NAME, 24).apply {
            execSQL(
                """
                INSERT INTO global_settings (
                    singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                    defaultReleaseVariantPreference, defaultPreferredAbi, defaultMaxApkSizeBytes,
                    updatedAt
                ) VALUES (
                    1, 'DARK', 'VERIFICATION', 'OFFICIAL_RELEASE', 'RELEASE', 'ARM64_V8A',
                    536870912, '2026-09-16T00:00:00Z'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            25,
            true,
            ReproDroidDatabase.MIGRATION_24_25,
        ).use { migrated ->
            migrated.query(
                "SELECT installerMode, recordGooglePlayAsInstaller FROM global_settings WHERE singletonId = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(InstallerMode.SYSTEM.name, cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            listOf("install_attempts", "release_install_attempts").forEach { table ->
                migrated.query("PRAGMA table_info('$table')").use { cursor ->
                    val columns = buildMap<String, String?> {
                        while (cursor.moveToNext()) put(cursor.getString(1), cursor.getString(4))
                    }
                    assertEquals("'SYSTEM'", columns["installerMode"])
                    assertTrue("installerPackageName" in columns)
                }
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "installer-24-25"
    }
}
