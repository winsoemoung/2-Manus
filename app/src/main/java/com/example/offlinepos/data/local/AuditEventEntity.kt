package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [Index("aggregateId"), Index("createdAtEpochMs")],
)
data class AuditEventEntity(
    @PrimaryKey val eventId: String,
    val actorId: String,
    val action: String,
    val aggregateType: String,
    val aggregateId: String,
    val reason: String?,
    val approverId: String?,
    val createdAtEpochMs: Long,
    val payloadHash: String,
)
