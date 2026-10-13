package com.example.offlinepos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY currentDebtMinor DESC, name ASC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE currentDebtMinor > 0 ORDER BY currentDebtMinor DESC")
    fun observeDebtors(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE customerId = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity): Int

    @Query("UPDATE customers SET currentDebtMinor = currentDebtMinor + :amount WHERE customerId = :customerId")
    suspend fun addDebt(customerId: Long, amount: Long): Int

    @Query("UPDATE customers SET currentDebtMinor = MAX(0, currentDebtMinor - :amount) WHERE customerId = :customerId")
    suspend fun reduceDebt(customerId: Long, amount: Long): Int

    @Insert
    suspend fun insertDebtTransaction(tx: DebtTransactionEntity): Long

    @Query("SELECT * FROM debt_transactions WHERE customerId = :customerId ORDER BY createdAtEpochMs DESC")
    fun observeTransactionsForCustomer(customerId: Long): Flow<List<DebtTransactionEntity>>

    @Query("SELECT * FROM debt_transactions ORDER BY createdAtEpochMs DESC LIMIT 100")
    fun observeAllDebtTransactions(): Flow<List<DebtTransactionEntity>>

    @Transaction
    suspend fun recordRepayment(customerId: Long, amount: Long, note: String, cashierId: String): Long {
        val customer = getCustomerById(customerId) ?: return 0L
        reduceDebt(customerId, amount)
        val newBalance = (customer.currentDebtMinor - amount).coerceAtLeast(0L)
        val tx = DebtTransactionEntity(
            customerId = customerId,
            type = "REPAYMENT",
            amountMinor = amount,
            remainingBalanceMinor = newBalance,
            note = note,
            cashierId = cashierId,
            createdAtEpochMs = System.currentTimeMillis()
        )
        return insertDebtTransaction(tx)
    }
}
