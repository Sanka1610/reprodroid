package com.sanka1610.reprodroid.data.local

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Two separate instrumentation invocations prove real process death and recovery.
 * Pass -e phase46MigrationKillPoint AFTER_CHECKPOINT (or another enum value),
 * execute killProcessAtRequestedSnapshotCutPoint, then execute resumeAfterRequestedSnapshotCutPointKill.
 * Every database, snapshot and marker is scoped to this test; product migration history is never deleted.
 */
@RunWith(AndroidJUnit4::class)
class Phase46MigrationKillPointTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val helper = MigrationTestHelper(instrumentation, ReproDroidDatabase::class.java)

    @Test
    fun killProcessAtRequestedSnapshotCutPoint() {
        val point = requestedPoint() ?: return
        val testRoot = testRoot(point)
        check(!testRoot.exists() && !context.getDatabasePath(databaseName(point)).exists()) {
            "Phase 4.6 cut-point evidence already exists; inspect it before starting a new run."
        }
        check(testRoot.mkdirs())
        helper.createDatabase(databaseName(point), 20).use { database ->
            database.execSQL("""
                INSERT INTO jobs(jobId,executionMode,repositoryUrl,revisionType,revisionValue,state,
                    progressPercent,latestLogSequence,createdAt,updatedAt)
                VALUES('phase46-kill-job','REAL_TRUSTED','https://github.com/example/fixture','TAG','v1',
                    'SUCCEEDED',100,7,'2026-09-08T00:00:00Z','2026-09-08T00:00:01Z')
            """.trimIndent())
            database.execSQL("""
                INSERT INTO registered_apps(registeredAppId,displayName,repositoryUrl,canonicalRepositoryUrl,
                    provider,managementMode,releaseDiscoveryStatus,createdAt,updatedAt)
                VALUES('phase46-kill-app','Retained 日本語 sentinel','https://github.com/example/fixture',
                    'https://github.com/example/fixture','GITHUB','OBSERVE_ONLY','NOT_CHECKED',
                    '2026-09-08T00:00:00Z','2026-09-08T00:00:01Z')
            """.trimIndent())
        }
        DatabaseMigrationGate.prepare(scopedContext(point), databaseName(point), 21,
            MigrationGatePolicy(onCutPoint = { reached ->
                if (reached == point) {
                    FileOutputStream(marker(point)).use { output ->
                        output.write(point.name.toByteArray(Charsets.US_ASCII))
                        output.fd.sync()
                    }
                    Process.killProcess(Process.myPid())
                    error("Process kill did not terminate instrumentation")
                }
            }),
        )
        error("Requested Phase 4.6 process-death cut point was not reached")
    }

    @Test
    fun resumeAfterRequestedSnapshotCutPointKill() {
        val point = requestedPoint() ?: return
        assertEquals(point.name, marker(point).readText())
        val source = context.getDatabasePath(databaseName(point))
        assertEquals(20, readVersion(source))
        assertSentinels(source)
        val snapshotDirectory = File(testRoot(point), "migration-snapshots")
        val snapshotsBefore = snapshotDirectory.listFiles().orEmpty().filter { it.extension == "sqlite3" }
        val partialsBefore = snapshotDirectory.listFiles().orEmpty().filter { it.extension == "part" }
        when (point) {
            MigrationGateCutPoint.AFTER_CHECKPOINT -> {
                assertTrue(snapshotsBefore.isEmpty())
                assertTrue(partialsBefore.isEmpty())
            }
            MigrationGateCutPoint.AFTER_TEMPORARY_VALIDATED -> {
                assertTrue(snapshotsBefore.isEmpty())
                assertEquals(1, partialsBefore.size)
            }
            MigrationGateCutPoint.AFTER_SNAPSHOT_MOVED -> {
                assertEquals(1, snapshotsBefore.size)
                assertFalse(File(snapshotDirectory, "${snapshotsBefore.single().name}.sha256").exists())
            }
        }
        DatabaseMigrationGate.prepare(scopedContext(point), databaseName(point), 21)
        val snapshot = snapshotDirectory.listFiles().orEmpty().single { it.extension == "sqlite3" }
        assertEquals(20, readVersion(snapshot))
        assertSentinels(snapshot)
        assertTrue(File(snapshotDirectory, "${snapshot.name}.sha256").isFile)
        assertEquals(20, readVersion(source))
        helper.runMigrationsAndValidate(databaseName(point), 21, true, ReproDroidDatabase.MIGRATION_20_21).use { migrated ->
            migrated.query("SELECT runnerId FROM jobs WHERE jobId='phase46-kill-job'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            migrated.query("SELECT count(*) FROM runner_connections").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
        }
        assertEquals(21, readVersion(source))
        assertSentinels(source)
        assertEquals(20, readVersion(snapshot))
        // Keep scoped evidence for the parent audit. No product or test evidence cleanup is implicit.
        FileOutputStream(File(testRoot(point), "verified-room21")).use { output ->
            output.write("ROOM20_PRESERVED_ROOM21_VALIDATED".toByteArray(Charsets.US_ASCII)); output.fd.sync()
        }
    }

    private fun assertSentinels(file: File) = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
        database.rawQuery("SELECT executionMode,state,latestLogSequence FROM jobs WHERE jobId='phase46-kill-job'", null).use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("REAL_TRUSTED", cursor.getString(0))
            assertEquals("SUCCEEDED", cursor.getString(1)); assertEquals(7, cursor.getInt(2)); assertFalse(cursor.moveToNext())
        }
        database.rawQuery("SELECT displayName FROM registered_apps WHERE registeredAppId='phase46-kill-app'", null).use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("Retained 日本語 sentinel", cursor.getString(0))
        }
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("ok", cursor.getString(0))
        }
    }

    private fun readVersion(file: File) = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    private fun requestedPoint(): MigrationGateCutPoint? {
        val raw = getArguments().getString(ARGUMENT)
        assumeTrue("Explicit Phase 4.6 process-death cut point is required", raw != null)
        return MigrationGateCutPoint.entries.singleOrNull { it.name == raw }
            ?: error("Unknown Phase 4.6 process-death cut point")
    }
    private fun databaseName(point: MigrationGateCutPoint) = "phase46-kill-${point.name}.sqlite3"
    private fun testRoot(point: MigrationGateCutPoint) = File(context.noBackupFilesDir, "phase46-migration-kill-tests/${point.name}")
    private fun marker(point: MigrationGateCutPoint) = File(testRoot(point), "reached-cut-point")
    private fun scopedContext(point: MigrationGateCutPoint) = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir(): File = testRoot(point)
    }
    private companion object { const val ARGUMENT = "phase46MigrationKillPoint" }
}
