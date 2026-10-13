package com.example.offlinepos.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShiftReportDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val storeProfile by viewModel.storeProfile.collectAsState()
    val sales by viewModel.salesHistory.collectAsState()

    // Calculate today's shift breakdown
    val startOfToday = PosViewModel.getStartOfToday()
    val endOfToday = PosViewModel.getEndOfToday()
    val todaySales = sales.filter { it.createdAtEpochMs in startOfToday..endOfToday }

    val totalCashSales = todaySales.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.totalMinor }
    val totalDigitalSales = todaySales.filter { it.paymentMethod.contains("KPAY", ignoreCase = true) || it.paymentMethod.contains("WAVEPAY", ignoreCase = true) }.sumOf { it.totalMinor }
    val totalCreditSales = todaySales.filter { it.paymentMethod.contains("CREDIT", ignoreCase = true) }.sumOf { it.totalMinor }
    val totalRevenue = todaySales.sumOf { it.totalMinor }

    var openingCashText by remember { mutableStateOf("50000") }
    var pettyExpenseText by remember { mutableStateOf("0") }
    var actualCashText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val openingCash = openingCashText.toLongOrNull() ?: 0L
    val pettyExpense = pettyExpenseText.toLongOrNull() ?: 0L
    val expectedCash = openingCash + totalCashSales - pettyExpense
    val actualCash = actualCashText.toLongOrNull() ?: expectedCash
    val difference = actualCash - expectedCash

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .testTag("shift_report_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "နေ့ချုပ် စာရင်းပိတ်ခြင်း (Z-Report)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "End of Day Shift Closing",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("ယနေ့ အရောင်း အနှစ်ချုပ်:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("စုစုပေါင်း ဘောင်ချာအရေအတွက်:", fontSize = 12.sp)
                            Text("${todaySales.size} စောင်", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ငွေသား အရောင်းရငွေ (Cash):", fontSize = 12.sp)
                            Text("%,d Ks".format(totalCashSales), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KPay / WavePay အရောင်းရငွေ:", fontSize = 12.sp)
                            Text("%,d Ks".format(totalDigitalSales), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("အကြွေး အရောင်း (Credit Sales):", fontSize = 12.sp)
                            Text("%,d Ks".format(totalCreditSales), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("စုစုပေါင်း အရောင်းရငွေ (Total):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("%,d Ks".format(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cash Inputs
                Text("ငွေကိုင် အံဆွဲ စစ်ဆေးချက်:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = openingCashText,
                        onValueChange = { openingCashText = it },
                        label = { Text("စရန်ငွေ (Starting Cash)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = pettyExpenseText,
                        onValueChange = { pettyExpenseText = it },
                        label = { Text("အသုံးစရိတ် (Expense)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("အံဆွဲတွင် ရှိရမည့် ငွေသား (Expected):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                        Text("%,d Ks".format(expectedCash), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = actualCashText,
                    onValueChange = { actualCashText = it },
                    label = { Text("လက်ဝယ် ရေတွက်ရရှိငွေ (Actual Counted Ks) *") },
                    placeholder = { Text("နမူနာ: %,d".format(expectedCash)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Difference indicator
                val diffColor = when {
                    difference > 0 -> Color(0xFF16A34A)
                    difference < 0 -> Color(0xFFDC2626)
                    else -> Color(0xFF2563EB)
                }
                val diffText = when {
                    difference > 0 -> "ငွေပိုနေပါသည် (+%,d Ks)".format(difference)
                    difference < 0 -> "ငွေလိုနေပါသည် (-%,d Ks)".format(kotlin.math.abs(difference))
                    else -> "ငွေပမာဏ ကွက်တိကိုက်ညီပါသည် (Balanced)"
                }

                Text(
                    text = diffText,
                    color = diffColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons: Share & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareSlip = """
                                📑 **787 POS နေ့ချုပ်စာရင်း (Z-Report)**
                                ဆိုင်အမည်: ${storeProfile.storeName}
                                ရက်စွဲ: ${SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())}
                                
                                • ဘောင်ချာအရေအတွက်: ${todaySales.size} စောင်
                                • ငွေသားအရောင်း: %,d Ks
                                • Digital (KPay/Wave): %,d Ks
                                • အကြွေးအရောင်း: %,d Ks
                                ---------------------------
                                စုစုပေါင်း အရောင်းရငွေ: %,d Ks
                                
                                • မနက်စရန်ငွေ: %,d Ks
                                • အသုံးစရိတ်: %,d Ks
                                • အံဆွဲတွင်ရှိရမည့်ငွေ: %,d Ks
                                • လက်ဝယ်ရေတွက်ငွေ: %,d Ks
                                • အခြေအနေ: $diffText
                            """.trimIndent().format(
                                totalCashSales, totalDigitalSales, totalCreditSales, totalRevenue,
                                openingCash, pettyExpense, expectedCash, actualCash
                            )

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareSlip)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "နေ့ချုပ်စာရင်း မျှဝေမည်"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share စာရင်း")
                    }

                    Button(
                        onClick = {
                            viewModel.recordShiftClosing(
                                openingCash = openingCash,
                                cashSales = totalCashSales,
                                digitalSales = totalDigitalSales,
                                creditSales = totalCreditSales,
                                expense = pettyExpense,
                                expectedCash = expectedCash,
                                actualCash = actualCash,
                                diff = difference,
                                note = note
                            ) { success, msg ->
                                viewModel.setBanner(msg)
                                if (success) onDismiss()
                            }
                        }
                    ) {
                        Text("နေ့ချုပ် ပိတ်သိမ်းမည်")
                    }
                }
            }
        }
    }
}
