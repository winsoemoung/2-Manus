package com.example.offlinepos.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT 'အထွေထွေ'")
        db.execSQL("ALTER TABLE products ADD COLUMN isRoofingSheet INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE products ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2
)
