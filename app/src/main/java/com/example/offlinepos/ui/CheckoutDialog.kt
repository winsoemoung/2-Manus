package com.example.offlinepos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.data.local.CustomerEntity
import com.example.offlinepos.domain.CartTotals
import com.example.offlinepos.hardware.QrCodeHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    totals: CartTotals,
    customLogoPath: String? = null,
    customers: List<CustomerEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        method: String,
        tendered: Long,
        customer: CustomerEntity?,
        splitCash: Long,
        splitDigital: Long
    ) -> Unit,
) {
    var selectedMethod by remember { mutableStateOf("CASH") } // CASH, KPAY, WAVEPAY, CREDIT, SPLIT
    var tenderedText by remember { mutableStateOf(totals.totalMinor.toString()) }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerDropdownOpen by remember { mutableStateOf(false) }

    // Split payment states
    var splitCashText by remember { mutableStateOf((totals.totalMinor / 2).toString()) }
    val splitCash = splitCashText.toLongOrNull() ?: 0L
    val splitDigital = (totals.totalMinor - splitCash).coerceAtLeast(0L)

    val tenderedAmount = tenderedText.toLongOrNull() ?: 0L
    val changeAmount = (tenderedAmount - totals.totalMinor).coerceAtLeast(0L)
    val isTenderedSufficient = when (selectedMethod) {
        "CASH" -> tenderedAmount >= totals.totalMinor
        "CREDIT" -> selectedCustomer != null
        "SPLIT" -> splitCash + splitDigital >= totals.totalMinor
        else -> true // KPAY, WAVEPAY assume exact transaction
    }

    // Dynamic QR Bitmap for KPay / WavePay
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(selectedMethod, totals.totalMinor) {
        if (selectedMethod == "KPAY") {
            qrBitmap = QrCodeHelper.generateQrBitmap("00020101021229370016A0000007270399990113KPAY_MERCHANT540${totals.totalMinor}5802MM5303104", 320)
        } else if (selectedMethod == "WAVEPAY") {
            qrBitmap = QrCodeHelper.generateQrBitmap("WAVEPAY:MERCHANT:787POS:AMOUNT=${totals.totalMinor}KS", 320)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .testTag("checkout_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StoreLogoAvatar(
                            customLogoPath = customLogoPath,
                            contentDescription = "Cashier",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ငွေရှင်းရန် (Checkout)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "787 Smart Payment",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_checkout_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payable Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "ကျသင့်ငွေ စုစုပေါင်း (TOTAL PAYABLE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "%,d Ks".format(totals.totalMinor),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (totals.discountMinor > 0) {
                            Text(
                                text = "စုစုပေါင်း လျှော့ငွေ: %,d Ks".format(totals.discountMinor),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF047857),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer Selection Bar (for All or Credit)
                Text(
                    text = "ဝယ်သူ ရွေးချယ်ရန် (Optional / Required for Credit):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customerDropdownOpen = true },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCustomer != null) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedCustomer != null) Color(0xFF1D4ED8) else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedCustomer?.name ?: "သာမန်ဝယ်သူ (Walking Customer)",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedCustomer != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedCustomer != null) Color(0xFF1D4ED8) else Color.DarkGray
                                )
                            }
                            if (selectedCustomer != null) {
                                Text(
                                    text = "အကြွေးကျန်: %,d Ks".format(selectedCustomer!!.currentDebtMinor),
                                    fontSize = 11.sp,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text("ပြောင်းမည် ▼", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = customerDropdownOpen,
                        onDismissRequest = { customerDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("သာမန်ဝယ်သူ (Walking Customer - အကြွေးမမှတ်ပါ)") },
                            onClick = {
                                selectedCustomer = null
                                customerDropdownOpen = false
                            }
                        )
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${c.phone}) - အကြွေးကျန်: %,d Ks".format(c.currentDebtMinor)) },
                                onClick = {
                                    selectedCustomer = c
                                    customerDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payment Method Selector Chips
                Text(
                    text = "ငွေပေးချေမှု ပုံစံ ရွေးပါ:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "CASH" to "💵 ငွေသား",
                        "KPAY" to "📱 KPay (QR)",
                        "WAVEPAY" to "🟡 WavePay (QR)",
                        "CREDIT" to "📝 အကြွေး",
                        "SPLIT" to "⚡ Split ခွဲယူ",
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = selectedMethod == id,
                            onClick = {
                                selectedMethod = id
                                if (id == "KPAY" || id == "WAVEPAY") {
                                    tenderedText = totals.totalMinor.toString()
                                }
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.testTag("payment_chip_$id"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Method Specific UI:
                when (selectedMethod) {
                    "CASH" -> {
                        Text(
                            text = "အမြန်ငွေစက္ကူများ (Quick Cash Bills):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val quickAmounts = listOf(
                            totals.totalMinor,
                            roundUpTo(totals.totalMinor, 5000),
                            roundUpTo(totals.totalMinor, 10000),
                            20000L,
                            50000L,
                            100000L,
                        ).distinct().filter { it >= totals.totalMinor }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            quickAmounts.forEach { amount ->
                                OutlinedButton(
                                    onClick = { tenderedText = amount.toString() },
                                    modifier = Modifier.testTag("quick_cash_$amount"),
                                ) {
                                    Text(
                                        text = if (amount == totals.totalMinor) "ကွက်တိ (%,d)".format(amount) else "%,d Ks".format(amount),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = tenderedText,
                            onValueChange = { input -> tenderedText = input.filter { it.isDigit() } },
                            label = { Text("လက်ခံရရှိငွေ (Cash Received Ks)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("tendered_input"),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Change Due
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTenderedSufficient) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isTenderedSufficient) "ပြန်အမ်းငွေ (Change Due):" else "ပေးငွေ မလုံလောက်ပါ (Short):",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (isTenderedSufficient) Color(0xFF166534) else Color(0xFF991B1B),
                                )
                                Text(
                                    text = if (isTenderedSufficient) "%,d Ks".format(changeAmount) else "-%,d Ks".format(totals.totalMinor - tenderedAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = if (isTenderedSufficient) Color(0xFF166534) else Color(0xFF991B1B),
                                )
                            }
                        }
                    }

                    "KPAY", "WAVEPAY" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedMethod == "KPAY") Color(0xFFEFF6FF) else Color(0xFFFEF9C3)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedMethod == "KPAY") "ကမ္ဘောဇ KPay Dynamic QR" else "WavePay Dynamic QR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "ဝယ်သူအား အောက်ပါ QR Code Scan ဖတ်ခိုင်းပါ (ပမာဏ: %,d Ks)".format(totals.totalMinor),
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                qrBitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Dynamic Payment QR",
                                        modifier = Modifier
                                            .size(170.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }

                    "CREDIT" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCustomer != null) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(
                                    text = "အကြွေးအရောင်း မှတ်တမ်းတင်ခြင်း (Credit Sale)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFB91C1C)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedCustomer == null) {
                                    Text(
                                        text = "⚠️ ကျေးဇူးပြု၍ အထက်တွင် ဝယ်သူကို အရင်ရွေးချယ်ပေးပါ",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "ဝယ်သူ: ${selectedCustomer!!.name} (လက်ရှိကျန်ငွေ: %,d Ks)".format(selectedCustomer!!.currentDebtMinor),
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val newTotalDebt = selectedCustomer!!.currentDebtMinor + totals.totalMinor
                                    Text(
                                        text = "အရောင်းအပြီး စုစုပေါင်း အကြွေးဖြစ်လာမည့်ငွေ: %,d Ks".format(newTotalDebt),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }

                    "SPLIT" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Split Payment (ငွေသား + KPay/Wave)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = splitCashText,
                                    onValueChange = { splitCashText = it.filter { c -> c.isDigit() } },
                                    label = { Text("ငွေသားဖြင့် ပေးမည့်ပမာဏ (Cash Ks)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("KPay/WavePay ပေးရန်ကျန်ငွေ:", fontSize = 12.sp)
                                        Text("%,d Ks".format(splitDigital), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1D4ED8))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Confirm Button
                Button(
                    onClick = {
                        val tenderedToPass = when (selectedMethod) {
                            "CASH" -> tenderedAmount
                            "CREDIT" -> 0L
                            "SPLIT" -> totals.totalMinor
                            else -> totals.totalMinor
                        }
                        onConfirm(selectedMethod, tenderedToPass, selectedCustomer, splitCash, splitDigital)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_checkout_button"),
                    enabled = isTenderedSufficient,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = when (selectedMethod) {
                            "CREDIT" -> "အကြွေးအရောင်း အတည်ပြုမည် (Confirm Credit)"
                            "SPLIT" -> "Split စနစ်ဖြင့် ရှင်းမည် (%,d Ks)".format(totals.totalMinor)
                            else -> "အတည်ပြု ငွေရှင်းမည် (Confirm %,d Ks)".format(totals.totalMinor)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun roundUpTo(value: Long, multiple: Long): Long {
    if (multiple <= 0) return value
    val remainder = value % multiple
    return if (remainder == 0L) value else value + (multiple - remainder)
}
