package com.sanka1610.reprodroid.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveRegistrationTest {
    @Test
    fun publicGitHubSourceOnlyRegistrationSurvivesColdDatabaseOpen() = runBlocking {
        val repositoryUrl = getArguments().getString(ARGUMENT_URL).orEmpty()
        assumeTrue("Pass -e $ARGUMENT_URL to run live GitHub registration", repositoryUrl.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val appId: String
        val firstDatabase = Room.databaseBuilder(context, ReproDroidDatabase::class.java, DATABASE_NAME).build()
        try {
            val database = firstDatabase
            val jobs = JobRepository(context, database, RunnerApiClient(""))
            val repository = ManagedAppRepository(context, database, jobs)
            repository.ensureSettings()
            val preview = repository.previewRepository(repositoryUrl)
            assertTrue(preview.identity.providerRepositoryId.toLong() > 0)
            assertTrue(preview.discovery.resolvedCommitSha?.matches(Regex("[0-9a-f]{40}")) == true)
            assertTrue(
                "${preview.discovery.state}: ${preview.discovery.reason}: ${preview.discovery.diagnostic}",
                preview.discovery.state in setOf("COMPLETE", "INCOMPLETE"),
            )
            appId = repository.registerRepository(
                preview,
                ManagementMode.VERIFICATION,
                InstallationSource.OFFICIAL_RELEASE,
            )
            val saved = repository.saveBuildConfiguration(
                appId,
                expectedRevision = 1,
                input = BuildConfigurationInput(
                    buildRoot = preview.discovery.candidates.firstOrNull()?.buildRoot ?: ".",
                    modulePath = ":app",
                    variant = "release",
                    tasks = listOf(":app:assembleRelease"),
                    javaMajor = 21,
                    gradleVersion = "9.1.0",
                    compileSdk = 36,
                    buildToolsVersion = "36.0.0",
                ),
            )
            assertEquals("CONFIGURED", saved.validationState)
        } finally {
            firstDatabase.close()
        }

        val reopened = Room.databaseBuilder(context, ReproDroidDatabase::class.java, DATABASE_NAME).build()
        try {
            val record = reopened.managedAppDao().getRegisteredAppRecord(appId)
            assertNotNull(record)
            assertEquals("VERIFIED", record?.repositoryBinding?.identityStatus)
            assertEquals("CONFIGURED", record?.selectedBuildConfiguration?.validationState)
            assertTrue(record?.releases.orEmpty().isEmpty())
            assertEquals(0, rowCount(reopened, "jobs"))
            assertEquals(0, rowCount(reopened, "release_assets"))
        } finally {
            reopened.close()
        }
        context.deleteDatabase(DATABASE_NAME)
        Unit
    }

    private fun rowCount(database: ReproDroidDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val ARGUMENT_URL = "phase4LiveRepositoryUrl"
        const val DATABASE_NAME = "phase4-live-registration.sqlite3"
    }
}
