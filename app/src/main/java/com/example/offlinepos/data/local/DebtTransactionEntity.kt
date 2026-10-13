package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debt_transactions",
    indices = [Index("customerId"), Index("saleId")],
)
data class DebtTransactionEntity(
    @PrimaryKey(autoGenerate = true) val txId: Long = 0,
    val customerId: Long,
    val saleId: String? = null,
    val type: String, // "CREDIT_SALE" or "REPAYMENT"
    val amountMinor: Long,
    val remainingBalanceMinor: Long,
    val note: String = "",
    val cashierId: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
