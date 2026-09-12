package com.sanka1610.reprodroid.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ArchivedRoom15To22Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun archivedRuntimeRoomFifteenPreservesEveryLegacyValueThroughRoomTwentyTwo() {
        val sourcePath = InstrumentationRegistry.getArguments().getString(ARCHIVE_ARGUMENT).orEmpty()
        assumeTrue("Pass -e $ARCHIVE_ARGUMENT with the archived runtime Room 15 database", sourcePath.isNotBlank())
        val source = File(sourcePath)
        assertTrue("The archived Room 15 database is not readable: $sourcePath", source.isFile && source.canRead())
        assertEquals(EXPECTED_ARCHIVE_SHA256, source.sha256())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val target = context.getDatabasePath(DATABASE_NAME)
        target.parentFile?.mkdirs()
        source.inputStream().use { input -> target.outputStream().use(input::copyTo) }

        try {
            val before = SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                assertEquals(15, database.userVersion())
                assertEquals("ok", database.integrityResult())
                assertFalse(database.rawQuery("PRAGMA foreign_key_check", null).moveToFirstAndClose())
                database.legacyTableSnapshots()
            }

            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                22,
                true,
                ReproDroidDatabase.MIGRATION_15_16,
                ReproDroidDatabase.MIGRATION_16_17,
                ReproDroidDatabase.MIGRATION_17_18,
                ReproDroidDatabase.MIGRATION_18_19,
                ReproDroidDatabase.MIGRATION_19_20,
                ReproDroidDatabase.MIGRATION_20_21,
                ReproDroidDatabase.MIGRATION_21_22,
            ).close()

            SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                assertEquals(22, database.userVersion())
                assertEquals("ok", database.integrityResult())
                assertFalse(database.rawQuery("PRAGMA foreign_key_check", null).moveToFirstAndClose())
                before.forEach { (table, snapshot) ->
                    assertEquals(
                        "Migration changed legacy values in $table",
                        snapshot,
                        database.snapshot(table, snapshot.columns),
                    )
                }
                database.rawQuery(
                    "SELECT canonicalRepositoryUrl, trackingState FROM registered_apps",
                    null,
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("https://github.com/example/project", cursor.getString(0))
                    assertEquals("ACTIVE", cursor.getString(1))
                    assertFalse(cursor.moveToNext())
                }
                database.rawQuery("SELECT COUNT(*) FROM app_repository_bindings", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun SQLiteDatabase.legacyTableSnapshots(): Map<String, TableSnapshot> {
        val tables = rawQuery(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
              AND name NOT IN ('android_metadata', 'room_master_table')
            ORDER BY name
            """.trimIndent(),
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return tables.associateWith { table ->
            val columns = rawQuery("PRAGMA table_info(${table.quoted()})", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            snapshot(table, columns)
        }
    }

    private fun SQLiteDatabase.snapshot(table: String, columns: List<String>): TableSnapshot {
        val projection = columns.joinToString(",") { it.quoted() }
        val rows = rawQuery("SELECT $projection FROM ${table.quoted()}", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).joinToString("|") { index -> cursor.encoded(index) })
                }
            }.sorted()
        }
        return TableSnapshot(columns, rows)
    }

    private fun Cursor.encoded(index: Int): String = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "N"
        Cursor.FIELD_TYPE_INTEGER -> "I:${getLong(index)}"
        Cursor.FIELD_TYPE_FLOAT -> "F:${java.lang.Double.toHexString(getDouble(index))}"
        Cursor.FIELD_TYPE_STRING -> "S:${Base64.encodeToString(getString(index).toByteArray(), Base64.NO_WRAP)}"
        Cursor.FIELD_TYPE_BLOB -> "B:${Base64.encodeToString(getBlob(index), Base64.NO_WRAP)}"
        else -> error("Unknown cursor field type")
    }

    private fun String.quoted(): String = "\"${replace("\"", "\"\"")}\""

    private fun SQLiteDatabase.userVersion(): Int =
        rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SQLiteDatabase.integrityResult(): String =
        rawQuery("PRAGMA integrity_check", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun Cursor.moveToFirstAndClose(): Boolean = use { moveToFirst() }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class TableSnapshot(
        val columns: List<String>,
        val rows: List<String>,
    )

    private companion object {
        const val ARCHIVE_ARGUMENT = "phase48Room15Archive"
        const val DATABASE_NAME = "phase48-room15-runtime-to-room22.sqlite3"
        const val EXPECTED_ARCHIVE_SHA256 = "e2cadfd5d97ad5075da1d05af4a278a8ac5c7e4848ee11ed40a4e773cff113a0"
    }
}
