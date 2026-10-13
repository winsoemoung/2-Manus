package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftReportEntity(
    @PrimaryKey(autoGenerate = true) val shiftId: Long = 0,
    val cashierName: String,
    val openTimeEpochMs: Long,
    val closeTimeEpochMs: Long,
    val openingCashMinor: Long,
    val cashSalesMinor: Long,
    val digitalSalesMinor: Long,
    val creditSalesMinor: Long,
    val expenseMinor: Long,
    val expectedCashMinor: Long,
    val actualCashCountedMinor: Long,
    val differenceMinor: Long,
    val status: String = "CLOSED", // OPEN or CLOSED
    val note: String = "",
)
