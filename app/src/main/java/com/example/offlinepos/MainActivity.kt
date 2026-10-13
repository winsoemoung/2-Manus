package com.example.offlinepos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.offlinepos.ui.AiCopilotScreen
import com.example.offlinepos.ui.CartSheet
import com.example.offlinepos.ui.CheckoutDialog
import com.example.offlinepos.ui.HomeScreen
import com.example.offlinepos.ui.InventoryScreen
import com.example.offlinepos.ui.PosViewModel
import com.example.offlinepos.ui.ProductDetailsDialog
import com.example.offlinepos.ui.ReceiptDialog
import com.example.offlinepos.ui.RoofingCalculatorDialog
import com.example.offlinepos.ui.SalesHistoryScreen
import com.example.offlinepos.ui.SettingsScreen
import com.example.offlinepos.ui.theme.OfflinePOSTheme

enum class PosTab(val title: String) {
    REGISTER("အရောင်း"),
    INVENTORY("ပစ္စည်းစာရင်း"),
    SALES("အရောင်းပြက္ခဒိန်"),
    AI_COPILOT("AI ကွပ်ကဲရေး"),
    SETTINGS("ဆက်တင်"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = PosViewModel(applicationContext)

        setContent {
            OfflinePOSTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: PosViewModel) {
    var selectedTab by remember { mutableStateOf(PosTab.REGISTER) }
    var showCartSheet by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    val cart by viewModel.cart.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val receipt by viewModel.receipt.collectAsState()
    val actor by viewModel.actor.collectAsState()
    val bannerMessage by viewModel.bannerMessage.collectAsState()
    val detailsProduct by viewModel.detailsProduct.collectAsState()
    val roofingProduct by viewModel.roofingProduct.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show banner notifications
    LaunchedEffect(bannerMessage) {
        bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    // Predictive back handler for tabs and sheets
    BackHandler(
        enabled = showCartSheet || showCheckoutDialog || detailsProduct != null || roofingProduct != null || selectedTab != PosTab.REGISTER
    ) {
        when {
            roofingProduct != null -> viewModel.dismissRoofingCalculator()
            detailsProduct != null -> viewModel.dismissProductDetails()
            showCheckoutDialog -> showCheckoutDialog = false
            showCartSheet -> showCartSheet = false
            selectedTab != PosTab.REGISTER -> selectedTab = PosTab.REGISTER
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                NavigationBarItem(
                    selected = selectedTab == PosTab.REGISTER,
                    onClick = { selectedTab = PosTab.REGISTER },
                    icon = { Icon(Icons.Default.Home, contentDescription = "အရောင်း") },
                    label = { Text("အရောင်း") },
                    modifier = Modifier.testTag("nav_register"),
                )
                NavigationBarItem(
                    selected = selectedTab == PosTab.INVENTORY,
                    onClick = { selectedTab = PosTab.INVENTORY },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "ပစ္စည်းစာရင်း") },
                    label = { Text("ပစ္စည်းစာရင်း") },
                    modifier = Modifier.testTag("nav_inventory"),
                )
                NavigationBarItem(
                    selected = selectedTab == PosTab.SALES,
                    onClick = { selectedTab = PosTab.SALES },
                    icon = { Icon(Icons.Default.Star, contentDescription = "အရောင်းပြက္ခဒိန်") },
                    label = { Text("အရောင်းပြက္ခဒိန်") },
                    modifier = Modifier.testTag("nav_sales"),
                )
                NavigationBarItem(
                    selected = selectedTab == PosTab.AI_COPILOT,
                    onClick = { selectedTab = PosTab.AI_COPILOT },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI ကွပ်ကဲရေး") },
                    label = { Text("AI ကွပ်ကဲရေး") },
                    modifier = Modifier.testTag("nav_ai_copilot"),
                )
                NavigationBarItem(
                    selected = selectedTab == PosTab.SETTINGS,
                    onClick = { selectedTab = PosTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "ဆက်တင်") },
                    label = { Text("ဆက်တင်") },
                    modifier = Modifier.testTag("nav_settings"),
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                PosTab.REGISTER -> HomeScreen(
                    viewModel = viewModel,
                    onOpenCart = { showCartSheet = true },
                )
                PosTab.INVENTORY -> InventoryScreen(
                    viewModel = viewModel,
                )
                PosTab.SALES -> SalesHistoryScreen(
                    viewModel = viewModel,
                )
                PosTab.AI_COPILOT -> AiCopilotScreen(
                    viewModel = viewModel,
                )
                PosTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                )
            }
        }
    }

    // Cart Sheet Dialog
    if (showCartSheet) {
        CartSheet(
            items = cart,
            totals = totals,
            canApplyDiscount = actor.canApplyDiscount,
            taxRatePercent = storeProfile.taxRatePercent,
            onQuantityChange = { id, qty -> viewModel.updateQuantity(id, qty) },
            onRemoveItem = { id -> viewModel.removeFromCart(id) },
            onApplyDiscount = { id, disc -> viewModel.applyDiscount(id, disc) },
            onUpdateTaxRate = { viewModel.setTaxRate(it) },
            onApplyPromoCode = { viewModel.applyPromoCodeToCart(it) },
            onClearCart = { viewModel.clearCart() },
            onRequestManagerUnlock = {
                viewModel.setBanner("မန်နေဂျာ စနစ်ဖွင့်ရန် Settings တွင် PIN 1234 ရိုက်ထည့်ပါ")
            },
            onProceedToCheckout = {
                showCartSheet = false
                showCheckoutDialog = true
            },
            onDismiss = { showCartSheet = false },
        )
    }

    val customers by viewModel.customers.collectAsState()

    // Checkout Flow Dialog
    if (showCheckoutDialog) {
        CheckoutDialog(
            totals = totals,
            customLogoPath = storeProfile.customLogoPath,
            customers = customers,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { method, tendered, customer, splitCash, splitDigital ->
                viewModel.checkout(
                    paymentMethod = method,
                    tenderedMinor = tendered,
                    selectedCustomer = customer,
                    splitCashMinor = splitCash,
                    splitDigitalMinor = splitDigital,
                ) { success, msg ->
                    if (success) {
                        showCheckoutDialog = false
                    }
                    viewModel.setBanner(msg)
                }
            }
        )
    }

    // Receipt Modal Dialog
    receipt?.let { currentReceipt ->
        ReceiptDialog(
            receipt = currentReceipt,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissReceipt() },
        )
    }

    // Product Details Dialog (Shown on double tap from any search or inventory screen)
    detailsProduct?.let { product ->
        ProductDetailsDialog(
            product = product,
            onDismiss = { viewModel.dismissProductDetails() },
            onAddToCart = { viewModel.addToCart(product) },
            onAdjustStock = { qty -> viewModel.adjustStock(product.productId, qty) },
        )
    }

    // Roofing Calculator Dialog
    roofingProduct?.let { product ->
        RoofingCalculatorDialog(
            product = product,
            onDismiss = { viewModel.dismissRoofingCalculator() },
            onConfirm = { lengthFeet, sheetQty, _ ->
                viewModel.addRoofingSheetToCart(product, lengthFeet, sheetQty)
            }
        )
    }
}