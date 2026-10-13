package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object SaleStatus {
    const val COMPLETED = "COMPLETED"
    const val VOIDED = "VOIDED"
    const val REFUNDED = "REFUNDED"
}

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val saleId: String,
    val businessDate: String,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val status: String,
    val cashierId: String,
    val deviceId: String,
    val createdAtEpochMs: Long,
    val customerId: Long? = null,
    val customerName: String? = null,
    val paymentMethod: String = "CASH",
    val splitCashMinor: Long = 0L,
    val splitDigitalMinor: Long = 0L,
)
