package com.example.offlinepos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY category, name")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT DISTINCT category FROM products WHERE isActive = 1 ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM products WHERE productId = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity): Int

    @Query("UPDATE products SET stockQuantity = stockQuantity + :adjustment, updatedAtEpochMs = :now WHERE productId = :id")
    suspend fun adjustStock(id: Long, adjustment: Long, now: Long): Int

    @Query("UPDATE products SET sellPriceMinor = :newPrice, updatedAtEpochMs = :now WHERE productId = :id")
    suspend fun updatePrice(id: Long, newPrice: Long, now: Long): Int

    @Query("UPDATE products SET sku = :newSku, updatedAtEpochMs = :now WHERE productId = :id")
    suspend fun updateSku(id: Long, newSku: String, now: Long): Int

    @Query("UPDATE products SET barcode = :newBarcode, updatedAtEpochMs = :now WHERE productId = :id")
    suspend fun updateBarcode(id: Long, newBarcode: String, now: Long): Int

    @Query("UPDATE products SET sku = :newSku, updatedAtEpochMs = :now WHERE sku = :oldSku AND isActive = 1")
    suspend fun replaceSku(oldSku: String, newSku: String, now: Long): Int

    @Query("UPDATE products SET barcode = :newBarcode, updatedAtEpochMs = :now WHERE barcode = :oldBarcode AND isActive = 1")
    suspend fun replaceBarcode(oldBarcode: String, newBarcode: String, now: Long): Int

    @Query("UPDATE products SET category = :newCategory, updatedAtEpochMs = :now WHERE category = :oldCategory AND isActive = 1")
    suspend fun batchUpdateCategory(oldCategory: String, newCategory: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countProducts(): Int

    @Query("UPDATE products SET isActive = 0, updatedAtEpochMs = :now WHERE productId = :id")
    suspend fun archive(id: Long, now: Long): Int

    @Query("DELETE FROM products")
    suspend fun clearAll(): Int
}
