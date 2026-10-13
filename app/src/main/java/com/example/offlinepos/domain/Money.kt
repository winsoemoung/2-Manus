package com.example.offlinepos.domain

sealed interface MoneyResult {
    data class Ok(val value: Long) : MoneyResult
    data class Overflow(val detail: String) : MoneyResult
}

object Money {
    fun add(a: Long, b: Long): MoneyResult = guarded("add($a, $b)") { Math.addExact(a, b) }
    fun subtract(a: Long, b: Long): MoneyResult = guarded("subtract($a, $b)") { Math.subtractExact(a, b) }
    fun multiply(a: Long, b: Long): MoneyResult = guarded("multiply($a, $b)") { Math.multiplyExact(a, b) }
    fun format(minor: Long): String = "%,d".format(minor)

    private inline fun guarded(detail: String, block: () -> Long): MoneyResult = try {
        MoneyResult.Ok(block())
    } catch (e: ArithmeticException) {
        MoneyResult.Overflow("$detail -> ${e.message}")
    }
}

data class CartItem(
    val productId: Long,
    val name: String,
    val unitPriceMinor: Long,
    val quantity: Long,
    val lineDiscountMinor: Long = 0,
    val lineTaxMinor: Long = 0,
    val unitName: String = "ခု",
    val isWholesale: Boolean = false,
    val costPriceMinor: Long = 0L,
)

data class CartTotals(
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
)

sealed interface TotalsResult {
    data class Ok(val totals: CartTotals) : TotalsResult
    data class Invalid(val reason: String, val productId: Long?) : TotalsResult
}

fun calculateTotals(items: List<CartItem>): TotalsResult {
    if (items.isEmpty()) return TotalsResult.Invalid("Cart is empty", null)
    var subtotal = 0L
    var discount = 0L
    var tax = 0L
    for (item in items) {
        if (item.quantity <= 0) return TotalsResult.Invalid("Quantity must be positive", item.productId)
        val gross = (Money.multiply(item.unitPriceMinor, item.quantity) as? MoneyResult.Ok)?.value
            ?: return TotalsResult.Invalid("Line amount overflow", item.productId)
        val net = (Money.subtract(gross, item.lineDiscountMinor) as? MoneyResult.Ok)?.value
            ?: return TotalsResult.Invalid("Line discount overflow", item.productId)
        if (net < 0) return TotalsResult.Invalid("Discount exceeds line amount", item.productId)
        subtotal = (Money.add(subtotal, net) as? MoneyResult.Ok)?.value
            ?: return TotalsResult.Invalid("Subtotal overflow", item.productId)
        discount = (Money.add(discount, item.lineDiscountMinor) as? MoneyResult.Ok)?.value
            ?: return TotalsResult.Invalid("Discount overflow", item.productId)
        tax = (Money.add(tax, item.lineTaxMinor) as? MoneyResult.Ok)?.value
            ?: return TotalsResult.Invalid("Tax overflow", item.productId)
    }
    val total = (Money.add(subtotal, tax) as? MoneyResult.Ok)?.value
        ?: return TotalsResult.Invalid("Total overflow", null)
    return TotalsResult.Ok(CartTotals(subtotal, discount, tax, total))
}
