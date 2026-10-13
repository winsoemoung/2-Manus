package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
object SyncState {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val ACKED = "ACKED"
    const val RETRYABLE_FAILURE = "RETRYABLE_FAILURE"
    const val PERMANENT_FAILURE = "PERMANENT_FAILURE"
    const val CONFLICT = "CONFLICT"
    const val QUARANTINED = "QUARANTINED"
}
object ErrorClass {
    const val NETWORK = "NETWORK"
    const val AUTH = "AUTH"
    const val VALIDATION = "VALIDATION"
    const val SCHEMA = "SCHEMA"
    const val RATE_LIMIT = "RATE_LIMIT"
    const val SERVER = "SERVER"
}
@Entity(
    tableName = "sync_outbox",
    indices = [Index("state"), Index(value = ["operationId"], unique = true)],
)
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val idempotencyKey: String,
    val aggregateType: String,
    val aggregateId: String,
    val payloadJson: String,
    val schemaVersion: Int,
    val aggregateVersion: Long,
    val actorId: String,
    val deviceId: String,
    val state: String,
    val errorClass: String?,
    val lastError: String?,
    val retryCount: Int,
    val nextAttemptAtEpochMs: Long,
    val reconciliationResult: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)