package com.example.offlinepos

import com.example.offlinepos.domain.CartItem
import com.example.offlinepos.domain.Money
import com.example.offlinepos.domain.MoneyResult
import com.example.offlinepos.domain.TotalsResult
import com.example.offlinepos.domain.calculateTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyAndTotalsTest {
    @Test
    fun multiply_overflows_becomes_overflow_not_crash() {
        val result = Money.multiply(Long.MAX_VALUE, 2)
        assertTrue(result is MoneyResult.Overflow)
    }

    @Test
    fun totals_are_snapshot_based_and_exact() {
        val items = listOf(
            CartItem(productId = 1, name = "Tea", unitPriceMinor = 1_250, quantity = 3),
            CartItem(productId = 2, name = "Bread", unitPriceMinor = 2_000, quantity = 2, lineDiscountMinor = 500),
        )
        val totals = (calculateTotals(items) as TotalsResult.Ok).totals
        assertEquals(7_250, totals.subtotalMinor)
        assertEquals(500, totals.discountMinor)
        assertEquals(7_250, totals.totalMinor)
        assertEquals("7,250", Money.format(totals.totalMinor))
    }

    @Test
    fun zero_quantity_is_rejected() {
        val items = listOf(
            CartItem(productId = 1, name = "Tea", unitPriceMinor = 1_000, quantity = 0),
        )
        assertTrue(calculateTotals(items) is TotalsResult.Invalid)
    }
}
