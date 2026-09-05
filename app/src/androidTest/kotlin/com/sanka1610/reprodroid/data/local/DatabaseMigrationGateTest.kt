package com.sanka1610.reprodroid.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationGateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "migration-gate-test.sqlite3"

    @Before
    fun prepareCleanTestState() {
        cleanTestState()
    }

    @After
    fun cleanTestState() {
        context.deleteDatabase(databaseName)
        File(context.noBackupFilesDir, "migration-snapshots").deleteRecursively()
    }

    @Test
    fun quiescentRoomDatabaseProducesVerifiedPrivateSnapshot() {
        createRoomDatabase(version = 14)

        DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)

        val snapshots = File(context.noBackupFilesDir, "migration-snapshots")
            .listFiles()
            .orEmpty()
            .filter { it.extension == "sqlite3" }
        assertEquals(1, snapshots.size)
        val checksum = File(snapshots.single().parentFile, "${snapshots.single().name}.sha256")
        assertTrue(checksum.isFile)
        assertTrue(checksum.delete())
        DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
        assertTrue(checksum.isFile)
        SQLiteDatabase.openDatabase(
            snapshots.single().absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(14, cursor.getInt(0))
            }
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        }
    }

    @Test
    fun unknownFutureSchemaStopsWithoutCreatingSnapshot() {
        createRoomDatabase(version = 99)

        assertThrows(IllegalStateException::class.java) {
            DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
        }
        val snapshots = File(context.noBackupFilesDir, "migration-snapshots")
            .listFiles()
            .orEmpty()
            .filter { it.extension == "sqlite3" }
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun storageBudgetAndFreeSpaceFailuresKeepOriginalAndCreateNoSnapshot() {
        listOf(
            MigrationGatePolicy(storageBudgetBytes = 0),
            MigrationGatePolicy(usableSpace = { 0 }),
        ).forEach { policy ->
            cleanTestState()
            createRoomDatabase(version = 14)

            assertThrows(IllegalStateException::class.java) {
                DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15, policy = policy)
            }

            assertEquals(14, databaseVersion(context.getDatabasePath(databaseName)))
            assertTrue(snapshotFiles().isEmpty())
        }
    }

    @Test
    fun corruptedExistingSnapshotFailsClosedAndKeepsOriginal() {
        createRoomDatabase(version = 14)
        DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
        val snapshot = snapshotFiles().single()
        snapshot.outputStream().use { output -> output.write(byteArrayOf(0x00)) }

        assertThrows(IllegalStateException::class.java) {
            DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
        }

        assertEquals(14, databaseVersion(context.getDatabasePath(databaseName)))
        assertTrue(snapshot.isFile)
    }

    @Test
    fun eachSnapshotCutPointCanResumeWithoutReplacingTheOriginal() {
        MigrationGateCutPoint.entries.forEach { interruptedPoint ->
            cleanTestState()
            createRoomDatabase(version = 14)
            val interruption = IllegalStateException("simulated process interruption at $interruptedPoint")

            val thrown = assertThrows(IllegalStateException::class.java) {
                DatabaseMigrationGate.prepare(
                    context,
                    databaseName,
                    targetVersion = 15,
                    policy = MigrationGatePolicy(
                        onCutPoint = { point -> if (point == interruptedPoint) throw interruption },
                    ),
                )
            }
            assertTrue(thrown === interruption)
            assertEquals(14, databaseVersion(context.getDatabasePath(databaseName)))

            DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
            val snapshot = snapshotFiles().single()
            assertEquals(14, databaseVersion(snapshot))
            assertTrue(File(snapshot.parentFile, "${snapshot.name}.sha256").isFile)
        }
    }

    @Test
    fun busyWalReaderStopsCheckpointAndMigrationCanResumeAfterReaderCloses() {
        createRoomDatabase(version = 14)
        val file = context.getDatabasePath(databaseName)
        val writer = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val reader = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            assertTrue(writer.enableWriteAheadLogging())
            assertTrue(reader.enableWriteAheadLogging())
            writer.execSQL("CREATE TABLE migration_gate_payload(value TEXT NOT NULL)")
            reader.beginTransactionReadOnly()
            reader.rawQuery("SELECT COUNT(*) FROM migration_gate_payload", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            writer.execSQL("INSERT INTO migration_gate_payload(value) VALUES('after-reader')")

            assertThrows(IllegalStateException::class.java) {
                DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
            }
            assertTrue(snapshotFiles().isEmpty())
        } finally {
            if (reader.inTransaction()) reader.endTransaction()
            reader.close()
            writer.close()
        }

        DatabaseMigrationGate.prepare(context, databaseName, targetVersion = 15)
        assertEquals(1, snapshotFiles().size)
        assertEquals(14, databaseVersion(context.getDatabasePath(databaseName)))
    }

    private fun createRoomDatabase(version: Int) {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            database.execSQL(
                "INSERT INTO room_master_table(id, identity_hash) VALUES(42, 'room-identity')",
            )
            database.execSQL("PRAGMA user_version = $version")
        }
    }

    private fun snapshotFiles(): List<File> = File(context.noBackupFilesDir, "migration-snapshots")
        .listFiles()
        .orEmpty()
        .filter { it.extension == "sqlite3" }

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
}
