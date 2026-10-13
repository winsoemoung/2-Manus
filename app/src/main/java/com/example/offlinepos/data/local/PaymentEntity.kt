package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["saleId"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("saleId")],
)
data class PaymentEntity(
    @PrimaryKey val paymentId: String,
    val saleId: String,
    val method: String,
    val tenderedMinor: Long,
    val changeMinor: Long,
    val authorizedMinor: Long,
    val authorizationState: String,
    val providerReference: String?,
    val settlementState: String,
    val manualRecorded: Boolean,
    val recordedByUserId: String,
    val createdAtEpochMs: Long,
)