package com.example.offlinepos.ui

import com.example.offlinepos.data.local.SaleItemEntity
import com.example.offlinepos.domain.CartTotals

data class ReceiptData(
    val saleId: String,
    val businessDate: String,
    val items: List<SaleItemEntity>,
    val totals: CartTotals,
    val paymentMethod: String,
    val tenderedMinor: Long,
    val changeMinor: Long,
    val timestampEpochMs: Long,
    val cashierId: String,
    val deviceId: String,
    val customerName: String? = null,
    val splitCashMinor: Long = 0L,
    val splitDigitalMinor: Long = 0L,
) {
    val subtotalMinor: Long get() = totals.subtotalMinor
    val discountMinor: Long get() = totals.discountMinor
    val taxMinor: Long get() = totals.taxMinor
    val totalMinor: Long get() = totals.totalMinor
}
