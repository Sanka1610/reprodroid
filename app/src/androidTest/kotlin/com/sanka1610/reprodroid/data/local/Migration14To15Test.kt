package com.sanka1610.reprodroid.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReproDroidDatabase::class.java,
    )

    @Test
    fun migrationPreservesLegacyAppAndCreatesUnresolvedBinding() {
        helper.createDatabase(DATABASE_NAME, 14).apply {
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'legacy-app', 'Legacy', 'https://github.com/example/legacy',
                    'https://github.com/example/legacy', 'PUBLIC_GITHUB_RELEASES',
                    'VERIFICATION', 'AVAILABLE', 'before', 'before'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO registered_apps (
                    registeredAppId, displayName, repositoryUrl, canonicalRepositoryUrl,
                    provider, managementMode, releaseDiscoveryStatus, createdAt, updatedAt
                ) VALUES (
                    'invalid-legacy-app', 'Invalid legacy', 'https://github.com//legacy',
                    'https://github.com//legacy', 'PUBLIC_GITHUB_RELEASES',
                    'VERIFICATION', 'AVAILABLE', 'before', 'before'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            15,
            true,
            ReproDroidDatabase.MIGRATION_14_15,
        ).use { migrated ->
            migrated.query(
                "SELECT displayName, managementMode, releaseDiscoveryStatus FROM registered_apps " +
                    "WHERE registeredAppId = 'legacy-app'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy", cursor.getString(0))
                assertEquals("VERIFICATION", cursor.getString(1))
                assertEquals("AVAILABLE", cursor.getString(2))
            }
            migrated.query(
                "SELECT provider, instance, providerRepositoryId, identityStatus, registrationSlot " +
                    "FROM app_repository_bindings WHERE registeredAppId = 'legacy-app'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("GITHUB", cursor.getString(0))
                assertEquals("github.com", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertEquals("LEGACY_UNRESOLVED", cursor.getString(3))
                assertEquals("PRIMARY", cursor.getString(4))
            }
            migrated.query(
                "SELECT identityStatus FROM app_repository_bindings " +
                    "WHERE registeredAppId = 'invalid-legacy-app'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LEGACY_INVALID", cursor.getString(0))
            }
            migrated.query("PRAGMA index_list('registered_apps')").use { cursor ->
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "index_registered_apps_canonicalRepositoryUrl") {
                        found = true
                        assertEquals(0, cursor.getInt(2))
                    }
                }
                assertTrue(found)
            }
            listOf(
                "source_discoveries",
                "gradle_candidates",
                "app_build_configurations",
                "app_source_heads",
            ).forEach { table ->
                migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun fullMigrationChainReachesSchemaFifteen() {
        helper.createDatabase("phase-4-full-chain", 1).close()
        helper.runMigrationsAndValidate(
            "phase-4-full-chain",
            15,
            true,
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
        ).close()
    }

    @Test
    fun archivedPhaseThreeESnapshotPreservesEveryLegacyTableValue() {
        val sourcePath = getArguments().getString(REAL_SNAPSHOT_ARGUMENT).orEmpty()
        assumeTrue(
            "Pass -e $REAL_SNAPSHOT_ARGUMENT with a checkpointed Room 14 database",
            sourcePath.isNotBlank(),
        )
        val source = File(sourcePath)
        assertTrue("The archived Room 14 snapshot is not readable: $sourcePath", source.isFile && source.canRead())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(REAL_SNAPSHOT_DATABASE_NAME)
        val target = context.getDatabasePath(REAL_SNAPSHOT_DATABASE_NAME)
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use(input::copyTo)
        }

        try {
            val before = SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                assertEquals(14, database.userVersion())
                assertEquals("ok", database.integrityResult())
                database.legacyTableSnapshots()
            }

            helper.runMigrationsAndValidate(
                REAL_SNAPSHOT_DATABASE_NAME,
                15,
                true,
                ReproDroidDatabase.MIGRATION_14_15,
            ).close()

            SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                assertEquals(15, database.userVersion())
                assertEquals("ok", database.integrityResult())
                assertEquals(before, database.legacyTableSnapshots(before.keys))
                database.rawQuery(
                    "SELECT COUNT(*) FROM app_repository_bindings",
                    null,
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(database.rowCount("registered_apps"), cursor.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(REAL_SNAPSHOT_DATABASE_NAME)
        }
    }

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

    private fun SQLiteDatabase.rowCount(table: String): Int =
        rawQuery("SELECT COUNT(*) FROM ${table.quotedIdentifier()}", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SQLiteDatabase.legacyTableSnapshots(
        requestedTables: Set<String>? = null,
    ): Map<String, TableSnapshot> {
        val tables = requestedTables ?: rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name",
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        return tables.associateWith { table -> snapshotTable(table) }
    }

    private fun SQLiteDatabase.snapshotTable(table: String): TableSnapshot {
        val columns = rawQuery("PRAGMA table_info(${table.quotedIdentifier()})", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        val order = columns.joinToString(",") { it.quotedIdentifier() }
        val query = "SELECT * FROM ${table.quotedIdentifier()}" +
            if (order.isEmpty()) "" else " ORDER BY $order"
        val rows = rawQuery(query, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(columns.indices.map { index -> cursor.canonicalValue(index) })
                }
            }
        }
        return TableSnapshot(columns, rows)
    }

    private fun Cursor.canonicalValue(index: Int): String = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_INTEGER -> "integer:${getLong(index)}"
        Cursor.FIELD_TYPE_FLOAT -> "float:${java.lang.Double.toHexString(getDouble(index))}"
        Cursor.FIELD_TYPE_STRING -> "text:${getString(index).toByteArray().base64()}"
        Cursor.FIELD_TYPE_BLOB -> "blob:${getBlob(index).base64()}"
        else -> error("Unsupported SQLite value type at column $index")
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.quotedIdentifier(): String = "\"${replace("\"", "\"\"")}\""

    private data class TableSnapshot(
        val columns: List<String>,
        val rows: List<List<String>>,
    )

    private companion object {
        const val DATABASE_NAME = "phase-4-registration-migration-test"
        const val REAL_SNAPSHOT_ARGUMENT = "phase4Room14DatabasePath"
        const val REAL_SNAPSHOT_DATABASE_NAME = "phase-4-real-snapshot.sqlite3"
    }
}
