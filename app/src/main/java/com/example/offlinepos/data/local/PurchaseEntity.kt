package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchases",
    indices = [Index("productId")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val purchaseId: Long = 0,
    val productId: Long,
    val productName: String,
    val supplierName: String,
    val quantity: Long,
    val unitCostMinor: Long,
    val totalCostMinor: Long,
    val invoiceRef: String = "",
    val note: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
