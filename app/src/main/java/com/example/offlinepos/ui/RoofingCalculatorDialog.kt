package com.example.offlinepos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.data.local.ProductEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoofingCalculatorDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (lengthFeet: Long, sheetQty: Long, calculatedTotal: Long) -> Unit,
) {
    var lengthFeetText by remember { mutableStateOf("9") }
    var sheetQtyText by remember { mutableStateOf("10") }

    val lengthFeet = lengthFeetText.toLongOrNull() ?: 0L
    val sheetQty = sheetQtyText.toLongOrNull() ?: 0L
    val pricePerFoot = product.sellPriceMinor

    val totalAmount = lengthFeet * sheetQty * pricePerFoot

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("roofing_calculator_dialog"),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "သွပ်ပြား အလျားတွက်ချက်ခြင်း",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rate banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("တစ်ပေ စျေးနှုန်း (Rate / ft):", style = MaterialTheme.typography.bodySmall)
                        Text("%,d Ks / ပေ".format(pricePerFoot), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Standard Length Quick Chips
                Text("ပေအရှည် ရွေးချယ်ရန် (Length in Feet):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val standardLengths = listOf(6L, 7L, 8L, 9L, 10L, 12L, 14L)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    standardLengths.forEach { feet ->
                        FilterChip(
                            selected = lengthFeet == feet,
                            onClick = { lengthFeetText = feet.toString() },
                            label = { Text("$feet ပေ", fontSize = 12.sp) },
                            modifier = Modifier.testTag("roof_length_$feet"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthFeetText,
                        onValueChange = { lengthFeetText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("ပေအရှည် (Feet)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("roofing_feet_input"),
                    )
                    OutlinedTextField(
                        value = sheetQtyText,
                        onValueChange = { sheetQtyText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("ချပ်ရေ (Sheets)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("roofing_qty_input"),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Formula Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "[ $lengthFeet ပေ × $sheetQty ချပ် × %,d Ks ]".format(pricePerFoot),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ကျသင့်ငွေ: %,d Ks".format(totalAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (lengthFeet > 0 && sheetQty > 0) {
                            onConfirm(lengthFeet, sheetQty, totalAmount)
                        }
                    },
                    enabled = lengthFeet > 0 && sheetQty > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_roofing_order_button"),
                ) {
                    Text(
                        text = "အော်ဒါထဲသို့ ထည့်မည် (%,d Ks)".format(totalAmount),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
