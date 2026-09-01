package com.sanka1610.reprodroid.data.local

import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LiveMigrationKillPointTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun killProcessAtRequestedSnapshotCutPoint() {
        val cutPoint = requestedCutPoint()
        assumeTrue("Pass -e $ARGUMENT_CUT_POINT with a MigrationGateCutPoint name", cutPoint != null)
        cleanup()
        createRoom14Database()

        DatabaseMigrationGate.prepare(
            context,
            DATABASE_NAME,
            targetVersion = 15,
            policy = MigrationGatePolicy(
                onCutPoint = { reached ->
                    if (reached == cutPoint) {
                        FileOutputStream(markerFile(reached), false).use { output ->
                            output.write(reached.name.toByteArray(Charsets.US_ASCII))
                            output.fd.sync()
                        }
                        Process.killProcess(Process.myPid())
                        error("Process kill did not terminate instrumentation")
                    }
                },
            ),
        )
        error("Requested migration kill point was not reached: $cutPoint")
    }

    @Test
    fun resumeAfterRequestedSnapshotCutPointKill() {
        val cutPoint = requestedCutPoint()
        assumeTrue("Pass -e $ARGUMENT_CUT_POINT with a MigrationGateCutPoint name", cutPoint != null)
        assertTrue(markerFile(cutPoint!!).isFile)
        assertEquals(14, databaseVersion(context.getDatabasePath(DATABASE_NAME)))
        val snapshotDirectory = snapshotDirectory()
        val snapshotsBefore = snapshotDirectory.listFiles().orEmpty().filter { it.extension == "sqlite3" }
        val partialsBefore = snapshotDirectory.listFiles().orEmpty().filter { it.extension == "part" }
        when (cutPoint) {
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

        DatabaseMigrationGate.prepare(context, DATABASE_NAME, targetVersion = 15)

        val snapshot = snapshotDirectory.listFiles().orEmpty().single { it.extension == "sqlite3" }
        assertEquals(14, databaseVersion(snapshot))
        assertTrue(File(snapshotDirectory, "${snapshot.name}.sha256").isFile)
        assertEquals(14, databaseVersion(context.getDatabasePath(DATABASE_NAME)))
        cleanup()
    }

    private fun requestedCutPoint(): MigrationGateCutPoint? =
        getArguments().getString(ARGUMENT_CUT_POINT)?.let { raw ->
            MigrationGateCutPoint.entries.firstOrNull { it.name == raw }
        }

    private fun createRoom14Database() {
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL("INSERT INTO room_master_table(id, identity_hash) VALUES(42, 'kill-point-room14')")
            database.execSQL("PRAGMA user_version = 14")
        }
    }

    private fun databaseVersion(file: File): Int = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use { database ->
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun markerFile(point: MigrationGateCutPoint) = File(context.noBackupFilesDir, "kill-${point.name}")
    private fun snapshotDirectory() = File(context.noBackupFilesDir, "migration-snapshots")

    private fun cleanup() {
        context.deleteDatabase(DATABASE_NAME)
        snapshotDirectory().deleteRecursively()
        MigrationGateCutPoint.entries.forEach { markerFile(it).delete() }
    }

    private companion object {
        const val ARGUMENT_CUT_POINT = "phase4MigrationKillPoint"
        const val DATABASE_NAME = "phase4-live-migration-kill.sqlite3"
    }
}
