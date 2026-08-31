package com.sanka1610.reprodroid.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ReproDroidDatabase::class.java)

    @Test fun fullMigrationChainReachesSchemaFourteen() {
        helper.createDatabase("sandbox-full-chain", 1).close()
        helper.runMigrationsAndValidate("sandbox-full-chain", 14, true,
            ReproDroidDatabase.MIGRATION_1_2, ReproDroidDatabase.MIGRATION_2_3, ReproDroidDatabase.MIGRATION_3_4,
            ReproDroidDatabase.MIGRATION_4_5, ReproDroidDatabase.MIGRATION_5_6, ReproDroidDatabase.MIGRATION_6_7,
            ReproDroidDatabase.MIGRATION_7_8, ReproDroidDatabase.MIGRATION_8_9, ReproDroidDatabase.MIGRATION_9_10,
            ReproDroidDatabase.MIGRATION_10_11, ReproDroidDatabase.MIGRATION_11_12, ReproDroidDatabase.MIGRATION_12_13,
            ReproDroidDatabase.MIGRATION_13_14,
        ).close()
    }

    @Test fun legacyJobsRemainUnavailableAndEvidenceIsPreserved() {
        helper.createDatabase("sandbox-migration", 13).apply {
            execSQL("""INSERT INTO jobs(jobId,executionMode,repositoryUrl,revisionType,revisionValue,state,progressPercent,latestLogSequence,createdAt,updatedAt)
                VALUES('job','REAL_TRUSTED','https://github.com/example/app','TAG','1.0','SUCCEEDED',100,1,'before','before')""")
            execSQL("""INSERT INTO build_environment_manifests(jobId,schemaVersion,commitSha,javaVersion,javaVendor,gradleVersion,androidSdkApiLevel,buildToolsVersion,apkSha256,retrievedAt)
                VALUES('job',2,'commit','18','vendor','8.14.3',36,'36.0.0','apk','before')""")
            close()
        }
        helper.runMigrationsAndValidate("sandbox-migration", 14, true, ReproDroidDatabase.MIGRATION_13_14).use { database ->
            database.query("SELECT state,sandboxMode,sandboxOrigin,sandboxProfileId,sandboxCleanupStatus,sandboxResponseSeen FROM jobs WHERE jobId='job'").use {
                assertTrue(it.moveToFirst())
                assertEquals("SUCCEEDED", it.getString(0))
                (1..4).forEach { index -> assertTrue(it.isNull(index)) }
                assertEquals(1, it.getInt(5))
            }
            database.query("SELECT schemaVersion,apkSha256,sandboxJson FROM build_environment_manifests WHERE jobId='job'").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
                assertEquals("apk", it.getString(1))
                assertTrue(it.isNull(2))
            }
        }
    }
}
