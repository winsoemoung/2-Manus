package com.example.offlinepos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        AuditEventEntity::class,
        CustomerEntity::class,
        DebtTransactionEntity::class,
        PurchaseEntity::class,
        ShiftReportEntity::class,
        ParkedOrderEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun checkoutDao(): CheckoutDao
    abstract fun customerDao(): CustomerDao
    abstract fun operationsDao(): OperationsDao

    companion object {
        private const val DB_NAME = "offline_pos.db"
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }

        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
