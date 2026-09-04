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
class Migration16To17Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationAddsReferenceOnlyToolchainState() {
        helper.createDatabase("phase-4-toolchain-16-17", 16).close()
        helper.runMigrationsAndValidate(
            "phase-4-toolchain-16-17",
            17,
            true,
            ReproDroidDatabase.MIGRATION_16_17,
        ).use { migrated ->
            migrated.execSQL(
                """
                INSERT INTO toolchain_installation_references(
                    runnerId,installationId,operationId,registeredAppId,buildSettingsRevision,
                    planSha256,catalogSha256,state,observedAt
                ) VALUES('${uuid(1)}','${uuid(2)}','${uuid(3)}',NULL,NULL,'${"a".repeat(64)}','${"b".repeat(64)}','INSTALLED','2026-09-05T00:00:00Z')
                """.trimIndent(),
            )
            migrated.query("SELECT state,registeredAppId FROM toolchain_installation_references").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("INSTALLED", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    private fun uuid(value: Int) = "00000000-0000-0000-0000-${value.toString().padStart(12, '0')}"
}
