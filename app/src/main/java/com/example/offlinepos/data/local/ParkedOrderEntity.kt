package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parked_orders")
data class ParkedOrderEntity(
    @PrimaryKey val parkId: String,
    val tag: String,
    val itemsJson: String,
    val totalMinor: Long,
    val itemCount: Long,
    val customerName: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
