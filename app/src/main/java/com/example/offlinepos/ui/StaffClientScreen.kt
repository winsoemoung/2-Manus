package com.example.offlinepos.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.offlinepos.server.RemoteProductDto
import com.example.offlinepos.server.RemoteProductItem
import com.example.offlinepos.server.ServerStatusInfo
import com.example.offlinepos.server.StaffClientApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StaffAddedLog(
    val id: String,
    val name: String,
    val price: Long,
    val stock: Long,
    val time: String,
    val staff: String,
)

enum class StaffClientTab(val label: String) {
    ADD_PRODUCT("➕ ပစ္စည်းအသစ်ထည့်မည်"),
    ADDED_LOGS("📋 ယနေ့ထည့်ပြီးစာရင်း"),
    SEARCH_SERVER("🔍 ဆာဗာပစ္စည်းရှာဖွေမည်"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffClientScreen(
    viewModel: PosViewModel,
    onSwitchToServerMode: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverUrlInput by remember { mutableStateOf(viewModel.storeConfig.getLastConnectedServerUrl()) }
    var staffNameInput by remember { mutableStateOf(viewModel.storeConfig.getStaffName()) }

    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf<ServerStatusInfo?>(null) }
    var serverCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var serverProducts by remember { mutableStateOf<List<RemoteProductItem>>(emptyList()) }

    var selectedTab by remember { mutableStateOf(StaffClientTab.ADD_PRODUCT) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannerModeForBarcode by remember { mutableStateOf(false) } // true for barcode, false for server QR

    // Product Input Fields
    var productName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("အထွေထွေ") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var sellPrice by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("0") }
    var stockQuantity by remember { mutableStateOf("10") }
    var lowThreshold by remember { mutableStateOf("3") }
    var unitName by remember { mutableStateOf("ခု") }

    var isSubmitting by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackIsSuccess by remember { mutableStateOf(true) }

    val addedLogs = remember { mutableStateListOf<StaffAddedLog>() }
    var searchQuery by remember { mutableStateOf("") }

    // Connect to server function
    fun connectToServer(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        isConnecting = true
        scope.launch {
            val res = StaffClientApi.testConnection(clean)
            if (res.isSuccess) {
                isConnected = true
                serverStatus = res.getOrNull()
                viewModel.storeConfig.setLastConnectedServerUrl(clean)
                // Fetch categories
                val cats = StaffClientApi.fetchCategories(clean).getOrDefault(emptyList())
                if (cats.isNotEmpty()) {
                    serverCategories = cats
                }
                // Fetch products
                val prods = StaffClientApi.fetchProducts(clean).getOrDefault(emptyList())
                serverProducts = prods

                feedbackIsSuccess = true
                feedbackMessage = "✅ ဆာဗာသို့ အောင်မြင်စွာ ချိတ်ဆက်ပြီးပါပြီ (${serverStatus?.storeName})"
            } else {
                isConnected = false
                serverStatus = null
                feedbackIsSuccess = false
                feedbackMessage = "❌ ဆာဗာသို့ ချိတ်ဆက်မရပါ: ${res.exceptionOrNull()?.message}"
            }
            isConnecting = false
        }
    }

    // Auto connect on launch
    LaunchedEffect(Unit) {
        connectToServer(serverUrlInput)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "📱 787 ဆိုင်ဝန်ထမ်း Client",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isConnected) "🟢 ${serverStatus?.storeName ?: "Main Server Online"}" else "🔴 ချိတ်ဆက်မှုမရှိသေးပါ",
                            fontSize = 11.sp,
                            color = if (isConnected) Color(0xFF16A34A) else Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    // Switch back to Main POS Server mode
                    OutlinedButton(
                        onClick = onSwitchToServerMode,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("switch_to_server_pos_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🖥️ Main POS သို့", fontSize = 11.5.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // Connection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                ),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isConnected) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "ဆာဗာ ချိတ်ဆက်ထားသည်" else "ဆာဗာ လိပ်စာ သတ်မှတ်ရန်",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isConnected) Color(0xFF15803D) else Color(0xFFB91C1C)
                            )
                        }

                        IconButton(
                            onClick = {
                                scannerModeForBarcode = false
                                showScannerDialog = true
                            },
                            modifier = Modifier.size(32.dp).testTag("scan_server_qr_button")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Server QR",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("Server URL (ဥပမာ - http://192.168.1.15:8787)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("server_url_input"),
                        )

                        Button(
                            onClick = { connectToServer(serverUrlInput) },
                            enabled = !isConnecting,
                            modifier = Modifier.testTag("connect_server_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ချိတ်ဆက်", fontSize = 11.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = staffNameInput,
                        onValueChange = {
                            staffNameInput = it
                            viewModel.storeConfig.setStaffName(it)
                        },
                        label = { Text("ထည့်သွင်းသူ ဝန်ထမ်းအမည် (Staff Name)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("staff_name_input"),
                    )
                }
            }

            // Feedback Banner
            feedbackMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (feedbackIsSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (feedbackIsSuccess) Color(0xFF15803D) else Color(0xFFB91C1C),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { feedbackMessage = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            ) {
                StaffClientTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                StaffClientTab.ADD_PRODUCT -> {
                    // Form inside Scrollable Column
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("📦 ကုန်ပစ္စည်း အသစ်ထည့်သွင်းရန်", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = productName,
                                    onValueChange = { productName = it },
                                    label = { Text("ကုန်ပစ္စည်း အမည် *") },
                                    placeholder = { Text("ဥပမာ - ဆင်တံဆိပ် ဘိလပ်မြေ") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("client_product_name_input"),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("အမျိုးအစား (Category):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                if (serverCategories.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(serverCategories) { cat ->
                                            FilterChip(
                                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                                onClick = { selectedCategory = cat },
                                                label = { Text(cat, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategory,
                                        onValueChange = { selectedCategory = it },
                                        label = { Text("အမျိုးအစား စာသား") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f).testTag("client_category_input"),
                                    )
                                    OutlinedTextField(
                                        value = unitName,
                                        onValueChange = { unitName = it },
                                        label = { Text("ယူနစ်") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f).testTag("client_unit_input"),
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = barcode,
                                        onValueChange = { barcode = it },
                                        label = { Text("Barcode") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("client_barcode_input"),
                                    )

                                    IconButton(
                                        onClick = {
                                            scannerModeForBarcode = true
                                            showScannerDialog = true
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .testTag("client_scan_barcode_button")
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = sku,
                                    onValueChange = { sku = it },
                                    label = { Text("SKU နံပါတ် (မဖြည့်ပါက အလိုအလျောက် သတ်မှတ်မည်)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("client_sku_input"),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = sellPrice,
                                        onValueChange = { sellPrice = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("ရောင်းဈေး (Ks) *") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("client_sell_price_input"),
                                    )
                                    OutlinedTextField(
                                        value = costPrice,
                                        onValueChange = { costPrice = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("ဝယ်ရင်းဈေး (Ks)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("client_cost_price_input"),
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = stockQuantity,
                                        onValueChange = { stockQuantity = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("အရေအတွက် *") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("client_stock_input"),
                                    )
                                    OutlinedTextField(
                                        value = lowThreshold,
                                        onValueChange = { lowThreshold = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("သတိပေး လက်ကျန်") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("client_threshold_input"),
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Stock Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(10L, 50L, 100L, 500L).forEach { q ->
                                        OutlinedButton(
                                            onClick = { stockQuantity = q.toString() },
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(2.dp)
                                        ) {
                                            Text("+$q", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (productName.isBlank()) {
                                            feedbackIsSuccess = false
                                            feedbackMessage = "ကုန်ပစ္စည်း အမည် ထည့်သွင်းပေးပါ"
                                            return@Button
                                        }
                                        val sellP = sellPrice.toLongOrNull() ?: 0L
                                        if (sellP <= 0L) {
                                            feedbackIsSuccess = false
                                            feedbackMessage = "ရောင်းဈေး မှန်ကန်စွာ ထည့်သွင်းပေးပါ"
                                            return@Button
                                        }
                                        val stockQ = stockQuantity.toLongOrNull() ?: 1L
                                        val costP = costPrice.toLongOrNull() ?: 0L
                                        val thresh = lowThreshold.toLongOrNull() ?: 3L

                                        isSubmitting = true
                                        scope.launch {
                                            val dto = RemoteProductDto(
                                                name = productName.trim(),
                                                category = selectedCategory.trim().ifBlank { "အထွေထွေ" },
                                                sku = sku.trim().ifBlank { "PROD-${System.currentTimeMillis() % 100000}" },
                                                barcode = barcode.trim().ifBlank { null },
                                                costPriceMinor = costP,
                                                sellPriceMinor = sellP,
                                                stockQuantity = stockQ,
                                                lowStockThreshold = thresh,
                                                unitName = unitName.trim().ifBlank { "ခု" },
                                                staffName = staffNameInput.trim().ifBlank { "ဆိုင်ဝန်ထမ်း" }
                                            )

                                            val res = StaffClientApi.sendProduct(serverUrlInput, dto)
                                            if (res.isSuccess) {
                                                feedbackIsSuccess = true
                                                val msg = res.getOrNull() ?: "အောင်မြင်စွာ ပေါင်းထည့်ပြီးပါပြီ"
                                                feedbackMessage = "✅ $msg"
                                                Toast.makeText(context, "✅ $productName ကို Server သို့ ထည့်သွင်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()

                                                addedLogs.add(
                                                    0,
                                                    StaffAddedLog(
                                                        id = UUIDString(),
                                                        name = dto.name,
                                                        price = dto.sellPriceMinor,
                                                        stock = dto.stockQuantity,
                                                        time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                                                        staff = dto.staffName
                                                    )
                                                )

                                                // Reset fields for next entry
                                                productName = ""
                                                barcode = ""
                                                sku = ""
                                                sellPrice = ""
                                                costPrice = "0"
                                                stockQuantity = "10"
                                            } else {
                                                feedbackIsSuccess = false
                                                feedbackMessage = "❌ ထည့်သွင်းခြင်း မအောင်မြင်ပါ: ${res.exceptionOrNull()?.message}"
                                            }
                                            isSubmitting = false
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("client_submit_product_button"),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("🚀 Main Server Database သို့ ပေါင်းထည့်မည်", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                StaffClientTab.ADDED_LOGS -> {
                    // Added Logs list
                    if (addedLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ယနေ့ ထည့်သွင်းပြီးသော ပစ္စည်းများ မရှိသေးပါ", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(addedLogs, key = { it.id }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(10.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(log.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${log.time} • ဝန်ထမ်း: ${log.staff} • လက်ကျန်: ${log.stock} ခု", fontSize = 11.sp, color = Color.DarkGray)
                                        }
                                        Text("%,d Ks".format(log.price), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                StaffClientTab.SEARCH_SERVER -> {
                    // Search Server Products
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("ဆာဗာရှိ ပစ္စည်းများ ရှာဖွေရန် (အမည် / Barcode)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )

                        val filtered = serverProducts.filter {
                            searchQuery.isBlank() ||
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.barcode.contains(searchQuery, ignoreCase = true) ||
                                    it.sku.contains(searchQuery, ignoreCase = true)
                        }

                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("ရှာဖွေမှုနှင့် ကိုက်ညီသော ပစ္စည်းမရှိပါ", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                items(filtered, key = { it.productId }) { p ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(10.dp),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${p.category} • Barcode: ${p.barcode.ifBlank { "N/A" }}", fontSize = 11.sp, color = Color.DarkGray)
                                                Text("လက်ကျန်: ${p.stockQuantity} ${p.unitName}", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                                            }
                                            Text("%,d Ks".format(p.sellPriceMinor), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScannerDialog) {
        CameraBarcodeScannerDialog(
            onDismiss = { showScannerDialog = false },
            onBarcodeScanned = { scanned ->
                showScannerDialog = false
                if (scannerModeForBarcode) {
                    barcode = scanned
                    Toast.makeText(context, "Barcode ဖတ်ယူရရှိပါသည်: $scanned", Toast.LENGTH_SHORT).show()
                } else {
                    // Scanned server QR
                    if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
                        serverUrlInput = scanned
                        connectToServer(scanned)
                    } else if (scanned.contains(":")) {
                        val fixedUrl = "http://$scanned"
                        serverUrlInput = fixedUrl
                        connectToServer(fixedUrl)
                    } else {
                        val fixedUrl = "http://$scanned:8787"
                        serverUrlInput = fixedUrl
                        connectToServer(fixedUrl)
                    }
                }
            }
        )
    }
}

private fun UUIDString(): String = java.util.UUID.randomUUID().toString()
