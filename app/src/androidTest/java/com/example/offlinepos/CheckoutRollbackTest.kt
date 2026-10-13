package com.example.offlinepos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinepos.data.local.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutRollbackTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insufficientStock_rollsBackEverything() = runTest {
        val productId = db.productDao().insert(
            ProductEntity(
                name = "Tea", sku = "TEA-1", barcode = "1234567890123",
                costPriceMinor = 800, sellPriceMinor = 1_250, stockQuantity = 1,
                updatedAtEpochMs = 1,
            ),
        )

        val sale = SaleEntity(
            saleId = "s1", businessDate = "2026-10-03", subtotalMinor = 2_500, discountMinor = 0, taxMinor = 0, totalMinor = 2_500,
            status = "COMPLETED", cashierId = "u1", deviceId = "d1", createdAtEpochMs = 1,
        )

        val line = SaleItemEntity(
            saleId = "s1", productId = productId, productNameSnapshot = "Tea",
            unitPriceMinor = 1_250, quantity = 5, lineDiscountMinor = 0,
            lineTaxMinor = 0, lineTotalMinor = 6_250,
        )

        val payment = PaymentEntity(
            paymentId = "p1", saleId = "s1", method = "CASH", tenderedMinor = 6_250,
            changeMinor = 0, authorizedMinor = 2_500,
            authorizationState = "NOT_REQUIRED", providerReference = null,
            settlementState = "NOT_APPLICABLE", manualRecorded = false,
            recordedByUserId = "u1", createdAtEpochMs = 1,
        )

        val audit = AuditEventEntity(
            eventId = "a1", actorId = "u1", action = "SALE_COMPLETED",
            aggregateType = "SALE", aggregateId = "s1", reason = null,
            approverId = null, createdAtEpochMs = 1, payloadHash = "x",
        )

        var threw = false
        try {
            db.checkoutDao().commitSale(sale, listOf(line), payment, audit, 1)
        } catch (e: IllegalStateException) {
            threw = true
        }

        assertEquals(true, threw)
        assertEquals(1L, db.productDao().findById(productId)?.stockQuantity)
    }
}
