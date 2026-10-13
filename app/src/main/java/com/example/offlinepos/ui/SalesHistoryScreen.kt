package com.example.offlinepos.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.data.local.ProductEntity
import com.example.offlinepos.data.local.PurchaseEntity
import com.example.offlinepos.data.local.SaleEntity
import com.example.offlinepos.data.local.SaleItemEntity
import com.example.offlinepos.data.local.ShiftReportEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterMode(val label: String) {
    TODAY("ယနေ့ (Today)"),
    THIS_WEEK("ယခုအပတ် (Week)"),
    THIS_MONTH("ယခုလ (Month)"),
    THIS_YEAR("ယခုနှစ် (Year)"),
    CUSTOM("စိတ်ကြိုက် (Custom)"),
}

enum class AnalyticsTab(val label: String) {
    SALES("အရောင်းမှတ်တမ်း"),
    PROFIT_LOSS("အမြတ်/အရှုံး"),
    PURCHASES("ပစ္စည်းအဝင် (COGS)"),
    SHIFTS("နေ့ချုပ်များ (Z-Reports)"),
    REORDER_REPORTS("📊 မှာယူရန်/Reorder"),
}

enum class PeriodicReportType(val label: String, val description: String) {
    WEEKLY_SUNDAY("အပတ်စဉ် Sunday Reorder", "တနင်္ဂနွေနေ့တိုင်း ထုတ်ပေးမည့် အပတ်စဉ် ရောင်းအားကောင်းပစ္စည်းများနှင့် ပြန်လည်မှာယူရန်စာရင်း"),
    MONTHLY_FIRST("လစဉ် ၁ ရက်နေ့ စာရင်း", "လဆန်း ၁ ရက်နေ့တိုင်း ထုတ်ပေးမည့် လစဉ် ရောင်းအားကောင်းပစ္စည်းများနှင့် ပိုမှာသင့်သည့်စာရင်း"),
    QUARTERLY_3M("၃ လ တစ်ခါ (Quarterly)", "၃ လအတွင်း အရောင်းအသွက်ဆုံး ပစ္စည်းများနှင့် သုံးလပတ် ဝယ်ယူသင့်သော ပစ္စည်းစာရင်း"),
    HALF_YEAR_6M("၆ လ တစ်ခါ (6 Months)", "၆ လအတွင်း အရောင်းအကောင်းဆုံး ပစ္စည်းများနှင့် ခြောက်လပတ် ဝယ်ယူသင့်သော ပစ္စည်းစာရင်း"),
    ANNUAL_12M("၁၂ လ တစ်ခါ (Annual)", "နှစ်စဉ် ၁၂ လအတွင်း မည်သည့်လတွင် ဘာပစ္စည်းများ ရောင်းကောင်းခဲ့သည်နှင့် နှစ်ချုပ် ဝယ်ယူရန်စာရင်း"),
    DAILY("ရက်စဉ် (Daily)", "နေ့စဉ် ရောင်းအားနှင့် လက်ကျန်ဖြည့်တင်းရန် အစီရင်ခံစာ"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    viewModel: PosViewModel,
) {
    val context = LocalContext.current
    val allSales by viewModel.salesHistory.collectAsState()
    val allProducts by viewModel.allProducts.collectAsState()
    val allSaleItems by viewModel.allSaleItems.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val shifts by viewModel.shifts.collectAsState()

    var selectedTab by remember { mutableStateOf(AnalyticsTab.SALES) }
    var selectedFilterMode by remember { mutableStateOf(DateFilterMode.TODAY) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var showStockInDialog by remember { mutableStateOf(false) }
    var showShiftReportDialog by remember { mutableStateOf(false) }

    var customStartMs by remember { mutableStateOf(PosViewModel.getStartOfToday()) }
    var customEndMs by remember { mutableStateOf(PosViewModel.getEndOfToday()) }

    val (rangeStart, rangeEnd) = when (selectedFilterMode) {
        DateFilterMode.TODAY -> Pair(PosViewModel.getStartOfToday(), PosViewModel.getEndOfToday())
        DateFilterMode.THIS_WEEK -> {
            val startWeek = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            Pair(startWeek, PosViewModel.getEndOfToday())
        }
        DateFilterMode.THIS_MONTH -> {
            val startMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            Pair(startMonth, PosViewModel.getEndOfToday())
        }
        DateFilterMode.THIS_YEAR -> {
            val startYear = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            Pair(startYear, PosViewModel.getEndOfToday())
        }
        DateFilterMode.CUSTOM -> Pair(customStartMs, customEndMs)
    }

    val filteredSales = allSales.filter { it.createdAtEpochMs in rangeStart..rangeEnd }
    val filteredPurchases = purchases.filter { it.createdAtEpochMs in rangeStart..rangeEnd }

    val totalRevenue = filteredSales.sumOf { it.totalMinor }
    val totalOrders = filteredSales.size
    val averageTicket = if (totalOrders > 0) totalRevenue / totalOrders else 0L

    // Estimated Cost and Profit
    // Estimated cost is approx 75-80% based on products catalog margin
    val avgCostRate = if (allProducts.isNotEmpty()) {
        val totalCost = allProducts.sumOf { it.costPriceMinor }
        val totalSell = allProducts.sumOf { it.sellPriceMinor }
        if (totalSell > 0) totalCost.toDouble() / totalSell.toDouble() else 0.8
    } else 0.8

    val estimatedCostOfGoods = (totalRevenue * avgCostRate).toLong()
    val grossProfit = (totalRevenue - estimatedCostOfGoods).coerceAtLeast(0L)
    val profitMarginPercent = if (totalRevenue > 0) (grossProfit.toDouble() / totalRevenue.toDouble()) * 100.0 else 0.0

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("အရောင်း & စာရင်းအင်း သုံးသပ်ချက်", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        StoreLogoAvatar(
                            customLogoPath = storeProfile.customLogoPath,
                            contentDescription = "Store Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Analytics Sub-tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            ) {
                AnalyticsTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Calendar / Period Filter Pills
            if (selectedTab != AnalyticsTab.REORDER_REPORTS) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DateFilterMode.values()) { mode ->
                        FilterChip(
                            selected = selectedFilterMode == mode,
                            onClick = {
                                selectedFilterMode = mode
                                if (mode == DateFilterMode.CUSTOM) {
                                    showCustomRangeDialog = true
                                }
                            },
                            label = { Text(mode.label, fontSize = 11.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (selectedTab) {
                AnalyticsTab.SALES -> {
                    // Revenue Summary Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("စုစုပေါင်း အရောင်းရငွေ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("%,d Ks".format(totalRevenue), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(0.9f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ဘောင်ချာ အရေအတွက်", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalOrders စောင်", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredSales.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("ရွေးချယ်ထားသော ကာလတွင် အရောင်းမှတ်တမ်း မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredSales, key = { it.saleId }) { sale ->
                                SaleRecordCard(sale = sale, onClick = { viewModel.viewPastReceipt(sale) })
                            }
                        }
                    }
                }

                AnalyticsTab.PROFIT_LOSS -> {
                    // Profit & Loss Dashboard
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("အမြတ်/အရှုံး သုံးသပ်ချက် (Profit & Loss)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF15803D))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("၁။ စုစုပေါင်း အရောင်းရငွေ (Revenue):", fontSize = 12.sp)
                                    Text("%,d Ks".format(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("၂။ ပစ္စည်းအရင်း ကုန်ကျငွေ (COGS):", fontSize = 12.sp)
                                    Text("-%,d Ks".format(estimatedCostOfGoods), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFDC2626))
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("အသားတင် အမြတ်ငွေ (Gross Profit):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("%,d Ks".format(grossProfit), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF16A34A))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("အမြတ် ရာခိုင်နှုန်း (Profit Margin):", fontSize = 12.sp, color = Color.DarkGray)
                                    Text("%.1f%%".format(profitMarginPercent), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF16A34A))
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (profitMarginPercent / 100f).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF16A34A),
                                    trackColor = Color(0xFFDCFCE7),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Payment Methods Breakdown
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("ငွေပေးချေမှုပုံစံ အလိုက် ဝင်ငွေခွဲခြမ်းစိတ်ဖြာချက်:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                val cashRev = filteredSales.filter { it.paymentMethod.equals("CASH", ignoreCase = true) }.sumOf { it.totalMinor }
                                val digitalRev = filteredSales.filter { it.paymentMethod.contains("KPAY", ignoreCase = true) || it.paymentMethod.contains("WAVEPAY", ignoreCase = true) }.sumOf { it.totalMinor }
                                val creditRev = filteredSales.filter { it.paymentMethod.contains("CREDIT", ignoreCase = true) }.sumOf { it.totalMinor }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("💵 ငွေသား အရောင်း:", fontSize = 12.sp)
                                    Text("%,d Ks".format(cashRev), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("📱 KPay / WavePay အရောင်း:", fontSize = 12.sp)
                                    Text("%,d Ks".format(digitalRev), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("📝 အကြွေး အရောင်း:", fontSize = 12.sp)
                                    Text("%,d Ks".format(creditRev), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }

                AnalyticsTab.PURCHASES -> {
                    // Purchases Log
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ပစ္စည်းအဝင်စာရင်းများ (${filteredPurchases.size} ကြိမ်):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Button(
                                onClick = { showStockInDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("အဝင်သွင်းမည်", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredPurchases.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("ပစ္စည်းအဝင်စာရင်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredPurchases, key = { it.purchaseId }) { purchase ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(purchase.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("ပေးသွင်းသူ: ${purchase.supplierName} (${purchase.quantity} ခု)", fontSize = 11.sp, color = Color.DarkGray)
                                                Text(SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(purchase.createdAtEpochMs)), fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("%,d Ks".format(purchase.totalCostMinor), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
                                                Text("@ %,d Ks/ခု".format(purchase.unitCostMinor), fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnalyticsTab.SHIFTS -> {
                    // Shifts list
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ပိတ်သိမ်းပြီးသော နေ့ချုပ်များ:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Button(
                                onClick = { showShiftReportDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("နေ့ချုပ်ပိတ်မည် (Z-Report)", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (shifts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("နေ့ချုပ်မှတ်တမ်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(shifts, key = { it.shiftId }) { shift ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(shift.cashierName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(shift.closeTimeEpochMs)), fontSize = 11.sp, color = Color.Gray)
                                                Text("ငွေသားအရောင်း: %,d Ks".format(shift.cashSalesMinor), fontSize = 11.sp, color = Color(0xFF16A34A))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("ရေတွက်ငွေ: %,d Ks".format(shift.actualCashCountedMinor), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                val diffColor = if (shift.differenceMinor >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                                                val diffText = if (shift.differenceMinor >= 0) "+%,d Ks (ပို)".format(shift.differenceMinor) else "-%,d Ks (လို)".format(kotlin.math.abs(shift.differenceMinor))
                                                Text(diffText, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = diffColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnalyticsTab.REORDER_REPORTS -> {
                    ReorderAndPeriodicReportView(
                        allSales = allSales,
                        allSaleItems = allSaleItems,
                        allProducts = allProducts,
                        storeProfile = storeProfile,
                        onOpenStockIn = { showStockInDialog = true },
                    )
                }
            }
        }
    }

    if (showStockInDialog) {
        StockInDialog(viewModel = viewModel, onDismiss = { showStockInDialog = false })
    }

    if (showShiftReportDialog) {
        ShiftReportDialog(viewModel = viewModel, onDismiss = { showShiftReportDialog = false })
    }

    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            initialStart = customStartMs,
            initialEnd = customEndMs,
            onDismiss = { showCustomRangeDialog = false },
            onConfirm = { s, e ->
                customStartMs = s
                customEndMs = e
                showCustomRangeDialog = false
            }
        )
    }
}

@Composable
private fun SaleRecordCard(
    sale: SaleEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("sale_item_${sale.saleId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${sale.saleId.take(8).uppercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (sale.paymentMethod) {
                            "CREDIT" -> Color(0xFFFEE2E2)
                            "KPAY", "WAVEPAY" -> Color(0xFFE0F2FE)
                            else -> Color(0xFFDCFCE7)
                        },
                    ) {
                        Text(
                            text = sale.paymentMethod,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (sale.paymentMethod) {
                                "CREDIT" -> Color(0xFFB91C1C)
                                "KPAY", "WAVEPAY" -> Color(0xFF0369A1)
                                else -> Color(0xFF15803D)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (!sale.customerName.isNullOrBlank()) {
                    Text(
                        text = "ဝယ်သူ: ${sale.customerName}",
                        fontSize = 11.sp,
                        color = Color(0xFF1E3A8A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${sale.businessDate} • ${sale.cashierId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%,d Ks".format(sale.totalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "ပြေစာကြည့်မည် →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CustomDateRangeDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var daysAgoText by remember { mutableStateOf("7") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "စိတ်ကြိုက် ကာလ သတ်မှတ်ရန်",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = daysAgoText,
                    onValueChange = { daysAgoText = it.filter { c -> c.isDigit() } },
                    label = { Text("လွန်ခဲ့သော ရက်ပေါင်း (Days)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val days = daysAgoText.toIntOrNull() ?: 7
                            val end = PosViewModel.getEndOfToday()
                            val start = end - (days.toLong() * 24L * 60L * 60L * 1000L)
                            onConfirm(start, end)
                        }
                    ) {
                        Text("အတည်ပြုသည်")
                    }
                }
            }
        }
    }
}

@Composable
fun ReorderAndPeriodicReportView(
    allSales: List<SaleEntity>,
    allSaleItems: List<SaleItemEntity>,
    allProducts: List<ProductEntity>,
    storeProfile: StoreProfile,
    onOpenStockIn: () -> Unit,
) {
    var selectedPeriod by remember { mutableStateOf(PeriodicReportType.WEEKLY_SUNDAY) }
    val context = LocalContext.current

    // Calculate timestamp cutoff based on selected period
    val now = System.currentTimeMillis()
    val periodStartMs = remember(selectedPeriod, now) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        when (selectedPeriod) {
            PeriodicReportType.DAILY -> {
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            PeriodicReportType.WEEKLY_SUNDAY -> {
                now - (7L * 24 * 60 * 60 * 1000)
            }
            PeriodicReportType.MONTHLY_FIRST -> {
                now - (30L * 24 * 60 * 60 * 1000)
            }
            PeriodicReportType.QUARTERLY_3M -> {
                now - (90L * 24 * 60 * 60 * 1000)
            }
            PeriodicReportType.HALF_YEAR_6M -> {
                now - (180L * 24 * 60 * 60 * 1000)
            }
            PeriodicReportType.ANNUAL_12M -> {
                now - (365L * 24 * 60 * 60 * 1000)
            }
        }
    }

    // Map sales by ID to extract timestamps
    val salesMap = remember(allSales) { allSales.associateBy { it.saleId } }

    // Group sales items into the selected timeframe
    val periodSaleItems = remember(allSaleItems, salesMap, periodStartMs) {
        allSaleItems.filter { item ->
            val sale = salesMap[item.saleId]
            (sale?.createdAtEpochMs ?: 0L) >= periodStartMs
        }
    }

    // Product sold quantities in this period
    val productSoldQtyMap = remember(periodSaleItems) {
        periodSaleItems.groupBy { it.productId }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
    }

    data class MonthTopProduct(val productName: String, val totalQty: Long)
    data class MonthSalesSummary(val monthLabel: String, val topProducts: List<MonthTopProduct>)

    // Monthly Best Sellers breakdown (across all sales)
    val monthlyBestSellers = remember(allSaleItems, salesMap) {
        val monthFormat = SimpleDateFormat("yyyy-MM (MMMM)", Locale.getDefault())
        val groupedByMonth = allSaleItems.groupBy { item ->
            val sale = salesMap[item.saleId]
            if (sale != null) monthFormat.format(Date(sale.createdAtEpochMs)) else "အထွေထွေ"
        }
        groupedByMonth.map { (monthStr, itemsInMonth) ->
            val topList = itemsInMonth.groupBy { it.productNameSnapshot }
                .map { (name, lines) -> MonthTopProduct(name, lines.sumOf { it.quantity }) }
                .sortedByDescending { it.totalQty }
                .take(4)
            MonthSalesSummary(monthStr, topList)
        }.sortedByDescending { it.monthLabel }
    }

    data class ReorderRecommendation(
        val product: ProductEntity,
        val soldInPeriod: Long,
        val suggestedReorderQty: Long,
        val urgency: String,
        val estimatedCost: Long,
    )

    val recommendations = remember(allProducts, productSoldQtyMap, selectedPeriod) {
        allProducts.map { prod ->
            val sold = productSoldQtyMap[prod.productId] ?: 0L
            val isLow = prod.stockQuantity <= prod.lowStockThreshold
            val urgency = when {
                prod.stockQuantity <= 0 -> "🔴 ပြတ်လပ်နေပါသည် (ချက်ချင်းမှာယူပါ)"
                isLow -> "🔴 လက်ကျန်နည်းနေပါသည် (အရေးပေါ်မှာယူပါ)"
                sold > prod.stockQuantity -> "🟡 ရောင်းအားသွက် (ကြိုတင်မှာယူပါ)"
                sold > 0 -> "🟢 ရောင်းအားကောင်း (ပိုမှာသင့်သည်)"
                else -> "⚪ ပုံမှန် လက်ကျန်"
            }

            val baseNeeded = when {
                sold > 0 -> (sold * 1.3).toLong().coerceAtLeast(prod.lowStockThreshold * 2)
                else -> prod.lowStockThreshold * 2
            }
            val suggested = (baseNeeded - prod.stockQuantity).coerceAtLeast(0L)
            val rounded = if (suggested > 0) ((suggested + 9) / 10) * 10 else 0L

            ReorderRecommendation(
                product = prod,
                soldInPeriod = sold,
                suggestedReorderQty = if (rounded > 0) rounded else (if (isLow) prod.lowStockThreshold * 2 else 0L),
                urgency = urgency,
                estimatedCost = (if (rounded > 0) rounded else (if (isLow) prod.lowStockThreshold * 2 else 0L)) * prod.costPriceMinor
            )
        }.sortedWith(
            compareByDescending<ReorderRecommendation> { it.suggestedReorderQty > 0 }
                .thenByDescending { it.soldInPeriod }
        )
    }

    val totalEstimatedBudget = recommendations.sumOf { it.estimatedCost }
    val totalItemsToReorder = recommendations.count { it.suggestedReorderQty > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Period Selector Chips
        item {
            Text("အစီရင်ခံစာ ကာလ ရွေးချယ်ရန်:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PeriodicReportType.values()) { pType ->
                    FilterChip(
                        selected = selectedPeriod == pType,
                        onClick = { selectedPeriod = pType },
                        label = { Text(pType.label, fontSize = 11.5.sp) }
                    )
                }
            }
        }

        // Summary Card & Action Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📋 ${selectedPeriod.label} သုံးသပ်ချက် & Reorder စာရင်း",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectedPeriod.description,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("မှာယူရန် လိုအပ်သော ပစ္စည်း:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("$totalItemsToReorder မျိုး", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ခန့်မှန်း အရင်းအနှီး ကုန်ကျစရိတ်:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("%,d Ks".format(totalEstimatedBudget), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val reportText = buildString {
                                    appendLine("📊 **${storeProfile.storeName} - ${selectedPeriod.label} အစီရင်ခံစာ**")
                                    appendLine("📅 ထုတ်ယူသည့်ရက်စွဲ: ${SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())}")
                                    appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                                    appendLine("⚠️ **ပိုမှာသင့်သည့် ပစ္စည်းများနှင့် Reorder စာရင်း:**")
                                    recommendations.filter { it.suggestedReorderQty > 0 }.forEachIndexed { idx, rec ->
                                        appendLine("${idx + 1}။ ${rec.product.name} (SKU: ${rec.product.sku})")
                                        appendLine("   • လက်ရှိလက်ကျန်: ${rec.product.stockQuantity} ခု | ရောင်းအား: ${rec.soldInPeriod} ခု")
                                        appendLine("   • ပိုမှာသင့်သည့်အရေအတွက်: ${rec.suggestedReorderQty} ခု (ခန့်မှန်း: %,d Ks)".format(rec.estimatedCost))
                                    }
                                    appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                                    appendLine("📈 စုစုပေါင်း မှာယူရန် ပစ္စည်း: $totalItemsToReorder မျိုး")
                                    appendLine("💰 ခန့်မှန်း အရင်းငွေ: %,d Ks".format(totalEstimatedBudget))
                                }

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, reportText)
                                    putExtra(Intent.EXTRA_SUBJECT, "${selectedPeriod.label} Reorder Report")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "အစီရင်ခံစာ မျှဝေရန်"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📤 အစီရင်ခံစာ Share", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenStockIn,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("📦 ပစ္စည်းအဝင် သွင်းမည်", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Monthly Best Sellers Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📅 လအလိုက် အရောင်းသွက်ဆုံး ပစ္စည်းများ (Best Sellers by Month)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("မည်သည့်လတွင် မည်သည့်ပစ္စည်းများ ရောင်းအားအကောင်းဆုံးဖြစ်ခဲ့သည်ကို လေ့လာသုံးသပ်ချက်", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (monthlyBestSellers.isEmpty()) {
                        Text("အရောင်းမှတ်တမ်း မရှိသေးပါ", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        monthlyBestSellers.take(6).forEach { summary ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("🗓️ ${summary.monthLabel}:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    summary.topProducts.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("• ${item.productName}", fontSize = 11.5.sp)
                                            Text("${item.totalQty} ခု ရောင်းချခဲ့ရ", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Color(0xFF15803D))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reorder & Purchase Recommendation List
        item {
            Text(
                text = "⚠️ ဝယ်ယူသင့်သော & ပိုမှာသင့်သော ပစ္စည်းစာရင်း (Reorder List):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(recommendations, key = { it.product.productId }) { rec ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (rec.suggestedReorderQty > 0) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("SKU: ${rec.product.sku} | အမျိုးအစား: ${rec.product.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (rec.suggestedReorderQty > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = rec.urgency,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rec.suggestedReorderQty > 0) Color(0xFFDC2626) else Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("လက်ရှိ လက်ကျန်: ${rec.product.stockQuantity} ခု", fontSize = 11.sp)
                        Text("ကာလအတွင်း ရောင်းရ: ${rec.soldInPeriod} ခု", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (rec.suggestedReorderQty > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📦 ပိုမှာသင့်သည့် ပမာဏ:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text(
                                text = "+${rec.suggestedReorderQty} ခု (ခန့်မှန်း: %,d Ks)".format(rec.estimatedCost),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
        }
    }
}
