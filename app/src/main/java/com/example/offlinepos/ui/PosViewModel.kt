package com.example.offlinepos.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinepos.Repository.Actor
import com.example.offlinepos.Repository.CheckoutRepository
import com.example.offlinepos.Repository.CheckoutResult
import com.example.offlinepos.Repository.PaymentRequest
import com.example.offlinepos.Sync.BackupManager
import com.example.offlinepos.ai.AiAction
import com.example.offlinepos.ai.AiActionType
import com.example.offlinepos.ai.AiChatMessage
import com.example.offlinepos.ai.AiPosEngine
import com.example.offlinepos.ai.MessageSender
import com.example.offlinepos.data.local.AppDatabase
import com.example.offlinepos.data.local.AuditEventEntity
import com.example.offlinepos.data.local.CustomerEntity
import com.example.offlinepos.data.local.DebtTransactionEntity
import com.example.offlinepos.data.local.ParkedOrderEntity
import com.example.offlinepos.data.local.ProductEntity
import com.example.offlinepos.data.local.PurchaseEntity
import com.example.offlinepos.data.local.SaleEntity
import com.example.offlinepos.data.local.SaleItemEntity
import com.example.offlinepos.data.local.ShiftReportEntity
import com.example.offlinepos.hardware.PdfReceiptHelper
import org.json.JSONArray
import org.json.JSONObject
import com.example.offlinepos.domain.CartItem
import com.example.offlinepos.domain.CartTotals
import com.example.offlinepos.domain.ProductDraft
import com.example.offlinepos.domain.TotalsResult
import com.example.offlinepos.domain.calculateTotals
import com.example.offlinepos.hardware.BluetoothPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToLong
import java.util.Locale
import java.util.UUID

class PosViewModel(private val context: Context) : ViewModel() {
    private var db = AppDatabase.get(context)
    private val deviceId = "SAN-THIT-POS-01"

    val storeConfig = StoreConfig(context)
    private val _storeProfile = MutableStateFlow(storeConfig.getProfile())
    val storeProfile: StateFlow<StoreProfile> = _storeProfile.asStateFlow()

    private val checkoutRepo = CheckoutRepository(
        db = db,
        now = { System.currentTimeMillis() },
        newId = { UUID.randomUUID().toString() },
        businessDate = { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
        deviceId = deviceId,
    )

    private val backupManager = BackupManager(
        db = db,
        now = { System.currentTimeMillis() },
    )

    // Current cashier / actor
    private val _actor = MutableStateFlow(Actor(userId = "အရောင်းတာဝန်ခံ (Cashier #1)", canApplyDiscount = false))
    val actor: StateFlow<Actor> = _actor.asStateFlow()

    // Products Flow
    val allProducts: StateFlow<List<ProductEntity>> = db.productDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Categories Flow
    val categories: StateFlow<List<String>> = db.productDao().observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sales Flow
    val salesHistory: StateFlow<List<SaleEntity>> = db.checkoutDao().observeSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Audit Events Flow
    val auditEvents: StateFlow<List<AuditEventEntity>> = db.checkoutDao().observeAuditEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Customers Flow
    val customers: StateFlow<List<CustomerEntity>> = db.customerDao().observeAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Parked Orders Flow
    val parkedOrders: StateFlow<List<ParkedOrderEntity>> = db.operationsDao().observeParkedOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Purchases Flow
    val purchases: StateFlow<List<PurchaseEntity>> = db.operationsDao().observeAllPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Shifts Flow
    val shifts: StateFlow<List<ShiftReportEntity>> = db.operationsDao().observeAllShifts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Staff Role: OWNER (7870), MANAGER (1234), CASHIER
    private val _currentRole = MutableStateFlow("CASHIER")
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    // Search and Category Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("အားလုံး (All)")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Filtered Products
    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        allProducts,
        searchQuery,
        selectedCategory,
    ) { products, query, category ->
        products.filter { product ->
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                product.name.contains(query, ignoreCase = true) ||
                    product.sku.contains(query, ignoreCase = true) ||
                    product.category.contains(query, ignoreCase = true) ||
                    (product.barcode != null && product.barcode.contains(query))
            }

            val matchesCategory = if (category == "အားလုံး (All)" || category == "All") {
                true
            } else {
                product.category.equals(category, ignoreCase = true)
            }

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cart state
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    // Calculated Totals
    private val _totals = MutableStateFlow(CartTotals(0, 0, 0, 0))
    val totals: StateFlow<CartTotals> = _totals.asStateFlow()

    // Current Receipt after sale
    private val _receipt = MutableStateFlow<ReceiptData?>(null)
    val receipt: StateFlow<ReceiptData?> = _receipt.asStateFlow()

    // Selected product for double-tap Details Dialog
    private val _detailsProduct = MutableStateFlow<ProductEntity?>(null)
    val detailsProduct: StateFlow<ProductEntity?> = _detailsProduct.asStateFlow()

    // Selected roofing sheet product for Length/Quantity Calculator
    private val _roofingProduct = MutableStateFlow<ProductEntity?>(null)
    val roofingProduct: StateFlow<ProductEntity?> = _roofingProduct.asStateFlow()

    // Status / Toast Banner message
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Date range filter for Sales Calendar
    private val _filterStartDate = MutableStateFlow(getStartOfToday())
    val filterStartDate: StateFlow<Long> = _filterStartDate.asStateFlow()

    private val _filterEndDate = MutableStateFlow(getEndOfToday())
    val filterEndDate: StateFlow<Long> = _filterEndDate.asStateFlow()

    // AI Engine and Chat State
    val aiEngine = AiPosEngine(context)

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(
        listOf(
            AiChatMessage(
                id = "welcome",
                sender = MessageSender.AI,
                text = "မင်္ဂလာပါ! ကျွန်ုပ်သည် 787 AI POS ကွပ်ကဲရေးမှူး ဖြစ်ပါသည်။\n\nဆိုင်၏ ပစ္စည်းအသစ်ထည့်သွင်းခြင်း၊ ဈေးနှုန်း/Code ပြင်ဆင်ခြင်း၊ အခွန်နှုန်းထား သတ်မှတ်ခြင်းနှင့် အရောင်းသုံးသပ်ချက်များကို စာရိုက်၍ တိုက်ရိုက် အမိန့်ပေးခိုင်းစေနိုင်ပါသည် (ဥပမာ - 'ဘိလပ်မြေ ၁၅၀၀၀ ဈေးဖြင့် ထည့်ပါ' သို့မဟုတ် 'အခွန်နှုန်း ၇% သတ်မှတ်ပါ')။",
                timestampMs = System.currentTimeMillis(),
            )
        )
    )
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    init {
        viewModelScope.launch {
            seedConstructionCatalogIfEmpty()
        }
    }

    private suspend fun seedConstructionCatalogIfEmpty() {
        val count = db.productDao().countProducts()
        if (count == 0) {
            val now = System.currentTimeMillis()
            val sampleItems = listOf(
                // Cement Category
                ProductEntity(
                    name = "Crown ဘိလပ်မြေ (Crown Cement)",
                    sku = "CEM-001",
                    barcode = "885011100001",
                    costPriceMinor = 24000,
                    sellPriceMinor = 26500,
                    stockQuantity = 200,
                    category = "ဘိလပ်မြေ",
                    isRoofingSheet = false,
                    lowStockThreshold = 20,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "AAA ဘိလပ်မြေ (AAA Cement)",
                    sku = "CEM-002",
                    barcode = "885011100002",
                    costPriceMinor = 23500,
                    sellPriceMinor = 26000,
                    stockQuantity = 150,
                    category = "ဘိလပ်မြေ",
                    isRoofingSheet = false,
                    lowStockThreshold = 20,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "Apache ဘိလပ်မြေ (Apache Cement)",
                    sku = "CEM-003",
                    barcode = "885011100003",
                    costPriceMinor = 23000,
                    sellPriceMinor = 25500,
                    stockQuantity = 120,
                    category = "ဘိလပ်မြေ",
                    isRoofingSheet = false,
                    lowStockThreshold = 15,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "ဆင် (Elephant) ဘိလပ်မြေ",
                    sku = "CEM-004",
                    barcode = "885011100004",
                    costPriceMinor = 25000,
                    sellPriceMinor = 27500,
                    stockQuantity = 100,
                    category = "ဘိလပ်မြေ",
                    isRoofingSheet = false,
                    lowStockThreshold = 20,
                    updatedAtEpochMs = now,
                ),

                // Roofing Category (သွပ်ပြား - Per Foot Calculation)
                ProductEntity(
                    name = "သွပ်ပြား အနီရောင် (Red Roofing Sheet)",
                    sku = "ROOF-RED",
                    barcode = "885022200001",
                    costPriceMinor = 4200,
                    sellPriceMinor = 5000,
                    stockQuantity = 300,
                    category = "သွပ်ပြား",
                    isRoofingSheet = true,
                    lowStockThreshold = 30,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "သွပ်ပြား အပြာရောင် (Blue Roofing Sheet)",
                    sku = "ROOF-BLU",
                    barcode = "885022200002",
                    costPriceMinor = 4200,
                    sellPriceMinor = 5000,
                    stockQuantity = 250,
                    category = "သွပ်ပြား",
                    isRoofingSheet = true,
                    lowStockThreshold = 25,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "သွပ်ပြား ငွေရောင် (Silver / Galvanized)",
                    sku = "ROOF-SLV",
                    barcode = "885022200003",
                    costPriceMinor = 3800,
                    sellPriceMinor = 4500,
                    stockQuantity = 350,
                    category = "သွပ်ပြား",
                    isRoofingSheet = true,
                    lowStockThreshold = 30,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "သွပ်ပြား အစိမ်းရောင် (Green Roofing Sheet)",
                    sku = "ROOF-GRN",
                    barcode = "885022200004",
                    costPriceMinor = 4300,
                    sellPriceMinor = 5200,
                    stockQuantity = 180,
                    category = "သွပ်ပြား",
                    isRoofingSheet = true,
                    lowStockThreshold = 20,
                    updatedAtEpochMs = now,
                ),

                // Steel & Rebar Category
                ProductEntity(
                    name = "သံချောင်း ၁၀ မီလီ (10mm Rebar)",
                    sku = "STL-10MM",
                    barcode = "885033300001",
                    costPriceMinor = 18000,
                    sellPriceMinor = 21000,
                    stockQuantity = 100,
                    category = "သံချောင်း",
                    isRoofingSheet = false,
                    lowStockThreshold = 10,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "သံချောင်း ၁၂ မီလီ (12mm Rebar)",
                    sku = "STL-12MM",
                    barcode = "885033300002",
                    costPriceMinor = 25000,
                    sellPriceMinor = 29000,
                    stockQuantity = 80,
                    category = "သံချောင်း",
                    isRoofingSheet = false,
                    lowStockThreshold = 10,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "သံကြိုးလိပ် (Binding Wire)",
                    sku = "STL-WIRE",
                    barcode = "885033300003",
                    costPriceMinor = 12000,
                    sellPriceMinor = 15000,
                    stockQuantity = 40,
                    category = "သံချောင်း",
                    isRoofingSheet = false,
                    lowStockThreshold = 5,
                    updatedAtEpochMs = now,
                ),

                // Paint Category
                ProductEntity(
                    name = "TOA 4Seasons အပြင်သုတ်ဆေး (White)",
                    sku = "PNT-TOA-W",
                    barcode = "885044400001",
                    costPriceMinor = 45000,
                    sellPriceMinor = 52000,
                    stockQuantity = 30,
                    category = "ဆေးသုတ်",
                    isRoofingSheet = false,
                    lowStockThreshold = 5,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "Premier သံချေးချွတ်ဆေး (Primer)",
                    sku = "PNT-PRM",
                    barcode = "885044400002",
                    costPriceMinor = 22000,
                    sellPriceMinor = 27000,
                    stockQuantity = 25,
                    category = "ဆေးသုတ်",
                    isRoofingSheet = false,
                    lowStockThreshold = 5,
                    updatedAtEpochMs = now,
                ),

                // PVC Pipes Category
                ProductEntity(
                    name = "Elephant PVC ပိုက် (၄ လက်မ)",
                    sku = "PVC-4IN",
                    barcode = "885055500001",
                    costPriceMinor = 14000,
                    sellPriceMinor = 17500,
                    stockQuantity = 60,
                    category = "ပိုက်နှင့်ဆက်စပ်",
                    isRoofingSheet = false,
                    lowStockThreshold = 10,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "PVC တံတောင်ဆစ် (Elbow 4\")",
                    sku = "PVC-ELB-4",
                    barcode = "885055500002",
                    costPriceMinor = 2500,
                    sellPriceMinor = 3500,
                    stockQuantity = 120,
                    category = "ပိုက်နှင့်ဆက်စပ်",
                    isRoofingSheet = false,
                    lowStockThreshold = 15,
                    updatedAtEpochMs = now,
                ),

                // Hardware & Nails
                ProductEntity(
                    name = "သွပ်မှို ၁ ဘူး (Roofing Nails)",
                    sku = "HDW-RNAIL",
                    barcode = "885066600001",
                    costPriceMinor = 15000,
                    sellPriceMinor = 18500,
                    stockQuantity = 50,
                    category = "သံမှို / ကိရိယာ",
                    isRoofingSheet = false,
                    lowStockThreshold = 8,
                    updatedAtEpochMs = now,
                ),
                ProductEntity(
                    name = "ကွန်ကရစ်မှို ၃ လက်မ (Concrete Nails)",
                    sku = "HDW-CNAIL",
                    barcode = "885066600002",
                    costPriceMinor = 8000,
                    sellPriceMinor = 10500,
                    stockQuantity = 40,
                    category = "သံမှို / ကိရိယာ",
                    isRoofingSheet = false,
                    lowStockThreshold = 5,
                    updatedAtEpochMs = now,
                ),
            )

            sampleItems.forEach {
                db.productDao().insert(it)
            }
        }
    }

    fun updateStoreProfile(profile: StoreProfile) {
        storeConfig.saveProfile(profile)
        _storeProfile.value = profile
        setBanner("ဆိုင်အချက်အလက်များကို သိမ်းဆည်းပြီးပါပြီ")
    }

    fun setTaxRate(percent: Double) {
        val clamped = percent.coerceIn(0.0, 100.0)
        val updatedProfile = _storeProfile.value.copy(taxRatePercent = clamped)
        updateStoreProfile(updatedProfile)
        refreshCartTax(clamped)
    }

    fun refreshCartTax(rate: Double = _storeProfile.value.taxRatePercent) {
        val currentList = _cart.value.map { item ->
            val singleItemTax = ((item.unitPriceMinor * rate) / 100.0).roundToLong()
            item.copy(lineTaxMinor = singleItemTax * item.quantity)
        }
        _cart.value = currentList
        recalculateTotals(currentList)
    }

    fun saveStoreLogo(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetFile = File(context.filesDir, "store_custom_logo.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val updatedProfile = _storeProfile.value.copy(customLogoPath = targetFile.absolutePath)
                storeConfig.saveProfile(updatedProfile)
                _storeProfile.value = updatedProfile
                withContext(Dispatchers.Main) {
                    onResult(true, "ဆိုင် Logo ပုံကို အောင်မြင်စွာ ပြောင်းလဲပြီးပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "ပုံသိမ်းဆည်းခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    fun resetStoreLogo() {
        val targetFile = File(context.filesDir, "store_custom_logo.jpg")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val updatedProfile = _storeProfile.value.copy(customLogoPath = null)
        storeConfig.saveProfile(updatedProfile)
        _storeProfile.value = updatedProfile
        setBanner("မူလ Logo ပုံသို့ ပြန်လည်ပြောင်းလဲပြီးပါပြီ")
    }

    fun sendAiCommand(userPrompt: String) {
        if (userPrompt.isBlank()) return
        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = userPrompt.trim(),
            timestampMs = System.currentTimeMillis(),
        )
        _aiMessages.value = _aiMessages.value + userMsg
        _isAiThinking.value = true

        viewModelScope.launch {
            try {
                val (responseText, action) = aiEngine.processUserCommand(
                    userPrompt = userPrompt,
                    products = allProducts.value,
                    sales = salesHistory.value,
                    storeProfile = _storeProfile.value,
                )
                val aiMsg = AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.AI,
                    text = responseText,
                    timestampMs = System.currentTimeMillis(),
                    action = action,
                )
                _aiMessages.value = _aiMessages.value + aiMsg

                if (aiEngine.isAutoExecute() && action != null) {
                    executeAiAction(action)
                }
            } catch (e: Exception) {
                val errorMsg = AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.AI,
                    text = "တောင်းပန်ပါသည်၊ အမိန့်ကို လုပ်ဆောင်ရာတွင် အမှားဖြစ်ပေါ်ခဲ့ပါသည်: ${e.message}",
                    timestampMs = System.currentTimeMillis(),
                )
                _aiMessages.value = _aiMessages.value + errorMsg
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun executeAiAction(action: AiAction) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                when (val type = action.type) {
                    is AiActionType.AddProduct -> {
                        val entity = ProductEntity(
                            productId = 0,
                            name = type.name,
                            sku = type.sku,
                            barcode = type.barcode,
                            costPriceMinor = type.costPriceMinor,
                            sellPriceMinor = type.sellPriceMinor,
                            stockQuantity = type.stockQuantity,
                            category = type.category,
                            isRoofingSheet = false,
                            lowStockThreshold = 10,
                            imageUri = null,
                            updatedAtEpochMs = now,
                        )
                        db.productDao().insert(entity)
                    }
                    is AiActionType.UpdatePrice -> {
                        db.productDao().updatePrice(type.productId, type.newSellPriceMinor, now)
                    }
                    is AiActionType.UpdateSku -> {
                        db.productDao().updateSku(type.productId, type.newSku, now)
                    }
                    is AiActionType.UpdateBarcode -> {
                        db.productDao().updateBarcode(type.productId, type.newBarcode, now)
                    }
                    is AiActionType.ReplaceProductCode -> {
                        if (type.isBarcode) {
                            db.productDao().updateBarcode(type.productId, type.newCode, now)
                        } else {
                            db.productDao().updateSku(type.productId, type.newCode, now)
                        }
                    }
                    is AiActionType.BatchReplaceCodePrefix -> {
                        val active = allProducts.value
                        active.forEach { p ->
                            val updatedSku = "${type.newPrefix}${p.sku.removePrefix(type.oldPrefix)}"
                            db.productDao().updateSku(p.productId, updatedSku, now)
                        }
                    }
                    is AiActionType.AdjustStock -> {
                        db.productDao().adjustStock(type.productId, type.adjustment, now)
                    }
                    is AiActionType.SetTaxRate -> {
                        setTaxRate(type.newTaxPercent)
                    }
                    is AiActionType.UpdateStoreProfile -> {
                        val current = _storeProfile.value
                        val updated = current.copy(
                            storeName = type.storeName ?: current.storeName,
                            phone = type.phone ?: current.phone,
                            address = type.address ?: current.address,
                        )
                        updateStoreProfile(updated)
                    }
                    is AiActionType.BatchReplaceCategory -> {
                        db.productDao().batchUpdateCategory(type.oldCategory, type.newCategory, now)
                    }
                    is AiActionType.AddPromoCode -> {
                        storeConfig.savePromoCode(
                            PromoCode(
                                code = type.code,
                                discountPercent = type.discountPercent,
                                discountFlatMinor = type.discountFlatMinor,
                                description = type.description,
                                isActive = true,
                            )
                        )
                    }
                    is AiActionType.ReplacePromoCode -> {
                        storeConfig.replacePromoCode(
                            oldCode = type.oldCode,
                            newPromo = PromoCode(
                                code = type.newCode,
                                discountPercent = type.discountPercent,
                                description = type.description,
                                isActive = true,
                            )
                        )
                    }
                    is AiActionType.ExecuteDynamicCode -> {
                        if (type.codeLanguage.equals("SQL", ignoreCase = true) && type.codeScript.isNotBlank()) {
                            try {
                                db.openHelper.writableDatabase.execSQL(type.codeScript)
                            } catch (e: Exception) {
                                // Handled
                            }
                        }
                    }
                    is AiActionType.ApplyCartDiscountByCode -> {
                        applyPromoCodeToCart(type.promoCode)
                    }
                }

                // Mark executed in chat list
                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.action?.id == action.id) {
                        msg.copy(action = msg.action.copy(isExecuted = true))
                    } else msg
                }

                // Log audit event
                db.checkoutDao().insertAudit(
                    AuditEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        actorId = _actor.value.userId,
                        action = "AI_COMMAND_EXECUTED",
                        aggregateType = "AI_ACTION",
                        aggregateId = action.id,
                        reason = action.summary,
                        approverId = "AI_ENGINE",
                        createdAtEpochMs = now,
                        payloadHash = "",
                    )
                )

                setBanner("AI အမိန့် '${action.summary}' ကို အောင်မြင်စွာ လုပ်ဆောင်ပြီးပါပြီ")
            } catch (e: Exception) {
                setBanner("AI အမိန့် လုပ်ဆောင်ခြင်း မအောင်မြင်ပါ: ${e.message}")
            }
        }
    }

    fun clearAiChat() {
        _aiMessages.value = listOf(
            AiChatMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.AI,
                text = "AI စကားပြောမှတ်တမ်းကို ရှင်းလင်းပြီးပါပြီ။ မည်သည့်အရာကို ကူညီဆောင်ရွက်ပေးရမလဲခင်ဗျာ?",
                timestampMs = System.currentTimeMillis(),
            )
        )
    }

    fun getGeminiApiKey(): String = aiEngine.getApiKey()
    fun setGeminiApiKey(key: String) = aiEngine.saveApiKey(key)
    fun isAiAutoExecute(): Boolean = aiEngine.isAutoExecute()
    fun setAiAutoExecute(enabled: Boolean) = aiEngine.setAutoExecute(enabled)
    fun isAiSuperuserGranted(): Boolean = storeConfig.isAiSuperuserGranted()
    fun setAiSuperuserGranted(granted: Boolean) = storeConfig.setAiSuperuserGranted(granted)

    fun getPromoCodes(): List<PromoCode> = storeConfig.getPromoCodes()

    fun applyPromoCodeToCart(promoCodeInput: String): Pair<Boolean, String> {
        val code = promoCodeInput.trim().uppercase()
        if (code.isBlank()) return Pair(false, "ကျေးဇူးပြု၍ Promo Code ရိုက်ထည့်ပါ")
        val promo = storeConfig.getPromoCodes().find { it.code.equals(code, ignoreCase = true) && it.isActive }
            ?: return Pair(false, "မှားယွင်းသော Promo Code သို့မဟုတ် သက်တမ်းကုန်သွားပါပြီ")

        val currentList = _cart.value.toMutableList()
        if (currentList.isEmpty()) {
            return Pair(false, "အော်ဒါထဲတွင် ပစ္စည်းမရှိပါ")
        }

        var totalDiscountApplied = 0L
        if (promo.discountPercent > 0) {
            val discFactor = promo.discountPercent / 100.0
            for (i in currentList.indices) {
                val item = currentList[i]
                val lineTotal = item.unitPriceMinor * item.quantity
                val lineDisc = (lineTotal * discFactor).roundToLong()
                currentList[i] = item.copy(lineDiscountMinor = lineDisc)
                totalDiscountApplied += lineDisc
            }
        } else if (promo.discountFlatMinor > 0) {
            var remaining = promo.discountFlatMinor
            for (i in currentList.indices) {
                val item = currentList[i]
                val lineTotal = item.unitPriceMinor * item.quantity
                val lineDisc = minOf(remaining, lineTotal)
                currentList[i] = item.copy(lineDiscountMinor = lineDisc)
                remaining -= lineDisc
                totalDiscountApplied += lineDisc
                if (remaining <= 0) break
            }
        }

        _cart.value = currentList
        recalculateTotals(currentList)
        val msg = "Promo Code '$code' အောင်မြင်စွာ အသုံးပြုပြီးပါပြီ (လျှော့ငွေ: %,d Ks)".format(totalDiscountApplied)
        setBanner(msg)
        return Pair(true, msg)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun setBanner(message: String) {
        _bannerMessage.value = message
    }

    fun openProductDetails(product: ProductEntity) {
        _detailsProduct.value = product
    }

    fun dismissProductDetails() {
        _detailsProduct.value = null
    }

    fun openRoofingCalculator(product: ProductEntity) {
        _roofingProduct.value = product
    }

    fun dismissRoofingCalculator() {
        _roofingProduct.value = null
    }

    /**
     * Standard product add to cart with wholesale price support
     */
    fun addToCart(product: ProductEntity) {
        if (product.isRoofingSheet) {
            // Roofing sheet requires length in feet calculation!
            openRoofingCalculator(product)
            return
        }

        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.productId == product.productId }

        val currentTaxRate = _storeProfile.value.taxRatePercent
        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity >= product.stockQuantity) {
                setBanner("လက်ကျန်မလုံလောက်ပါ: ${product.stockQuantity} ခုသာ ကျန်ရှိပါသည်")
                return
            }
            val newQty = existing.quantity + 1
            val isWholesale = product.wholesaleMinQty > 0 && newQty >= product.wholesaleMinQty && product.wholesalePriceMinor > 0
            val effectivePrice = if (isWholesale) product.wholesalePriceMinor else product.sellPriceMinor
            val singleTax = ((effectivePrice * currentTaxRate) / 100.0).roundToLong()
            currentList[index] = existing.copy(
                quantity = newQty,
                unitPriceMinor = effectivePrice,
                lineTaxMinor = singleTax * newQty,
                unitName = product.unitName,
                isWholesale = isWholesale,
                costPriceMinor = product.costPriceMinor,
            )
            if (isWholesale && !existing.isWholesale) {
                setBanner("လက်ကားဈေးနှုန်း (Wholesale) အလိုအလျောက် သတ်မှတ်ပေးလိုက်ပါသည်")
            }
        } else {
            if (product.stockQuantity <= 0) {
                setBanner("ပစ္စည်း လက်ကျန် ကုန်နေပါသည်")
                return
            }
            val isWholesale = product.wholesaleMinQty in 1..1 && product.wholesalePriceMinor > 0
            val effectivePrice = if (isWholesale) product.wholesalePriceMinor else product.sellPriceMinor
            val singleTax = ((effectivePrice * currentTaxRate) / 100.0).roundToLong()
            currentList.add(
                CartItem(
                    productId = product.productId,
                    name = product.name,
                    unitPriceMinor = effectivePrice,
                    quantity = 1,
                    lineDiscountMinor = 0,
                    lineTaxMinor = singleTax,
                    unitName = product.unitName,
                    isWholesale = isWholesale,
                    costPriceMinor = product.costPriceMinor,
                )
            )
        }

        _cart.value = currentList
        recalculateTotals(currentList)
    }

    /**
     * Add Roofing Sheet with Length (Feet) × Quantity (Sheets)
     */
    fun addRoofingSheetToCart(product: ProductEntity, lengthFeet: Long, sheetQty: Long) {
        if (sheetQty > product.stockQuantity) {
            setBanner("လက်ကျန်မလုံလောက်ပါ: စုစုပေါင်း ${product.stockQuantity} ချပ်သာ ကျန်ပါသည်")
            return
        }

        val currentList = _cart.value.toMutableList()
        val pricePerSheet = product.sellPriceMinor * lengthFeet
        val currentTaxRate = _storeProfile.value.taxRatePercent
        val singleTax = ((pricePerSheet * currentTaxRate) / 100.0).roundToLong()
        val lineTax = singleTax * sheetQty
        val lineName = "${product.name} (${lengthFeet} ပေ x ${sheetQty} ချပ်)"

        currentList.add(
            CartItem(
                productId = product.productId,
                name = lineName,
                unitPriceMinor = pricePerSheet,
                quantity = sheetQty,
                lineDiscountMinor = 0,
                lineTaxMinor = lineTax,
                unitName = "ချပ်",
                costPriceMinor = product.costPriceMinor * lengthFeet,
            )
        )

        _cart.value = currentList
        recalculateTotals(currentList)
        dismissRoofingCalculator()
        setBanner("သွပ်ပြား ($sheetQty ချပ်) ကို အော်ဒါထဲသို့ ထည့်ပြီးပါပြီ")
    }

    fun updateQuantity(productId: Long, quantity: Long) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            if (quantity <= 0) {
                currentList.removeAt(index)
            } else {
                val existing = currentList[index]
                val product = allProducts.value.find { it.productId == productId }
                val isWholesale = product != null && product.wholesaleMinQty > 0 && quantity >= product.wholesaleMinQty && product.wholesalePriceMinor > 0
                val effectivePrice = if (isWholesale) product!!.wholesalePriceMinor else (product?.sellPriceMinor ?: existing.unitPriceMinor)
                val currentTaxRate = _storeProfile.value.taxRatePercent
                val singleTax = ((effectivePrice * currentTaxRate) / 100.0).roundToLong()
                currentList[index] = existing.copy(
                    quantity = quantity,
                    unitPriceMinor = effectivePrice,
                    lineTaxMinor = singleTax * quantity,
                    isWholesale = isWholesale,
                )
            }
            _cart.value = currentList
            recalculateTotals(currentList)
        }
    }

    fun removeFromCart(productId: Long) {
        val currentList = _cart.value.filter { it.productId != productId }
        _cart.value = currentList
        recalculateTotals(currentList)
    }

    fun applyDiscount(productId: Long, discountAmountMinor: Long) {
        if (!_actor.value.canApplyDiscount) {
            setBanner("လျှော့စျေးပေးရန် မန်နေဂျာ PIN လိုအပ်ပါသည်")
            return
        }
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val existing = currentList[index]
            val maxDiscount = existing.unitPriceMinor * existing.quantity
            val safeDiscount = discountAmountMinor.coerceIn(0, maxDiscount)
            currentList[index] = existing.copy(lineDiscountMinor = safeDiscount)
            _cart.value = currentList
            recalculateTotals(currentList)
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _totals.value = CartTotals(0, 0, 0, 0)
    }

    private fun recalculateTotals(items: List<CartItem>) {
        if (items.isEmpty()) {
            _totals.value = CartTotals(0, 0, 0, 0)
            return
        }
        when (val res = calculateTotals(items)) {
            is TotalsResult.Ok -> _totals.value = res.totals
            is TotalsResult.Invalid -> {
                setBanner("တွက်ချက်မှု အမှား: ${res.reason}")
            }
        }
    }

    fun authenticateManager(pin: String): Boolean {
        return if (pin == "1234") {
            _actor.value = Actor(userId = "မန်နေဂျာ (Manager)", canApplyDiscount = true)
            setBanner("မန်နေဂျာ စနစ် ဖွင့်လှစ်ပြီးပါပြီ")
            true
        } else {
            setBanner("မှားယွင်းသော PIN ဖြစ်ပါသည်")
            false
        }
    }

    fun setCashierMode() {
        _actor.value = Actor(userId = "အရောင်းတာဝန်ခံ (Cashier #1)", canApplyDiscount = false)
        setBanner("အရောင်းတာဝန်ခံ စနစ်သို့ ပြောင်းလဲပြီးပါပြီ")
    }

    fun checkout(
        paymentMethod: String,
        tenderedMinor: Long,
        selectedCustomer: CustomerEntity? = null,
        splitCashMinor: Long = 0L,
        splitDigitalMinor: Long = 0L,
        onComplete: (Boolean, String) -> Unit,
    ) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) {
            onComplete(false, "အော်ဒါထဲတွင် ပစ္စည်းမရှိပါ")
            return
        }
        val currentTotals = _totals.value
        val isCredit = paymentMethod == "CREDIT"
        if (!isCredit && tenderedMinor < currentTotals.totalMinor) {
            onComplete(false, "ပေးငွေ မလုံလောက်ပါ")
            return
        }

        if (isCredit && selectedCustomer == null) {
            onComplete(false, "အကြွေးအရောင်းအတွက် ဝယ်သူကို ရွေးချယ်ပေးပါ")
            return
        }

        viewModelScope.launch {
            val paymentRequest = PaymentRequest(
                method = paymentMethod,
                tenderedMinor = tenderedMinor,
                authorizationState = "APPROVED",
                providerReference = if (paymentMethod == "CASH") null else "TXN-${System.currentTimeMillis()}",
                manualRecorded = false,
            )

            when (val result = checkoutRepo.checkout(cartItems, paymentRequest, _actor.value)) {
                is CheckoutResult.Committed -> {
                    val items = cartItems.map { item ->
                        SaleItemEntity(
                            saleId = result.saleId,
                            productId = item.productId,
                            productNameSnapshot = item.name,
                            unitPriceMinor = item.unitPriceMinor,
                            quantity = item.quantity,
                            lineDiscountMinor = item.lineDiscountMinor,
                            lineTaxMinor = item.lineTaxMinor,
                            lineTotalMinor = (item.unitPriceMinor * item.quantity) - item.lineDiscountMinor + item.lineTaxMinor,
                            unitName = item.unitName,
                        )
                    }

                    // Handle Customer Credit Debt ledger
                    if (isCredit && selectedCustomer != null) {
                        val unpaidDebt = (currentTotals.totalMinor - tenderedMinor).coerceAtLeast(0L)
                        db.customerDao().addDebt(selectedCustomer.customerId, unpaidDebt)
                        db.customerDao().insertDebtTransaction(
                            DebtTransactionEntity(
                                customerId = selectedCustomer.customerId,
                                saleId = result.saleId,
                                type = "CREDIT_SALE",
                                amountMinor = unpaidDebt,
                                remainingBalanceMinor = selectedCustomer.currentDebtMinor + unpaidDebt,
                                note = "အကြွေးအရောင်း (Sale #${result.saleId.take(8)})",
                                cashierId = _actor.value.userId
                            )
                        )
                    }

                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val receiptData = ReceiptData(
                        saleId = result.saleId,
                        businessDate = dateStr,
                        items = items,
                        totals = currentTotals,
                        paymentMethod = paymentMethod,
                        tenderedMinor = tenderedMinor,
                        changeMinor = result.changeMinor,
                        timestampEpochMs = System.currentTimeMillis(),
                        cashierId = _actor.value.userId,
                        deviceId = deviceId,
                        customerName = selectedCustomer?.name,
                        splitCashMinor = splitCashMinor,
                        splitDigitalMinor = splitDigitalMinor,
                    )

                    _receipt.value = receiptData
                    clearCart()
                    val successMsg = if (isCredit) {
                        "အကြွေးအရောင်း မှတ်တမ်းတင်ပြီးပါပြီ (ဝယ်သူ: ${selectedCustomer?.name})"
                    } else {
                        "အရောင်းအောင်မြင်ပါသည်! ပြန်အမ်းငွေ: %,d Ks".format(result.changeMinor)
                    }
                    onComplete(true, successMsg)
                }
                is CheckoutResult.Rejected -> {
                    onComplete(false, result.reason)
                }
            }
        }
    }

    // Role switching
    fun switchRole(role: String, pin: String): Boolean {
        return when (role) {
            "OWNER" -> {
                if (pin == "7870") {
                    _currentRole.value = "OWNER"
                    _actor.value = Actor(userId = "ဆိုင်ရှင် (Store Owner)", canApplyDiscount = true)
                    setBanner("ဆိုင်ရှင် (Owner) စနစ်သို့ ပြောင်းလဲပြီးပါပြီ")
                    true
                } else {
                    setBanner("ဆိုင်ရှင် PIN မှားယွင်းပါသည် (Default: 7870)")
                    false
                }
            }
            "MANAGER" -> {
                if (pin == "1234") {
                    _currentRole.value = "MANAGER"
                    _actor.value = Actor(userId = "မန်နေဂျာ (Manager)", canApplyDiscount = true)
                    setBanner("မန်နေဂျာ (Manager) စနစ်သို့ ပြောင်းလဲပြီးပါပြီ")
                    true
                } else {
                    setBanner("မန်နေဂျာ PIN မှားယွင်းပါသည် (Default: 1234)")
                    false
                }
            }
            else -> {
                _currentRole.value = "CASHIER"
                _actor.value = Actor(userId = "အရောင်းတာဝန်ခံ (Cashier #1)", canApplyDiscount = false)
                setBanner("အရောင်းတာဝန်ခံ (Cashier) စနစ်သို့ ပြောင်းလဲပြီးပါပြီ")
                true
            }
        }
    }

    // Customer CRM & Debt
    fun createCustomer(name: String, phone: String, address: String, creditLimit: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customer = CustomerEntity(
                    name = name,
                    phone = phone,
                    address = address,
                    creditLimitMinor = creditLimit,
                    currentDebtMinor = 0L,
                    createdAtEpochMs = System.currentTimeMillis()
                )
                db.customerDao().insertCustomer(customer)
                withContext(Dispatchers.Main) {
                    onResult(true, "ဝယ်သူ '$name' ကို စာရင်းသွင်းပြီးပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "ဝယ်သူသွင်းခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    fun recordRepayment(customerId: Long, amount: Long, note: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.customerDao().recordRepayment(customerId, amount, note, _actor.value.userId)
                withContext(Dispatchers.Main) {
                    onResult(true, "အကြွေးဆပ်ငွေ %,d Ks လက်ခံပြီးပါပြီ".format(amount))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "အကြွေးဆပ်ငွေမှတ်တမ်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    fun getCustomerTransactions(customerId: Long) = db.customerDao().observeTransactionsForCustomer(customerId)

    // Hold / Park Orders
    fun parkCurrentCart(tag: String) {
        val currentList = _cart.value
        if (currentList.isEmpty()) return
        val currentTot = _totals.value
        viewModelScope.launch(Dispatchers.IO) {
            val jsonArray = JSONArray()
            currentList.forEach { item ->
                val obj = JSONObject().apply {
                    put("productId", item.productId)
                    put("name", item.name)
                    put("unitPriceMinor", item.unitPriceMinor)
                    put("quantity", item.quantity)
                    put("lineDiscountMinor", item.lineDiscountMinor)
                    put("lineTaxMinor", item.lineTaxMinor)
                    put("unitName", item.unitName)
                    put("isWholesale", item.isWholesale)
                    put("costPriceMinor", item.costPriceMinor)
                }
                jsonArray.put(obj)
            }
            val parkId = UUID.randomUUID().toString()
            val order = ParkedOrderEntity(
                parkId = parkId,
                tag = tag,
                itemsJson = jsonArray.toString(),
                totalMinor = currentTot.totalMinor,
                itemCount = currentList.size.toLong(),
                createdAtEpochMs = System.currentTimeMillis()
            )
            db.operationsDao().insertParkedOrder(order)
            withContext(Dispatchers.Main) {
                clearCart()
                setBanner("အော်ဒါ '$tag' ကို ဆိုင်းငံ့ထားပြီးပါပြီ")
            }
        }
    }

    fun resumeParkedOrder(parkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val order = parkedOrders.value.find { it.parkId == parkId } ?: return@launch
            try {
                val array = JSONArray(order.itemsJson)
                val restoredItems = mutableListOf<CartItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    restoredItems.add(
                        CartItem(
                            productId = obj.getLong("productId"),
                            name = obj.getString("name"),
                            unitPriceMinor = obj.getLong("unitPriceMinor"),
                            quantity = obj.getLong("quantity"),
                            lineDiscountMinor = obj.optLong("lineDiscountMinor", 0L),
                            lineTaxMinor = obj.optLong("lineTaxMinor", 0L),
                            unitName = obj.optString("unitName", "ခု"),
                            isWholesale = obj.optBoolean("isWholesale", false),
                            costPriceMinor = obj.optLong("costPriceMinor", 0L)
                        )
                    )
                }
                db.operationsDao().deleteParkedOrder(parkId)
                withContext(Dispatchers.Main) {
                    _cart.value = restoredItems
                    recalculateTotals(restoredItems)
                    setBanner("ဆိုင်းငံ့အော်ဒါ '${order.tag}' ကို ပြန်လည်ဖွင့်လိုက်ပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setBanner("အော်ဒါပြန်ဖွင့်ခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    fun deleteParkedOrder(parkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.operationsDao().deleteParkedOrder(parkId)
            withContext(Dispatchers.Main) {
                setBanner("ဆိုင်းငံ့အော်ဒါကို ဖျက်လိုက်ပါပြီ")
            }
        }
    }

    // Stock-In Purchase & Restocking
    fun recordStockInPurchase(
        productId: Long,
        productName: String,
        supplierName: String,
        quantity: Long,
        unitCost: Long,
        invoiceRef: String,
        note: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalCost = quantity * unitCost
                val purchase = PurchaseEntity(
                    productId = productId,
                    productName = productName,
                    supplierName = supplierName,
                    quantity = quantity,
                    unitCostMinor = unitCost,
                    totalCostMinor = totalCost,
                    invoiceRef = invoiceRef,
                    note = note,
                    createdAtEpochMs = System.currentTimeMillis()
                )
                db.operationsDao().insertPurchase(purchase)
                val now = System.currentTimeMillis()
                db.productDao().adjustStock(productId, quantity, now)

                withContext(Dispatchers.Main) {
                    onResult(true, "'$productName' အဝင် (+$quantity) စာရင်းသွင်းပြီးပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "ပစ္စည်းအဝင်သွင်းခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    // Shift Closing & Z-Report
    fun recordShiftClosing(
        openingCash: Long,
        cashSales: Long,
        digitalSales: Long,
        creditSales: Long,
        expense: Long,
        expectedCash: Long,
        actualCash: Long,
        diff: Long,
        note: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val shift = ShiftReportEntity(
                    cashierName = _actor.value.userId,
                    openTimeEpochMs = getStartOfToday(),
                    closeTimeEpochMs = now,
                    openingCashMinor = openingCash,
                    cashSalesMinor = cashSales,
                    digitalSalesMinor = digitalSales,
                    creditSalesMinor = creditSales,
                    expenseMinor = expense,
                    expectedCashMinor = expectedCash,
                    actualCashCountedMinor = actualCash,
                    differenceMinor = diff,
                    status = "CLOSED",
                    note = note
                )
                db.operationsDao().insertShift(shift)
                withContext(Dispatchers.Main) {
                    onResult(true, "နေ့ချုပ်စာရင်း (Z-Report) အောင်မြင်စွာ ပိတ်ပြီးပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "နေ့ချုပ်ပိတ်ခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    // Barcode scanner handling
    fun handleScannedBarcode(barcode: String, onNotFound: () -> Unit = {}) {
        val clean = barcode.trim()
        val foundProduct = allProducts.value.find {
            it.barcode.equals(clean, ignoreCase = true) || it.sku.equals(clean, ignoreCase = true)
        }
        if (foundProduct != null) {
            addToCart(foundProduct)
            setBanner("Barcode '${foundProduct.name}' ကို Cart ထဲ ထည့်ပြီးပါပြီ")
        } else {
            setBanner("Barcode '$clean' ဖြင့် ပစ္စည်း ရှာမတွေ့ပါ")
            onNotFound()
        }
    }

    // PDF Receipt Export & Share
    fun sharePdfReceipt(receipt: ReceiptData) {
        PdfReceiptHelper.generateAndSharePdf(context, receipt, _storeProfile.value)
    }

    fun dismissReceipt() {
        _receipt.value = null
    }

    fun viewPastReceipt(sale: SaleEntity) {
        viewModelScope.launch {
            val items = db.checkoutDao().getItemsForSale(sale.saleId)
            val payment = db.checkoutDao().getPaymentForSale(sale.saleId)
            val receiptData = ReceiptData(
                saleId = sale.saleId,
                businessDate = sale.businessDate,
                items = items,
                totals = CartTotals(
                    subtotalMinor = sale.subtotalMinor,
                    discountMinor = sale.discountMinor,
                    taxMinor = sale.taxMinor,
                    totalMinor = sale.totalMinor,
                ),
                paymentMethod = payment?.method ?: sale.paymentMethod,
                tenderedMinor = payment?.tenderedMinor ?: sale.totalMinor,
                changeMinor = payment?.changeMinor ?: 0,
                timestampEpochMs = sale.createdAtEpochMs,
                cashierId = sale.cashierId,
                deviceId = sale.deviceId,
                customerName = sale.customerName,
                splitCashMinor = sale.splitCashMinor,
                splitDigitalMinor = sale.splitDigitalMinor,
            )
            _receipt.value = receiptData
        }
    }

    /**
     * Copy user-picked photo from Gallery into internal storage
     */
    fun saveImageFromGalleryUri(uri: Uri): String? {
        return try {
            val imageDir = File(context.filesDir, "product_images").apply { mkdirs() }
            val destFile = File(imageDir, "prod_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun addNewProduct(draft: ProductDraft, sku: String, onResult: (Boolean, String) -> Unit) {
        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            onResult(false, errors.first())
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val newEntity = ProductEntity(
                    name = draft.name,
                    sku = sku.ifBlank { "PROD-${System.currentTimeMillis() % 10000}" },
                    barcode = draft.barcode,
                    costPriceMinor = draft.costPriceMinor,
                    sellPriceMinor = draft.sellPriceMinor,
                    stockQuantity = draft.stockQuantity,
                    category = draft.category.ifBlank { "အထွေထွေ" },
                    isRoofingSheet = draft.isRoofingSheet,
                    lowStockThreshold = draft.lowStockThreshold,
                    imageUri = draft.imageUri,
                    updatedAtEpochMs = now,
                )
                db.productDao().insert(newEntity)
                onResult(true, "ကုန်ပစ္စည်း အသစ်ကို အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ")
            } catch (e: Exception) {
                onResult(false, e.message ?: "ကုန်ပစ္စည်းထည့်သွင်းခြင်း မအောင်မြင်ပါ")
            }
        }
    }

    fun adjustStock(productId: Long, adjustment: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.productDao().adjustStock(productId, adjustment, now)
            setBanner("လက်ကျန်စာရင်း ပြင်ဆင်ပြီးပါပြီ")
        }
    }

    fun archiveProduct(productId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.productDao().archive(productId, now)
            setBanner("ကုန်ပစ္စည်းကို ဖျက်သိမ်းပြီးပါပြီ")
        }
    }

    /**
     * Print receipt via Bluetooth Thermal Printer
     */
    fun printReceiptBluetooth(device: BluetoothDevice, receipt: ReceiptData, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = BluetoothPrinterHelper.printReceipt(device, receipt, _storeProfile.value)
            onResult(result.first, result.second)
        }
    }

    /**
     * SQLite Database Export & Share (Google Drive / Files)
     */
    fun exportDatabaseFile(): File? {
        return try {
            val dbFile = context.getDatabasePath("offline_pos.db")
            if (!dbFile.exists()) return null
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val targetFile = File(exportDir, "santhit_pos_backup_$timeStamp.db")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * SQLite Database Import & Restore
     */
    fun importDatabaseFile(sourceUri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.closeAndReset()
                val dbFile = context.getDatabasePath("offline_pos.db")

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Reopen DB instance
                db = AppDatabase.get(context)
                withContext(Dispatchers.Main) {
                    onComplete(true, "ဒေတာဘေ့စ်ကို အောင်မြင်စွာ Restore ပြုလုပ်ပြီးပါပြီ")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Restore ပြုလုပ်ခြင်း မအောင်မြင်ပါ: ${e.message}")
                }
            }
        }
    }

    fun setDateFilter(startMs: Long, endMs: Long) {
        _filterStartDate.value = startMs
        _filterEndDate.value = endMs
    }

    companion object {
        fun getStartOfToday(): Long {
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        fun getEndOfToday(): Long {
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        }
    }
}
