package com.example.offlinepos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.R
import com.example.offlinepos.domain.CartItem
import com.example.offlinepos.domain.CartTotals

@Composable
fun CartSheet(
    items: List<CartItem>,
    totals: CartTotals,
    canApplyDiscount: Boolean,
    taxRatePercent: Double = 0.0,
    onQuantityChange: (productId: Long, qty: Long) -> Unit,
    onRemoveItem: (productId: Long) -> Unit,
    onApplyDiscount: (productId: Long, discountMinor: Long) -> Unit,
    onUpdateTaxRate: ((Double) -> Unit)? = null,
    onApplyPromoCode: ((String) -> Unit)? = null,
    onClearCart: () -> Unit,
    onRequestManagerUnlock: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onDismiss: () -> Unit,
) {
    var discountTargetItem by remember { mutableStateOf<CartItem?>(null) }
    var discountInput by remember { mutableStateOf("") }
    var promoCodeInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("cart_sheet_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Current Order",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${items.size} item(s) in cart",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        if (items.isNotEmpty()) {
                            TextButton(
                                onClick = onClearCart,
                                modifier = Modifier.testTag("clear_cart_button"),
                            ) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_cart_button"),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Cart")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛒", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your cart is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Tap products from the catalog to add",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Cart Items List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items, key = { it.productId }) { item ->
                            CartItemRow(
                                item = item,
                                canApplyDiscount = canApplyDiscount,
                                onQuantityChange = { qty -> onQuantityChange(item.productId, qty) },
                                onRemove = { onRemoveItem(item.productId) },
                                onDiscountClick = {
                                    if (canApplyDiscount) {
                                        discountTargetItem = item
                                        discountInput = if (item.lineDiscountMinor > 0) item.lineDiscountMinor.toString() else ""
                                    } else {
                                        onRequestManagerUnlock()
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Totals summary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                    ) {
                        if (onUpdateTaxRate != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "အခွန်နှုန်း (Tax %):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0.0, 5.0, 7.0, 10.0).forEach { rate ->
                                        val label = if (rate == 0.0) "0%" else "${rate.toInt()}%"
                                        FilterChip(
                                            selected = taxRatePercent == rate,
                                            onClick = { onUpdateTaxRate(rate) },
                                            label = { Text(label, fontSize = 11.sp) },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (onApplyPromoCode != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = promoCodeInput,
                                    onValueChange = { promoCodeInput = it },
                                    placeholder = { Text("🎟️ Promo Code (787VIP)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("promo_code_input"),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (promoCodeInput.isNotBlank()) {
                                            onApplyPromoCode.invoke(promoCodeInput)
                                        }
                                    },
                                    enabled = promoCodeInput.isNotBlank(),
                                    modifier = Modifier.testTag("apply_promo_code_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        SummaryRow("Subtotal", "%,d Ks".format(totals.subtotalMinor))
                        if (totals.discountMinor > 0) {
                            SummaryRow("Discount", "-%,d Ks".format(totals.discountMinor), isNegative = true)
                        }
                        if (totals.taxMinor > 0 || taxRatePercent > 0.0) {
                            val rateStr = if (taxRatePercent % 1.0 == 0.0) "${taxRatePercent.toInt()}%" else "%.1f%%".format(taxRatePercent)
                            SummaryRow("Tax ($rateStr)", "%,d Ks".format(totals.taxMinor))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Total Amount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "%,d Ks".format(totals.totalMinor),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Checkout Button
                    Button(
                        onClick = onProceedToCheckout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_to_payment_button"),
                    ) {
                        Text(
                            text = "Proceed to Payment (%,d Ks)".format(totals.totalMinor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }

    // Line Discount Dialog
    discountTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { discountTargetItem = null },
            title = { Text("Line Discount: ${item.name}") },
            text = {
                Column {
                    Text(
                        text = "Unit Price: %,d Ks | Max Discount: %,d Ks".format(
                            item.unitPriceMinor,
                            item.unitPriceMinor * item.quantity
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { discountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Discount Amount (Ks)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("discount_input_field"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = discountInput.toLongOrNull() ?: 0L
                        onApplyDiscount(item.productId, amount)
                        discountTargetItem = null
                    },
                    modifier = Modifier.testTag("apply_discount_confirm_button")
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { discountTargetItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    canApplyDiscount: Boolean,
    onQuantityChange: (Long) -> Unit,
    onRemove: () -> Unit,
    onDiscountClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_${item.productId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%,d Ks each".format(item.unitPriceMinor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.lineDiscountMinor > 0) {
                    Text(
                        text = "Discount: -%,d Ks".format(item.lineDiscountMinor),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDC2626),
                    )
                }
                TextButton(
                    onClick = onDiscountClick,
                    modifier = Modifier.padding(0.dp),
                ) {
                    Text(
                        text = if (item.lineDiscountMinor > 0) "Edit Discount" else "+ Discount",
                        fontSize = 11.sp,
                        color = if (canApplyDiscount) MaterialTheme.colorScheme.primary else Color(0xFFD97706),
                    )
                }
            }

            // Quantity adjustments
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { onQuantityChange(item.quantity - 1) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("decrement_qty_${item.productId}"),
                ) {
                    Text("-", fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )

                IconButton(
                    onClick = { onQuantityChange(item.quantity + 1) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .testTag("increment_qty_${item.productId}"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("remove_item_${item.productId}"),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isNegative: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isNegative) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
        )
    }
}
