package com.example.offlinepos.Repository

import com.example.offlinepos.data.local.*
import com.example.offlinepos.domain.*
data class Actor(val userId: String, val canApplyDiscount: Boolean)
data class PaymentRequest(
    val method: String,
    val tenderedMinor: Long,
    val authorizationState: String,
    val providerReference: String?,
    val manualRecorded: Boolean,
)
sealed interface CheckoutResult {
    data class Committed(val saleId: String, val changeMinor: Long) : CheckoutResult
    data class Rejected(val reason: String) : CheckoutResult
}
class CheckoutRepository(
    private val db: AppDatabase,
    private val now: () -> Long,
    private val newId: () -> String,
    private val businessDate: () -> String,
    private val deviceId: String,
) {
    suspend fun checkout(cart: List<CartItem>, payment: PaymentRequest, actor: Actor):
            CheckoutResult {
        if (cart.any { it.lineDiscountMinor > 0 } && !actor.canApplyDiscount) {
            return CheckoutResult.Rejected("Discount requires a manager")
        }
        val totals = when (val result = calculateTotals(cart)) {
            is TotalsResult.Ok -> result.totals
            is TotalsResult.Invalid -> return CheckoutResult.Rejected(result.reason)
        }
        val isCredit = payment.method == "CREDIT"
        if (!isCredit && payment.tenderedMinor < totals.totalMinor) {
            return CheckoutResult.Rejected("Cash received is less than the total")
        }
        val saleId = newId()
        val timestamp = now()
        val sale = SaleEntity(
            saleId = saleId,
            businessDate = businessDate(),
            subtotalMinor = totals.subtotalMinor,
            discountMinor = totals.discountMinor,
            taxMinor = totals.taxMinor,
            totalMinor = totals.totalMinor,
            status = SaleStatus.COMPLETED,
            cashierId = actor.userId,
            deviceId = deviceId,
            createdAtEpochMs = timestamp,
            paymentMethod = payment.method,
        )
        val lines = cart.map { item ->
            val gross = (Money.multiply(item.unitPriceMinor, item.quantity) as
                    MoneyResult.Ok).value
            val net = (Money.subtract(gross, item.lineDiscountMinor) as MoneyResult.Ok).value
            val lineTotal = (Money.add(net, item.lineTaxMinor) as MoneyResult.Ok).value
            SaleItemEntity(
                saleId = saleId, productId = item.productId,
                productNameSnapshot = item.name, unitPriceMinor = item.unitPriceMinor,
                quantity = item.quantity, lineDiscountMinor = item.lineDiscountMinor,
                lineTaxMinor = item.lineTaxMinor, lineTotalMinor = lineTotal,
                unitName = item.unitName,
            )
        }
        val changeMinor = if (isCredit) 0L else (payment.tenderedMinor - totals.totalMinor).coerceAtLeast(0L)
        val paymentRow = PaymentEntity(
            paymentId = newId(), saleId = saleId, method = payment.method,
            tenderedMinor = payment.tenderedMinor,
            changeMinor = changeMinor,
            authorizedMinor = totals.totalMinor,
            authorizationState = payment.authorizationState,
            providerReference = payment.providerReference,
            settlementState = if (payment.method == "CASH") "NOT_APPLICABLE" else
                "PENDING_SETTLEMENT",
            manualRecorded = payment.manualRecorded, recordedByUserId = actor.userId,
            createdAtEpochMs = timestamp,
        )
        val audit = AuditEventEntity(
            eventId = newId(), actorId = actor.userId, action = "SALE_COMPLETED",
            aggregateType = "SALE", aggregateId = saleId, reason = null,
            approverId = null, createdAtEpochMs = timestamp, payloadHash = "",
        )
        return try {
            db.checkoutDao().commitSale(sale, lines, paymentRow, audit, timestamp)
            CheckoutResult.Committed(saleId, paymentRow.changeMinor)
        } catch (e: IllegalStateException) {
            CheckoutResult.Rejected(e.message ?: "Checkout failed")
        }
    }
}