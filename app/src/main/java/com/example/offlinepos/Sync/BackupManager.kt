package com.example.offlinepos.Sync

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.offlinepos.data.local.AppDatabase
import java.io.File
import java.security.MessageDigest
class BackupManager(
    private val db: AppDatabase,
    private val now: () -> Long,
) {
    suspend fun quiesce() = db.withTransaction {
        val sql = SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")
        db.openHelper.writableDatabase.query(sql).use { it.moveToFirst() }
    }
    suspend fun snapshot(target: File) {
        require(target.parentFile?.exists() == true)
        db.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("VACUUM INTO ?", arrayOf(target.absolutePath)))
            .use { it.moveToFirst() }
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun manifest(
        snapshot: File,
        appVersion: String,
        schemaVersion: Int,
        deviceId: String,
    ): String = """
{
"format": "offlinepos-backup",
"formatVersion": 1,
"createdAtEpochMs": ${now()},
"appVersion": "$appVersion",
"schemaVersion": $schemaVersion,
"deviceId": "$deviceId",
"snapshotSha256": "${sha256(snapshot)}",
"snapshotBytes": ${snapshot.length()}
}
""".trimIndent()
}