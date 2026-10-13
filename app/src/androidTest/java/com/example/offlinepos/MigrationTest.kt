package com.example.offlinepos

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.offlinepos.data.local.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrate1To2_addsTaxCodeColumnAndKeepsExistingSales() {
        val dbFile = File(context.cacheDir, "migration-test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE sales (
                            saleId TEXT PRIMARY KEY NOT NULL,
                            businessDate TEXT NOT NULL,
                            subtotalMinor INTEGER NOT NULL,
                            discountMinor INTEGER NOT NULL,
                            taxMinor INTEGER NOT NULL,
                            totalMinor INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            cashierId TEXT NOT NULL,
                            deviceId TEXT NOT NULL,
                            createdAtEpochMs INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE products (
                            productId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            sku TEXT NOT NULL,
                            barcode TEXT,
                            costPriceMinor INTEGER NOT NULL,
                            sellPriceMinor INTEGER NOT NULL,
                            stockQuantity INTEGER NOT NULL,
                            isActive INTEGER NOT NULL,
                            updatedAtEpochMs INTEGER NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL("""
            INSERT INTO sales
            (saleId, businessDate, subtotalMinor, discountMinor, taxMinor, totalMinor,
            status, cashierId, deviceId, createdAtEpochMs)
            VALUES ('s1', '2026-10-03', 1000, 0, 0, 1000,
            'COMPLETED', 'u1', 'd1', 1)
        """.trimIndent())

        // Run migration 1 -> 2
        MIGRATION_1_2.migrate(db)

        // Validate sales data is preserved
        db.query("SELECT COUNT(*) FROM sales").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        // Validate taxCode column exists
        db.query("PRAGMA table_info(products)").use { cursor ->
            var foundTaxCode = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == "taxCode") {
                    foundTaxCode = true
                    break
                }
            }
            assertTrue("taxCode column should exist in products table", foundTaxCode)
        }

        db.close()
        dbFile.delete()
    }
}
