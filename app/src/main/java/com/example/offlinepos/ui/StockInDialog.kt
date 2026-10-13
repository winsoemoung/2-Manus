package com.example.offlinepos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.data.local.ProductEntity

@Composable
fun StockInDialog(
    viewModel: PosViewModel,
    preselectedProduct: ProductEntity? = null,
    onDismiss: () -> Unit,
) {
    val products by viewModel.allProducts.collectAsState()

    var selectedProduct by remember { mutableStateOf(preselectedProduct ?: products.firstOrNull()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var quantityText by remember { mutableStateOf("10") }
    var unitCostText by remember(selectedProduct) {
        mutableStateOf(selectedProduct?.costPriceMinor?.toString() ?: "1000")
    }
    var supplierName by remember { mutableStateOf("ကုမ္ပဏီ / ပေးသွင်းသူ") }
    var invoiceRef by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val qty = quantityText.toLongOrNull() ?: 0L
    val unitCost = unitCostText.toLongOrNull() ?: 0L
    val totalCost = qty * unitCost

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .testTag("stock_in_dialog"),
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
                            text = "ပစ္စည်းအဝင်သွင်းခြင်း (Stock-In)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Supplier Purchase & Cost Tracking",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Product Selection
                Text("ပစ္စည်းရွေးချယ်ပါ:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedProduct?.name ?: "ပစ္စည်းရွေးပါ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "လက်ကျန်: ${selectedProduct?.stockQuantity ?: 0} ${selectedProduct?.unitName ?: "ခု"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (လက်ကျန်: ${p.stockQuantity})") },
                                onClick = {
                                    selectedProduct = p
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("အရေအတွက် *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it },
                        label = { Text("တစ်ခုရင်းနှီးငွေ (Cost Ks) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("ပစ္စည်းပေးသွင်းသူ (Supplier / Company)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = invoiceRef,
                    onValueChange = { invoiceRef = it },
                    label = { Text("ဘောင်ချာ / Invoice အမှတ်") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Cost Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("စုစုပေါင်း အရင်းကုန်ကျငွေ:", fontSize = 12.sp, color = Color.DarkGray)
                        Text("%,d Ks".format(totalCost), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val prod = selectedProduct
                            if (prod != null && qty > 0) {
                                viewModel.recordStockInPurchase(
                                    productId = prod.productId,
                                    productName = prod.name,
                                    supplierName = supplierName.trim(),
                                    quantity = qty,
                                    unitCost = unitCost,
                                    invoiceRef = invoiceRef.trim(),
                                    note = note.trim()
                                ) { success, msg ->
                                    viewModel.setBanner(msg)
                                    if (success) onDismiss()
                                }
                            }
                        },
                        enabled = selectedProduct != null && qty > 0
                    ) {
                        Text("စာရင်းသွင်းမည်")
                    }
                }
            }
        }
    }
}
