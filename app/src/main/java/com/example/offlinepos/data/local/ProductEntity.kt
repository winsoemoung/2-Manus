package com.example.offlinepos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val productId: Long = 0,
    val name: String,
    val sku: String,
    val barcode: String?,
    val costPriceMinor: Long,
    val sellPriceMinor: Long,
    val stockQuantity: Long,
    val isActive: Boolean = true,
    val updatedAtEpochMs: Long,
    val category: String = "အထွေထွေ",
    val isRoofingSheet: Boolean = false,
    val lowStockThreshold: Long = 3,
    val imageUri: String? = null,
    val wholesalePriceMinor: Long = 0L,
    val wholesaleMinQty: Long = 0L,
    val unitName: String = "ခု",
)