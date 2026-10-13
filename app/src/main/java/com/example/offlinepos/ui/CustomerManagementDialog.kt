package com.example.offlinepos.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.window.DialogProperties
import com.example.offlinepos.data.local.CustomerEntity
import com.example.offlinepos.data.local.DebtTransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerManagementDialog(
    viewModel: PosViewModel,
    onSelectCustomer: ((CustomerEntity) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetails by remember { mutableStateOf<CustomerEntity?>(null) }
    var showRepaymentDialogFor by remember { mutableStateOf<CustomerEntity?>(null) }

    val filteredCustomers = customers.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
    }

    val totalDebtAll = customers.sumOf { it.currentDebtMinor }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .testTag("customer_management_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ဝယ်သူ & အကြွေးစာရင်း စီမံမှု",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Customer CRM & Debt Ledger",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Total Debt Banner Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalDebtAll > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ဆိုင်ရှိ စုစုပေါင်း အကြွေးကျန်ငွေ",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "%,d Ks".format(totalDebtAll),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalDebtAll > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_add_customer")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ဝယ်သူအသစ်")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("အမည် သို့မဟုတ် ဖုန်းနံပါတ်ဖြင့် ရှာပါ...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_customer_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Customer List
                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "ဝယ်သူစာရင်း မရှိသေးပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { showAddDialog = true }) {
                                Text("+ ဝယ်သူအသစ် ထည့်သွင်းမည်")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCustomers, key = { it.customerId }) { customer ->
                            CustomerListItem(
                                customer = customer,
                                isSelectMode = onSelectCustomer != null,
                                onSelect = {
                                    onSelectCustomer?.invoke(customer)
                                    onDismiss()
                                },
                                onViewDetails = { selectedCustomerForDetails = customer },
                                onRepay = { showRepaymentDialogFor = customer }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddDialog) {
        AddCustomerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, address, creditLimit ->
                viewModel.createCustomer(name, phone, address, creditLimit) { success, msg ->
                    viewModel.setBanner(msg)
                    if (success) showAddDialog = false
                }
            }
        )
    }

    // Repayment Dialog
    showRepaymentDialogFor?.let { customer ->
        RecordRepaymentDialog(
            customer = customer,
            onDismiss = { showRepaymentDialogFor = null },
            onConfirm = { amount, note ->
                viewModel.recordRepayment(customer.customerId, amount, note) { success, msg ->
                    viewModel.setBanner(msg)
                    if (success) showRepaymentDialogFor = null
                }
            }
        )
    }

    // Customer Detailed Ledger Dialog
    selectedCustomerForDetails?.let { customer ->
        CustomerLedgerDetailsDialog(
            customer = customer,
            viewModel = viewModel,
            storeName = storeProfile.storeName,
            onDismiss = { selectedCustomerForDetails = null },
            onRepayClick = {
                showRepaymentDialogFor = customer
            }
        )
    }
}

@Composable
private fun CustomerListItem(
    customer: CustomerEntity,
    isSelectMode: Boolean,
    onSelect: () -> Unit,
    onViewDetails: () -> Unit,
    onRepay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectMode) onSelect() else onViewDetails()
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (customer.currentDebtMinor > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = "အကြွေးရှိ",
                                fontSize = 10.sp,
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.phone.ifBlank { "ဖုန်းမရှိပါ" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "အကြွေး: %,d Ks".format(customer.currentDebtMinor),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (customer.currentDebtMinor > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (isSelectMode) {
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ရွေးမည်", fontSize = 12.sp)
                        }
                    } else {
                        if (customer.currentDebtMinor > 0) {
                            OutlinedButton(
                                onClick = onRepay,
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("အကြွေးဆပ်", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Button(
                            onClick = onViewDetails,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("မှတ်တမ်း", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, creditLimit: Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ဝယ်သူ အချက်အလက်အသစ် ထည့်သွင်းမည်",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ဝယ်သူအမည် *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("ဖုန်းနံပါတ် *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("လိပ်စာ (မဖြစ်မနေ မလိုပါ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it },
                    label = { Text("အကြွေး အများဆုံးခွင့်ပြုငွေ (Credit Limit Ks)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("ဥပမာ: 500000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val limit = creditLimitText.toLongOrNull() ?: 0L
                                onConfirm(name.trim(), phone.trim(), address.trim(), limit)
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("သိမ်းဆည်းမည်")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRepaymentDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, note: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "အကြွေးဆပ်ငွေ လက်ခံမှတ်တမ်းတင်ခြင်း",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ဝယ်သူ: ${customer.name} (လက်ရှိကျန်ငွေ: %,d Ks)".format(customer.currentDebtMinor),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("ဆပ်မည့်ငွေ ပမာဏ (Ks) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("မှတ်ချက် (ဥပမာ: KPay ဖြင့်ဆပ်သည်)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toLongOrNull() ?: 0L
                            if (amt > 0) {
                                onConfirm(amt, note.trim())
                            }
                        },
                        enabled = (amountText.toLongOrNull() ?: 0L) > 0
                    ) {
                        Text("အတည်ပြုသည်")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerLedgerDetailsDialog(
    customer: CustomerEntity,
    viewModel: PosViewModel,
    storeName: String,
    onDismiss: () -> Unit,
    onRepayClick: () -> Unit,
) {
    val context = LocalContext.current
    val transactions by viewModel.getCustomerTransactions(customer.customerId).collectAsState(initial = emptyList())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ဖုန်း: ${customer.phone}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Share Debt Voucher
                    IconButton(
                        onClick = {
                            val shareText = """
                                📄 **အကြွေးကျန်ငွေ သတိပေးလွှာ ($storeName)**
                                • ဝယ်သူအမည်: ${customer.name}
                                • ဖုန်း: ${customer.phone}
                                • လက်ရှိ ပေးရန်ကျန်ငွေ: %,d Ks
                                
                                ဝယ်ယူအားပေးမှုကို အထူးပင် ကျေးဇူးတင်ရှိပါသည်။
                            """.trimIndent().format(customer.currentDebtMinor)

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "အကြွေးသတိပေးလွှာ မျှဝေမည်"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Outstanding Debt Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (customer.currentDebtMinor > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("လက်ရှိ ကျန်ရှိသောအကြွေး", fontSize = 12.sp, color = Color.DarkGray)
                            Text(
                                "%,d Ks".format(customer.currentDebtMinor),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.currentDebtMinor > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        }
                        if (customer.currentDebtMinor > 0) {
                            Button(
                                onClick = onRepayClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Text("အကြွေးဆပ်ငွေ ရယူ")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "အကြွေးအရောင်း & ဆပ်ငွေ မှတ်တမ်းများ (Ledger History):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ငွေပေးချေမှု မှတ်တမ်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions, key = { it.txId }) { tx ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tx.type == "CREDIT_SALE") Color(0xFFFFFBEB) else Color(0xFFF0FDF4)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (tx.type == "CREDIT_SALE") "အကြွေးဝယ်ယူမှု (-)" else "ငွေဆပ်လက်ခံမှု (+)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (tx.type == "CREDIT_SALE") Color(0xFFB45309) else Color(0xFF15803D)
                                        )
                                        Text(
                                            text = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(tx.createdAtEpochMs)),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        if (tx.note.isNotBlank()) {
                                            Text(
                                                text = "မှတ်ချက်: ${tx.note}",
                                                fontSize = 11.sp,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "%,d Ks".format(tx.amountMinor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (tx.type == "CREDIT_SALE") Color(0xFFDC2626) else Color(0xFF16A34A)
                                        )
                                        Text(
                                            text = "လက်ကျန်: %,d Ks".format(tx.remainingBalanceMinor),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
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
