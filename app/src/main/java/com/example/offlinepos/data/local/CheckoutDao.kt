package com.example.offlinepos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

import kotlinx.coroutines.flow.Flow

@Dao
abstract class CheckoutDao {
    @Query("SELECT * FROM sales ORDER BY createdAtEpochMs DESC")
    abstract fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    abstract suspend fun getItemsForSale(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM payments WHERE saleId = :saleId LIMIT 1")
    abstract suspend fun getPaymentForSale(saleId: String): PaymentEntity?

    @Query("SELECT * FROM audit_events ORDER BY createdAtEpochMs DESC LIMIT 50")
    abstract fun observeAuditEvents(): Flow<List<AuditEventEntity>>
    @Insert
    abstract suspend fun insertSale(sale: SaleEntity)

    @Insert
    abstract suspend fun insertItems(items: List<SaleItemEntity>)

    @Insert
    abstract suspend fun insertPayment(payment: PaymentEntity)

    @Insert
    abstract suspend fun insertAudit(event: AuditEventEntity)

    @Query(
        """
        UPDATE products SET stockQuantity = stockQuantity - :qty, updatedAtEpochMs = :now
        WHERE productId = :productId AND isActive = 1 AND stockQuantity >= :qty
        """
    )
    abstract suspend fun reduceStock(productId: Long, qty: Long, now: Long): Int

    @Transaction
    open suspend fun commitSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        payment: PaymentEntity,
        audit: AuditEventEntity,
        now: Long,
    ) {
        require(items.isNotEmpty()) { "Sale must contain at least one line" }
        require(payment.saleId == sale.saleId) { "Payment must belong to the sale" }
        insertSale(sale)
        items.forEach { line ->
            val updated = reduceStock(line.productId, line.quantity, now)
            check(updated == 1) { "Insufficient stock" }
        }
        insertItems(items)
        insertPayment(payment)
        insertAudit(audit)
    }
}
