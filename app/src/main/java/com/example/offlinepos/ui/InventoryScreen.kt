package com.example.offlinepos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import com.example.offlinepos.R
import com.example.offlinepos.data.local.ProductEntity
import com.example.offlinepos.domain.ProductDraft
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: PosViewModel,
) {
    val products by viewModel.allProducts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("အားလုံး") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredList = products.filter {
        val matchesSearch = searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.sku.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategoryFilter == "အားလုံး" || it.category.equals(selectedCategoryFilter, ignoreCase = true)
        matchesSearch && matchesCat
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ပစ္စည်းစာရင်း စီမံခန့်ခွဲမှု (Inventory)", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        StoreLogoAvatar(
                            customLogoPath = storeProfile.customLogoPath,
                            contentDescription = "Inventory Manager",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_new_product_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ပစ္စည်းအမည်၊ SKU ဖြင့် ရှာဖွေရန်...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_field"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter pills
            val catPills = listOf("အားလုံး") + categories
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(catPills) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inventory Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ပစ္စည်း စုစုပေါင်း", style = MaterialTheme.typography.labelSmall)
                        Text("${products.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("လက်ကျန် စုစုပေါင်း", style = MaterialTheme.typography.labelSmall)
                        Text("${products.sumOf { it.stockQuantity }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("လက်ကျန်နည်း ပစ္စည်း", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${products.count { it.stockQuantity <= it.lowStockThreshold }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Product name ကို Double Tap နှိပ်၍ အသေးစိတ် အချက်အလက် ကြည့်ရှုနိုင်ပါသည်",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Products List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.testTag("inventory_list"),
            ) {
                items(filteredList, key = { it.productId }) { product ->
                    InventoryItemCard(
                        product = product,
                        onDoubleTap = { viewModel.openProductDetails(product) },
                        onAdjustStock = { qty -> viewModel.adjustStock(product.productId, qty) },
                        onArchive = { viewModel.archiveProduct(product.productId) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            categories = categories,
            onSaveImage = { uri -> viewModel.saveImageFromGalleryUri(uri) },
            onDismiss = { showAddDialog = false },
            onAdd = { draft, sku ->
                viewModel.addNewProduct(draft, sku) { success, msg ->
                    if (success) {
                        showAddDialog = false
                    }
                    viewModel.setBanner(msg)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryItemCard(
    product: ProductEntity,
    onDoubleTap: () -> Unit,
    onAdjustStock: (Long) -> Unit,
    onArchive: () -> Unit,
) {
    val isLowStock = product.stockQuantity <= product.lowStockThreshold

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onDoubleTap,
                onDoubleClick = onDoubleTap,
            )
            .testTag("inventory_item_${product.productId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductVisualHelper.ProductVisual(
                    product = product,
                    modifier = Modifier.size(54.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "အမျိုးအစား: ${product.category} | SKU: ${product.sku}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (product.isRoofingSheet) "ရောင်း: %,d Ks/ပေ | ဝယ်: %,d Ks/ပေ".format(product.sellPriceMinor, product.costPriceMinor)
                        else "ရောင်း: %,d Ks | ဝယ်: %,d Ks".format(product.sellPriceMinor, product.costPriceMinor),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag("archive_product_${product.productId}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Archive",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Stock adjustments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "လက်ကျန်: ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLowStock) Color(0xFFFEF2F2) else Color(0xFFDCFCE7),
                    ) {
                        Text(
                            text = "${product.stockQuantity} ${if (product.isRoofingSheet) "ချပ်" else "ခု"}${if (isLowStock) " (⚠️သတိပေး)" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) Color(0xFFDC2626) else Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onAdjustStock(-1) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("-1", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onAdjustStock(5) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("+5", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onAdjustStock(10) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("+10", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    categories: List<String>,
    onSaveImage: (Uri) -> String?,
    onDismiss: () -> Unit,
    onAdd: (draft: ProductDraft, sku: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var sellPrice by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("20") }
    var lowStockThreshold by remember { mutableStateOf("3") }
    var selectedCategory by remember { mutableStateOf(if (categories.isNotEmpty()) categories.first() else "ဘိလပ်မြေ") }
    var newCategoryInput by remember { mutableStateOf("") }
    var isRoofingSheet by remember { mutableStateOf(false) }
    var pickedImageUri by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Android Zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = onSaveImage(uri)
            pickedImageUri = savedPath
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .testTag("add_product_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "ကုန်ပစ္စည်း အသစ် ထည့်သွင်းခြင်း",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Image from Phone Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pickedImageUri != null) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            val bitmap = remember(pickedImageUri) {
                                android.graphics.BitmapFactory.decodeFile(pickedImageUri)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Picked Image",
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷", fontSize = 24.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_gallery_image_button"),
                        ) {
                            Text(if (pickedImageUri != null) "ပုံ ပြောင်းမည်" else "📱 ဖုန်း Gallery မှ ပုံရွေးမည်", fontSize = 12.sp)
                        }
                        if (pickedImageUri != null) {
                            TextButton(onClick = { pickedImageUri = null }) {
                                Text("ပုံ ပြန်ဖျက်မည်", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Grouping
                Text("အမျိုးအစား အုပ်စု (Category Group) *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val existingAndDefaults = (listOf("ဘိလပ်မြေ", "သွပ်ပြား", "သံချောင်း", "ဆေးသုတ်", "ပိုက်နှင့်ဆက်စပ်", "သံမှို / ကိရိယာ") + categories).distinct()

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(existingAndDefaults) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat && newCategoryInput.isBlank(),
                            onClick = {
                                selectedCategory = cat
                                newCategoryInput = ""
                                if (cat == "သွပ်ပြား") isRoofingSheet = true
                            },
                            label = { Text(cat, fontSize = 11.sp) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newCategoryInput,
                    onValueChange = {
                        newCategoryInput = it
                        if (it.isNotBlank()) selectedCategory = it.trim()
                    },
                    label = { Text("သို့မဟုတ် အမျိုးအစားအသစ် ရိုက်ထည့်ရန် (ဥပမာ- ဘိလပ်မြေ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_category_input"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Roofing Sheet Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = isRoofingSheet,
                        onCheckedChange = { isRoofingSheet = it },
                        modifier = Modifier.testTag("roofing_checkbox"),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("သွပ်ပြား (Roofing Sheet)", fontWeight = FontWeight.Bold)
                        Text("တစ်ပေ စျေးနှုန်းဖြင့် ပေအရှည် x ချပ်ရေ တွက်ချက်မည်", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isRoofingSheet) "သွပ်ပြား အမည် (ဥပမာ- သွပ် အနီရောင်) *" else "ပစ္စည်း အမည် (ဥပမာ- Crown ဘိလပ်မြေ) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU / ကုဒ်") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("product_sku_input"),
                    )
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_barcode_input"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRoofingSheet) "ဝယ်စျေး (Ks / ပေ)" else "ဝယ်စျေး (Ks)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_cost_input"),
                    )
                    OutlinedTextField(
                        value = sellPrice,
                        onValueChange = { sellPrice = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRoofingSheet) "ရောင်းစျေး (Ks / ပေ) *" else "ရောင်းစျေး (Ks) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_sell_input"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRoofingSheet) "လက်ကျန် (ချပ်ရေ)" else "လက်ကျန် အရေအတွက်") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_stock_input"),
                    )
                    OutlinedTextField(
                        value = lowStockThreshold,
                        onValueChange = { lowStockThreshold = it.filter { ch -> ch.isDigit() } },
                        label = { Text("သတိပေး လက်ကျန် (ခု/ချပ်)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("product_low_stock_input"),
                    )
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("မလုပ်တော့ပါ")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cost = costPrice.toLongOrNull() ?: 0L
                            val sell = sellPrice.toLongOrNull() ?: 0L
                            val stockQty = stock.toLongOrNull() ?: 0L
                            val lowStock = lowStockThreshold.toLongOrNull() ?: 3L
                            val barcodeVal = barcode.trim().takeIf { it.isNotEmpty() }
                            val catVal = if (newCategoryInput.isNotBlank()) newCategoryInput.trim() else selectedCategory

                            val draft = ProductDraft(
                                name = name.trim(),
                                barcode = barcodeVal,
                                costPriceMinor = cost,
                                sellPriceMinor = sell,
                                stockQuantity = stockQty,
                                category = catVal,
                                isRoofingSheet = isRoofingSheet,
                                lowStockThreshold = lowStock,
                                imageUri = pickedImageUri,
                            )
                            val errors = draft.validate()
                            if (errors.isNotEmpty()) {
                                errorMessage = errors.first()
                            } else {
                                onAdd(draft, sku.trim())
                            }
                        },
                        modifier = Modifier.testTag("save_product_button")
                    ) {
                        Text("သိမ်းဆည်းမည်")
                    }
                }
            }
        }
    }
}
