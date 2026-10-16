package com.example.offlinepos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
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

@Composable
fun ProductDetailsDialog(
    product: ProductEntity,
    categories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAddToCart: (() -> Unit)? = null,
    onAdjustStock: ((Long) -> Unit)? = null,
    onUpdateProduct: ((ProductEntity) -> Unit)? = null,
    onSaveImage: ((android.net.Uri) -> String?)? = null,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(product) { mutableStateOf(product.name) }
    var editSku by remember(product) { mutableStateOf(product.sku) }
    var editBarcode by remember(product) { mutableStateOf(product.barcode ?: "") }
    var editCategory by remember(product) { mutableStateOf(product.category) }
    var editUnitName by remember(product) { mutableStateOf(product.unitName) }
    var editImageUri by remember(product) { mutableStateOf(product.imageUri) }
    var editSellPrice by remember(product) { mutableStateOf(product.sellPriceMinor.toString()) }
    var editCostPrice by remember(product) { mutableStateOf(product.costPriceMinor.toString()) }
    var editStock by remember(product) { mutableStateOf(product.stockQuantity.toString()) }
    var editThreshold by remember(product) { mutableStateOf(product.lowStockThreshold.toString()) }
    var editError by remember { mutableStateOf<String?>(null) }

    val profitMinor = product.sellPriceMinor - product.costPriceMinor
    val marginPercent = if (product.sellPriceMinor > 0) {
        (profitMinor * 100.0) / product.sellPriceMinor
    } else 0.0

    val isLowStock = product.stockQuantity <= product.lowStockThreshold

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .testTag("product_details_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEditing) "✏️ ပစ္စည်း ပြင်ဆင်ခြင်း" else "ကုန်ပစ္စည်း အချက်အလက်",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isEditing && onUpdateProduct != null) {
                            OutlinedButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.height(34.dp).testTag("edit_product_button"),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ပြင်မည်", fontSize = 11.5.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_details_button")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isEditing) {
                    // Edit Form
                    // Camera and Gallery Image Picker
                    ProductImagePicker(
                        imagePath = editImageUri,
                        onImageSelected = { editImageUri = it },
                        onSaveGalleryUri = { uri -> onSaveImage?.invoke(uri) },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("ပစ္စည်းအမည် (Product Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_name_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("အမျိုးအစား (Category):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    if (categories.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = editCategory.equals(cat, ignoreCase = true),
                                    onClick = { editCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("အမျိုးအစား စာသား") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_category_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Unit selector & custom unit input
                    Text("ယူနစ် သတ်မှတ်ရန် (Unit) *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    val commonUnits = listOf("ခု", "အိတ်", "ပိဿာ", "ဗူး", "ချပ်", "လိပ်", "တန်", "ထုပ်", "စုံ", "ပေ", "ကီလို")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        items(commonUnits) { u ->
                            FilterChip(
                                selected = editUnitName == u,
                                onClick = { editUnitName = u },
                                label = { Text(u, fontSize = 11.sp) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editUnitName,
                        onValueChange = { editUnitName = it },
                        label = { Text("ယူနစ် စာသား (ဥပမာ - အိတ်၊ ပိဿာ၊ ဗူး၊ ခု)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_unit_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editSku,
                            onValueChange = { editSku = it },
                            label = { Text("SKU နံပါတ်") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_sku_input"),
                        )
                        OutlinedTextField(
                            value = editBarcode,
                            onValueChange = { editBarcode = it },
                            label = { Text("Barcode") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_barcode_input"),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editSellPrice,
                            onValueChange = { editSellPrice = it },
                            label = { Text("ရောင်းစျေး (Ks)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_sell_price_input"),
                        )
                        OutlinedTextField(
                            value = editCostPrice,
                            onValueChange = { editCostPrice = it },
                            label = { Text("ဝယ်ရင်းစျေး (Ks)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_cost_price_input"),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editStock,
                            onValueChange = { editStock = it },
                            label = { Text("လက်ကျန် အရေအတွက်") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_stock_input"),
                        )
                        OutlinedTextField(
                            value = editThreshold,
                            onValueChange = { editThreshold = it },
                            label = { Text("သတိပေး လက်ကျန်") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_threshold_input"),
                        )
                    }

                    editError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("မလုပ်တော့ပါ")
                        }
                        Button(
                            onClick = {
                                if (editName.isBlank()) {
                                    editError = "ပစ္စည်းအမည် ထည့်သွင်းပါ"
                                    return@Button
                                }
                                val sellP = editSellPrice.toLongOrNull() ?: 0L
                                val costP = editCostPrice.toLongOrNull() ?: 0L
                                val stockQ = editStock.toLongOrNull() ?: product.stockQuantity
                                val thresh = editThreshold.toLongOrNull() ?: product.lowStockThreshold

                                val updated = product.copy(
                                    name = editName.trim(),
                                    sku = editSku.trim(),
                                    barcode = if (editBarcode.isNotBlank()) editBarcode.trim() else null,
                                    category = editCategory.trim(),
                                    unitName = editUnitName.trim().ifBlank { "ခု" },
                                    imageUri = editImageUri,
                                    sellPriceMinor = sellP,
                                    costPriceMinor = costP,
                                    stockQuantity = stockQ,
                                    lowStockThreshold = thresh,
                                )
                                onUpdateProduct?.invoke(updated)
                                isEditing = false
                            },
                            modifier = Modifier.weight(1.5f).testTag("save_product_edit_button"),
                        ) {
                            Text("💾 သိမ်းဆည်းမည်")
                        }
                    }
                } else {

                // Product Visual & Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductVisualHelper.ProductVisual(
                        product = product,
                        modifier = Modifier.size(72.dp),
                    )

                    Spacer(modifier = Modifier.size(14.dp))

                    Column {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = product.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Low stock alert banner
                if (isLowStock) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚠️ ", fontSize = 18.sp)
                            Text(
                                text = "လက်ကျန် အလွန်နည်းနေပါသည် (အနည်းဆုံး ${product.lowStockThreshold} ခု သတ်မှတ်ထားသော်လည်း ${product.stockQuantity} ခုသာ ကျန်ရှိပါသည်)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Attributes List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        DetailItem("SKU နံပါတ်", product.sku)
                        DetailItem("Barcode", product.barcode ?: "မရှိပါ")
                        DetailItem(
                            "ရောင်းစျေးနှုန်း",
                            if (product.isRoofingSheet) "%,d Ks / ပေ".format(product.sellPriceMinor) else "%,d Ks".format(product.sellPriceMinor),
                            isHighlight = true
                        )
                        DetailItem(
                            "ဝယ်ရင်းစျေး",
                            if (product.isRoofingSheet) "%,d Ks / ပေ".format(product.costPriceMinor) else "%,d Ks".format(product.costPriceMinor)
                        )
                        DetailItem(
                            "ခန့်မှန်း အမြတ်",
                            "%,d Ks (%.1f%%)".format(profitMinor, marginPercent)
                        )
                        DetailItem("အမျိုးအစားပုံစံ", if (product.isRoofingSheet) "သွပ်ပြား (ပေအရှည်တွက်ချက်သည်)" else "သာမန်ပစ္စည်း")
                        DetailItem("ယူနစ်", product.unitName)
                        DetailItem("လက်ရှိ လက်ကျန်", "${product.stockQuantity} ${product.unitName}")
                        DetailItem("သတိပေး လက်ကျန်", "${product.lowStockThreshold} ${product.unitName}")
                    }
                }

                // Quick stock adjustments if provided
                if (onAdjustStock != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("လက်ကျန် စာရင်း အမြန်တိုး/လျှော့:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { onAdjustStock(-5) }, modifier = Modifier.weight(1f)) {
                            Text("-5")
                        }
                        OutlinedButton(onClick = { onAdjustStock(10) }, modifier = Modifier.weight(1f)) {
                            Text("+10")
                        }
                        OutlinedButton(onClick = { onAdjustStock(50) }, modifier = Modifier.weight(1f)) {
                            Text("+50")
                        }
                    }
                }

                // Add to cart action if provided
                if (onAddToCart != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onAddToCart()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("details_add_to_cart_button"),
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("အော်ဒါထဲသို့ ထည့်မည်")
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DetailItem(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}
