package com.sanka1610.reprodroid.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object DatabaseMigrationGate {
    fun prepare(context: Context, databaseName: String, targetVersion: Int) {
        val databaseFile = context.getDatabasePath(databaseName)
        if (!databaseFile.isFile) return
        val snapshotDirectory = File(context.noBackupFilesDir, "migration-snapshots")
        check(snapshotDirectory.isDirectory || snapshotDirectory.mkdirs()) {
            "Migration snapshot directory could not be created."
        }
        val lockFile = File(snapshotDirectory, "migration.lock")
        FileOutputStream(lockFile, true).channel.use { channel ->
            channel.lock().use {
                prepareLocked(context, databaseFile, snapshotDirectory, targetVersion)
            }
        }
    }

    @SuppressLint("UsableSpace") // Fail closed; migration must not evict other app caches to manufacture capacity.
    private fun prepareLocked(
        context: Context,
        databaseFile: File,
        snapshotDirectory: File,
        targetVersion: Int,
    ) {
        val metadata = inspectAndCheckpoint(databaseFile)
        if (metadata.userVersion == targetVersion) return
        check(metadata.userVersion in 1 until targetVersion) {
            "Unsupported database schema ${metadata.userVersion}; automatic migration is stopped."
        }
        val sourceBytes = databaseAndSidecarBytes(databaseFile)
        val privateUsage = directoryBytes(File(context.applicationInfo.dataDir))
        check(privateUsage + sourceBytes <= ANDROID_STORAGE_BUDGET_BYTES) {
            "Android storage budget cannot reserve a migration snapshot."
        }
        check(databaseFile.parentFile?.usableSpace ?: 0L >= sourceBytes * 2 + RECOVERY_RESERVE_BYTES) {
            "Insufficient free space for a verified migration snapshot."
        }

        val sourceSha256 = sha256(databaseFile)
        val snapshotFile = File(
            snapshotDirectory,
            "reprodroid-v${metadata.userVersion}-$sourceSha256.sqlite3",
        )
        if (snapshotFile.exists()) {
            validateSnapshot(snapshotFile, metadata, sourceSha256)
            writeChecksum(snapshotDirectory, snapshotFile, sourceSha256)
            return
        }
        val temporary = File(snapshotDirectory, ".${snapshotFile.name}.part")
        check(!Files.isSymbolicLink(databaseFile.toPath())) { "Database path must not be a symbolic link." }
        try {
            FileInputStream(databaseFile).channel.use { input ->
                FileOutputStream(temporary, false).channel.use { output ->
                    var position = 0L
                    while (position < input.size()) {
                        val copied = input.transferTo(position, input.size() - position, output)
                        check(copied > 0) { "Migration snapshot copy stopped before EOF." }
                        position += copied
                    }
                    output.force(true)
                }
            }
            validateSnapshot(temporary, metadata, sourceSha256)
            Files.move(
                temporary.toPath(),
                snapshotFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            writeChecksum(snapshotDirectory, snapshotFile, sourceSha256)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun writeChecksum(directory: File, snapshot: File, sha256: String) {
        FileOutputStream(File(directory, "${snapshot.name}.sha256"), false).use { output ->
            output.write("$sha256  ${snapshot.name}\n".toByteArray(Charsets.US_ASCII))
            output.fd.sync()
        }
    }

    private fun inspectAndCheckpoint(databaseFile: File): DatabaseMetadata {
        return SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                check(cursor.moveToFirst()) { "WAL checkpoint returned no status." }
                val busy = cursor.getInt(0)
                val logFrames = cursor.getInt(1)
                val checkpointedFrames = cursor.getInt(2)
                check(busy == 0 && logFrames == checkpointedFrames) {
                    "Database is busy or has uncheckpointed WAL frames."
                }
            }
            val userVersion = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
            val roomIdentity = database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Room identity is missing from the pre-migration database." }
                cursor.getString(0)
            }
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            check(integrity == "ok") { "Pre-migration database integrity check failed: $integrity" }
            DatabaseMetadata(userVersion, roomIdentity)
        }
    }

    private fun validateSnapshot(snapshot: File, expected: DatabaseMetadata, expectedSha256: String) {
        check(snapshot.isFile && sha256(snapshot) == expectedSha256) {
            "Migration snapshot hash verification failed."
        }
        SQLiteDatabase.openDatabase(
            snapshot.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
            check(version == expected.userVersion) { "Migration snapshot schema version changed." }
            val identity = database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            check(identity == expected.roomIdentity) { "Migration snapshot Room identity changed." }
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
            check(integrity == "ok") { "Migration snapshot integrity check failed: $integrity" }
        }
    }

    private fun databaseAndSidecarBytes(database: File): Long = listOf(
        database,
        File("${database.absolutePath}-wal"),
        File("${database.absolutePath}-shm"),
        File("${database.absolutePath}-journal"),
    ).sumOf { file -> file.takeIf(File::isFile)?.length() ?: 0L }

    private fun directoryBytes(root: File): Long {
        if (!root.exists() || Files.isSymbolicLink(root.toPath())) return 0
        if (root.isFile) return root.length()
        return root.listFiles()?.sumOf(::directoryBytes) ?: 0
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class DatabaseMetadata(val userVersion: Int, val roomIdentity: String)

    private const val ANDROID_STORAGE_BUDGET_BYTES = 4L * 1024 * 1024 * 1024
    private const val RECOVERY_RESERVE_BYTES = 16L * 1024 * 1024
}
