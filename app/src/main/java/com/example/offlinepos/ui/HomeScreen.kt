package com.example.offlinepos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import com.example.offlinepos.R
import com.example.offlinepos.data.local.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PosViewModel,
    onOpenCart: () -> Unit,
) {
    val products by viewModel.filteredProducts.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val actor by viewModel.actor.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val parkedOrders by viewModel.parkedOrders.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()

    var showScanner by remember { mutableStateOf(false) }
    var showHoldOrders by remember { mutableStateOf(false) }
    var showCustomerLedger by remember { mutableStateOf(false) }
    var showShiftReport by remember { mutableStateOf(false) }

    val totalItemCount = cart.sumOf { it.quantity }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar with Offline POS status & Cashier info
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StoreLogoAvatar(
                            customLogoPath = storeProfile.customLogoPath,
                            contentDescription = "Store Owner Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = storeProfile.storeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Offline ready badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFDCFCE7),
                                    modifier = Modifier.padding(2.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF16A34A), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "အော့ဖ်လိုင်း",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = actor.userId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // Camera Barcode Scanner
                    IconButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.testTag("top_bar_scanner_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera Scanner", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Held / Parked Orders
                    IconButton(
                        onClick = { showHoldOrders = true },
                        modifier = Modifier.testTag("top_bar_hold_orders_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (parkedOrders.isNotEmpty()) {
                                    Badge { Text("${parkedOrders.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PauseCircleOutline, contentDescription = "Hold Orders")
                        }
                    }

                    // Customer CRM & Debt Ledger
                    IconButton(
                        onClick = { showCustomerLedger = true },
                        modifier = Modifier.testTag("top_bar_customer_button")
                    ) {
                        Icon(Icons.Default.People, contentDescription = "Customer Debt Ledger")
                    }

                    // Cart Button
                    IconButton(
                        onClick = onOpenCart,
                        modifier = Modifier.testTag("top_bar_cart_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (totalItemCount > 0) {
                                    Badge {
                                        Text("$totalItemCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = "View Cart",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )

            // Search Bar & Construction Category Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("ပစ္စည်းအမည်၊ SKU သို့မဟုတ် Barcode ရှာရန်...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dynamic Category Chips
                val allCategoriesList = listOf("အားလုံး (All)") + categories.ifEmpty {
                    listOf("ဘိလပ်မြေ", "သွပ်ပြား", "သံချောင်း", "ဆေးသုတ်", "ပိုက်နှင့်ဆက်စပ်", "သံမှို / ကိရိယာ")
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(allCategoriesList) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setSelectedCategory(cat) },
                            label = { Text(cat, fontSize = 12.sp) },
                            modifier = Modifier.testTag("category_chip_$cat"),
                        )
                    }
                }
            }

            // Product Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (totalItemCount > 0) 90.dp else 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("product_grid"),
            ) {
                // Header Banner for San Thit Hardware
                item(span = { GridItemSpan(2) }) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF0F766E), // Deep Teal
                                            Color(0xFF0284C7), // Sky Blue
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🏗️ ${storeProfile.storeName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "ဘိလပ်မြေ၊ သွပ်ပြား၊ သံချောင်း၊ ဆေး၊ ပိုက်",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE2E8F0),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "✨ 787 AI ကွပ်ကဲရေး ပါဝင်သည် • Double Tap: အသေးစိတ်",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFFFEF08A),
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                StoreLogoAvatar(
                                    customLogoPath = storeProfile.customLogoPath,
                                    contentDescription = "Store Owner Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color.White, CircleShape),
                                )
                            }
                        }
                    }
                }

                if (products.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📦", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "ရှာဖွေတွေ့ရှိသော ပစ္စည်း မရှိပါ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(products, key = { it.productId }) { product ->
                        ProductCard(
                            product = product,
                            onAddToCart = { viewModel.addToCart(product) },
                            onDoubleTap = { viewModel.openProductDetails(product) },
                        )
                    }
                }
            }
        }

        // Floating Bottom Cart Bar when items are present
        if (totalItemCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("floating_cart_bar"),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                onClick = onOpenCart,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$totalItemCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "အော်ဒါကြည့်ရန် (View Cart)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "စုစုပေါင်း: %,d Ks".format(totals.totalMinor),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                    }

                    Text(
                        text = "ငွေရှင်းမည် →",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    if (showScanner) {
        CameraBarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onBarcodeScanned = { barcode ->
                viewModel.handleScannedBarcode(barcode)
            }
        )
    }

    if (showHoldOrders) {
        HoldOrdersDialog(
            viewModel = viewModel,
            onDismiss = { showHoldOrders = false }
        )
    }

    if (showCustomerLedger) {
        CustomerManagementDialog(
            viewModel = viewModel,
            onDismiss = { showCustomerLedger = false }
        )
    }

    if (showShiftReport) {
        ShiftReportDialog(
            viewModel = viewModel,
            onDismiss = { showShiftReport = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCard(
    product: ProductEntity,
    onAddToCart: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val isOutOfStock = product.stockQuantity <= 0
    val isLowStock = product.stockQuantity <= product.lowStockThreshold && !isOutOfStock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onAddToCart,
                onDoubleClick = onDoubleTap,
            )
            .testTag("product_card_${product.productId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column {
            // Product Visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp),
            ) {
                ProductVisualHelper.ProductVisual(
                    product = product,
                    modifier = Modifier.fillMaxSize(),
                )

                // Stock Badge
                val (stockBg, stockText, textColor) = when {
                    isOutOfStock -> Triple(Color(0xFFFEE2E2), "ပစ္စည်းကုန်", Color(0xFFDC2626))
                    isLowStock -> Triple(Color(0xFFFEF3C7), "လက်ကျန်နည်း (${product.stockQuantity})", Color(0xFFD97706))
                    else -> Triple(Color(0xFFDCFCE7), "လက်ကျန်: ${product.stockQuantity} ${product.unitName}", Color(0xFF15803D))
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                    color = stockBg,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = stockText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Info icon to hint double tap details
                IconButton(
                    onClick = onDoubleTap,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .padding(2.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Details",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Info
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (product.isRoofingSheet) "သွပ်ပြား • ${product.category}" else "${product.category} • ${product.sku}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (product.wholesalePriceMinor > 0 && product.wholesaleMinQty > 0) {
                    Text(
                        text = "လက်ကား: %,d Ks (${product.wholesaleMinQty} %s အထက်)".format(product.wholesalePriceMinor, product.unitName),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F766E),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (product.isRoofingSheet) "%,d Ks / ပေ".format(product.sellPriceMinor) else "%,d Ks / %s".format(product.sellPriceMinor, product.unitName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (product.isRoofingSheet) {
                            Text("ပေအရှည်တွက်ချက်သည်", fontSize = 9.sp, color = Color(0xFF0284C7))
                        }
                    }

                    IconButton(
                        onClick = onAddToCart,
                        enabled = !isOutOfStock,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = if (isOutOfStock) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .testTag("add_product_to_cart_${product.productId}"),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add to cart",
                            tint = if (isOutOfStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
