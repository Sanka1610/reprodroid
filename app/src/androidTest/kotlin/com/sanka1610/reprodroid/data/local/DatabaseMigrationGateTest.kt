package com.sanka1610.reprodroid.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
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
}
