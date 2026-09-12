package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationFullChainTo22Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun fullMigrationChainReachesCurrentSchemaWithoutAnImplicitReset() {
        helper.createDatabase(DATABASE_NAME, 1).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            22,
            true,
            *MIGRATIONS,
        ).use { migrated ->
            migrated.query("PRAGMA user_version").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 22)
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "phase-4-full-chain-to-22"
        private val MIGRATIONS = arrayOf(
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
            ReproDroidDatabase.MIGRATION_16_17,
            ReproDroidDatabase.MIGRATION_17_18,
            ReproDroidDatabase.MIGRATION_18_19,
            ReproDroidDatabase.MIGRATION_19_20,
            ReproDroidDatabase.MIGRATION_20_21,
            ReproDroidDatabase.MIGRATION_21_22,
        )
    }
}
