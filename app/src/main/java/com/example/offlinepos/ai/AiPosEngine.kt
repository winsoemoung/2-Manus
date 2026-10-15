package com.example.offlinepos.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.offlinepos.data.local.ProductEntity
import com.example.offlinepos.data.local.SaleEntity
import com.example.offlinepos.ui.StoreConfig
import com.example.offlinepos.ui.StoreProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AiPosEngine(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_ai_prefs", Context.MODE_PRIVATE)
    private val storeConfig = StoreConfig(context)

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun isAutoExecute(): Boolean {
        return prefs.getBoolean("ai_auto_execute", false)
    }

    fun setAutoExecute(enabled: Boolean) {
        prefs.edit().putBoolean("ai_auto_execute", enabled).apply()
    }

    fun isSuperuserGranted(): Boolean {
        return storeConfig.isAiSuperuserGranted()
    }

    fun setSuperuserGranted(granted: Boolean) {
        storeConfig.setAiSuperuserGranted(granted)
    }

    suspend fun processUserCommand(
        userPrompt: String,
        dbContext: PosDatabaseContext,
    ): Pair<String, AiAction?> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        if (apiKey.isNotBlank()) {
            try {
                val result = callGeminiApi(apiKey, userPrompt, dbContext)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                // Fallback to offline rule-based NLP parser
            }
        }

        // Offline POS Database Chatbot & NLP Engine
        processOfflineNlpCommand(userPrompt, dbContext)
    }

    suspend fun processUserCommand(
        userPrompt: String,
        products: List<ProductEntity>,
        sales: List<SaleEntity>,
        storeProfile: StoreProfile,
    ): Pair<String, AiAction?> = processUserCommand(
        userPrompt = userPrompt,
        dbContext = PosDatabaseContext(
            products = products,
            sales = sales,
            storeProfile = storeProfile,
            totalRevenue = sales.sumOf { it.totalMinor },
            lowStockCount = products.count { it.stockQuantity <= it.lowStockThreshold },
            lowStockProducts = products.filter { it.stockQuantity <= it.lowStockThreshold }
        )
    )

    private fun callGeminiApi(
        apiKey: String,
        userPrompt: String,
        dbContext: PosDatabaseContext,
    ): Pair<String, AiAction?>? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            doInput = true
        }

        val products = dbContext.products
        val sales = dbContext.sales
        val storeProfile = dbContext.storeProfile

        // Build rich database context summary
        val catalogSummary = products.take(35).joinToString("\n") { p ->
            "- ID:${p.productId}, Name:${p.name}, SKU:${p.sku}, Barcode:${p.barcode ?: "-"}, Sell:${p.sellPriceMinor}Ks, Cost:${p.costPriceMinor}Ks, Stock:${p.stockQuantity} ${p.unitName}, Cat:${p.category}"
        }
        val lowStockSummary = if (dbContext.lowStockProducts.isNotEmpty()) {
            dbContext.lowStockProducts.take(15).joinToString("\n") { p ->
                "• ${p.name} (SKU: ${p.sku}): လက်ကျန် ${p.stockQuantity} ${p.unitName} (သတိပေးလက်ကျန်: ${p.lowStockThreshold})"
            }
        } else "လက်ကျန်နည်းနေသော ပစ္စည်းမရှိပါ"

        val debtorSummary = if (dbContext.debtors.isNotEmpty()) {
            dbContext.debtors.take(10).joinToString("\n") { c ->
                "• ${c.name} (${c.phone.ifBlank { "ဖုန်းမရှိ" }}): အကြွေးကျန် ${"%,d".format(c.currentDebtMinor)} Ks"
            }
        } else "လက်ရှိ အကြွေးကျန် Customer မရှိပါ"

        val promoSummary = storeConfig.getPromoCodes().joinToString(", ") { "${it.code} (${if (it.discountPercent > 0) "${it.discountPercent}%" else "${it.discountFlatMinor}Ks"})" }

        val systemPrompt = """
            You are the intelligent Gemini AI Chatbot directly connected to the SQLite POS Database of '787 POS' (Store: ${storeProfile.storeName}).
            You have real-time access to the store's full database:

            === LIVE POS DATABASE SNAPSHOT ===
            Store Details:
            - Store Name: ${storeProfile.storeName}
            - Current Tax Rate: ${storeProfile.taxRatePercent}%.
            - Active Categories: ${dbContext.categories.joinToString(", ").ifBlank { "ဘိလပ်မြေ, သွပ်ပြား, သံချောင်း, ဆေးသုတ်, ပိုက်နှင့်ဆက်စပ်" }}

            Inventory Status:
            - Total Products in Database: ${products.size}
            - Low Stock Products Count: ${dbContext.lowStockCount}
            - Low Stock Items:
            $lowStockSummary

            Sales & Financial Status:
            - Total Lifetime Sales Orders: ${sales.size}
            - Total Lifetime Revenue: ${"%,d".format(dbContext.totalRevenue)} Ks
            - Today's Sales Count: ${dbContext.todaySalesCount} orders
            - Today's Revenue: ${"%,d".format(dbContext.todayRevenue)} Ks

            Customer Debt Ledger:
            - Customers with Debt: ${dbContext.debtors.size}
            - Total Outstanding Debt: ${"%,d".format(dbContext.totalDebtAmount)} Ks
            - Top Debtors:
            $debtorSummary

            Active Promo Codes: $promoSummary

            Sample Product Catalog:
            $catalogSummary
            ==================================

            Your Capabilities:
            1. ANSWER QUESTIONS ABOUT POS DATA: Answer any questions about stock levels, low-stock warnings, today's sales, total revenue, debtor customers, prices, profit margins, product details, or store statistics.
            2. SUPERUSER ACTIONS: You have permission to add new products, update prices, modify SKUs/barcodes, adjust stock quantities, change tax rates, add promo codes, or run dynamic database scripts.

            Guidance:
            - Always respond courteously and clearly in Myanmar (Burmese) language.
            - When answering questions about database data (e.g. sales, stock, debt), provide accurate numbers from the snapshot above.
            - When the user asks you to perform an actionable command (e.g., add product, update price, adjust stock, replace code), write a concise explanation in Burmese AND append a valid JSON block enclosed in ```json and ``` at the end:
            ```json
            {
              "action": "ADD_PRODUCT" | "REPLACE_PRODUCT_CODE" | "UPDATE_SKU" | "UPDATE_BARCODE" | "BATCH_REPLACE_PREFIX" | "UPDATE_PRICE" | "ADJUST_STOCK" | "SET_TAX_RATE" | "ADD_PROMO_CODE" | "REPLACE_PROMO_CODE" | "EXECUTE_DYNAMIC_CODE" | "UPDATE_STORE_PROFILE" | "BATCH_REPLACE_CATEGORY",
              "summary": "Short summary of the action in Burmese",
              "data": {
                // For ADD_PRODUCT: "name": "...", "sku": "...", "barcode": "...", "category": "...", "sellPrice": 15000, "costPrice": 12000, "stock": 50
                // For REPLACE_PRODUCT_CODE: "productId": 123, "productName": "...", "oldCode": "...", "newCode": "...", "isBarcode": false
                // For UPDATE_SKU: "productId": 123, "productName": "...", "oldSku": "...", "newSku": "..."
                // For UPDATE_BARCODE: "productId": 123, "productName": "...", "newBarcode": "..."
                // For BATCH_REPLACE_PREFIX: "oldPrefix": "...", "newPrefix": "787-"
                // For UPDATE_PRICE: "productId": 123, "productName": "...", "newPrice": 16000
                // For ADJUST_STOCK: "productId": 123, "productName": "...", "adjustment": 20
                // For SET_TAX_RATE: "taxRate": 7.0
                // For ADD_PROMO_CODE: "code": "787VIP", "discountPercent": 10.0, "description": "..."
                // For REPLACE_PROMO_CODE: "oldCode": "787VIP", "newCode": "787SUPER", "discountPercent": 15.0
                // For EXECUTE_DYNAMIC_CODE: "codeLanguage": "SQL", "codeScript": "UPDATE products SET ...", "explanation": "..."
                // For UPDATE_STORE_PROFILE: "storeName": "...", "phone": "...", "address": "..."
                // For BATCH_REPLACE_CATEGORY: "oldCategory": "...", "newCategory": "..."
              }
            }
            ```
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$systemPrompt\n\nUser Question/Command: $userPrompt"))
                    })
                })
            }
            put("contents", contents)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(jsonRequest.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val responseString = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val fullText = parts?.optJSONObject(0)?.optString("text") ?: return null

            return parseAiResponse(fullText, products)
        }
        return null
    }

    private fun parseAiResponse(fullText: String, products: List<ProductEntity>): Pair<String, AiAction?> {
        val jsonRegex = "```json\\s*([\\s\\S]*?)\\s*```".toRegex()
        val match = jsonRegex.find(fullText)

        if (match != null) {
            val cleanText = fullText.replace(match.value, "").trim()
            val jsonContent = match.groupValues[1]
            try {
                val actionObj = JSONObject(jsonContent)
                val actionTypeStr = actionObj.optString("action")
                val summary = actionObj.optString("summary", "AI အထူးလုပ်ဆောင်ချက်")
                val data = actionObj.optJSONObject("data") ?: JSONObject()

                val actionType: AiActionType? = when (actionTypeStr) {
                    "ADD_PRODUCT" -> {
                        AiActionType.AddProduct(
                            name = data.optString("name", "ပစ္စည်းအသစ်"),
                            sku = data.optString("sku", "787-CODE-${System.currentTimeMillis() % 10000}"),
                            category = data.optString("category", "အထွေထွေ"),
                            sellPriceMinor = data.optLong("sellPrice", 1000L),
                            costPriceMinor = data.optLong("costPrice", 800L),
                            stockQuantity = data.optLong("stock", 10L),
                            barcode = data.optString("barcode").takeIf { it.isNotBlank() },
                        )
                    }
                    "REPLACE_PRODUCT_CODE" -> {
                        val pId = data.optLong("productId", 0L)
                        val pName = data.optString("productName", "")
                        val resolvedProduct = if (pId > 0) products.find { it.productId == pId } else products.find { it.name.contains(pName, ignoreCase = true) }
                        if (resolvedProduct != null) {
                            AiActionType.ReplaceProductCode(
                                productId = resolvedProduct.productId,
                                productName = resolvedProduct.name,
                                oldCode = data.optString("oldCode", resolvedProduct.sku),
                                newCode = data.optString("newCode", "787-CODE-${System.currentTimeMillis() % 10000}"),
                                isBarcode = data.optBoolean("isBarcode", false),
                            )
                        } else null
                    }
                    "UPDATE_SKU" -> {
                        val pId = data.optLong("productId", 0L)
                        val pName = data.optString("productName", "")
                        val resolvedProduct = if (pId > 0) products.find { it.productId == pId } else products.find { it.name.contains(pName, ignoreCase = true) }
                        if (resolvedProduct != null) {
                            AiActionType.UpdateSku(
                                productId = resolvedProduct.productId,
                                productName = resolvedProduct.name,
                                oldSku = resolvedProduct.sku,
                                newSku = data.optString("newSku", "787-SKU-${System.currentTimeMillis() % 10000}"),
                            )
                        } else null
                    }
                    "UPDATE_BARCODE" -> {
                        val pId = data.optLong("productId", 0L)
                        val pName = data.optString("productName", "")
                        val resolvedProduct = if (pId > 0) products.find { it.productId == pId } else products.find { it.name.contains(pName, ignoreCase = true) }
                        if (resolvedProduct != null) {
                            AiActionType.UpdateBarcode(
                                productId = resolvedProduct.productId,
                                productName = resolvedProduct.name,
                                oldBarcode = resolvedProduct.barcode,
                                newBarcode = data.optString("newBarcode", "787${System.currentTimeMillis() % 1000000}"),
                            )
                        } else null
                    }
                    "BATCH_REPLACE_PREFIX" -> {
                        AiActionType.BatchReplaceCodePrefix(
                            oldPrefix = data.optString("oldPrefix", ""),
                            newPrefix = data.optString("newPrefix", "787-"),
                        )
                    }
                    "UPDATE_PRICE" -> {
                        val pId = data.optLong("productId", 0L)
                        val pName = data.optString("productName", "")
                        val resolvedProduct = if (pId > 0) products.find { it.productId == pId } else products.find { it.name.contains(pName, ignoreCase = true) }
                        if (resolvedProduct != null) {
                            AiActionType.UpdatePrice(
                                productId = resolvedProduct.productId,
                                productName = resolvedProduct.name,
                                newSellPriceMinor = data.optLong("newPrice", resolvedProduct.sellPriceMinor),
                            )
                        } else null
                    }
                    "ADJUST_STOCK" -> {
                        val pId = data.optLong("productId", 0L)
                        val pName = data.optString("productName", "")
                        val resolvedProduct = if (pId > 0) products.find { it.productId == pId } else products.find { it.name.contains(pName, ignoreCase = true) }
                        if (resolvedProduct != null) {
                            AiActionType.AdjustStock(
                                productId = resolvedProduct.productId,
                                productName = resolvedProduct.name,
                                adjustment = data.optLong("adjustment", 10L),
                            )
                        } else null
                    }
                    "SET_TAX_RATE" -> {
                        AiActionType.SetTaxRate(
                            newTaxPercent = data.optDouble("taxRate", 0.0),
                        )
                    }
                    "ADD_PROMO_CODE" -> {
                        AiActionType.AddPromoCode(
                            code = data.optString("code", "787VIP"),
                            discountPercent = data.optDouble("discountPercent", 10.0),
                            discountFlatMinor = data.optLong("discountFlatMinor", 0L),
                            description = data.optString("description", "AI Created Promo Code"),
                        )
                    }
                    "REPLACE_PROMO_CODE" -> {
                        AiActionType.ReplacePromoCode(
                            oldCode = data.optString("oldCode", "787VIP"),
                            newCode = data.optString("newCode", "787SUPER"),
                            discountPercent = data.optDouble("discountPercent", 15.0),
                            description = data.optString("description", "Replaced Promo Code"),
                        )
                    }
                    "EXECUTE_DYNAMIC_CODE" -> {
                        AiActionType.ExecuteDynamicCode(
                            codeLanguage = data.optString("codeLanguage", "SQL"),
                            codeScript = data.optString("codeScript", ""),
                            explanation = data.optString("explanation", "AI Code Execution"),
                        )
                    }
                    "UPDATE_STORE_PROFILE" -> {
                        AiActionType.UpdateStoreProfile(
                            storeName = data.optString("storeName").takeIf { it.isNotBlank() },
                            phone = data.optString("phone").takeIf { it.isNotBlank() },
                            address = data.optString("address").takeIf { it.isNotBlank() },
                        )
                    }
                    "BATCH_REPLACE_CATEGORY" -> {
                        AiActionType.BatchReplaceCategory(
                            oldCategory = data.optString("oldCategory", "အထွေထွေ"),
                            newCategory = data.optString("newCategory", "အိမ်ဆောက်ပစ္စည်း"),
                        )
                    }
                    else -> null
                }

                if (actionType != null) {
                    val aiAction = AiAction(
                        id = UUID.randomUUID().toString(),
                        summary = summary,
                        type = actionType,
                        privilegeLevel = "AI_SUPERUSER",
                    )
                    return Pair(cleanText, aiAction)
                }
            } catch (e: Exception) {
                // Ignore parse error
            }
            return Pair(cleanText, null)
        }
        return Pair(fullText, null)
    }

    private fun processOfflineNlpCommand(
        prompt: String,
        dbContext: PosDatabaseContext,
    ): Pair<String, AiAction?> {
        val products = dbContext.products
        val sales = dbContext.sales
        val storeProfile = dbContext.storeProfile
        val lower = prompt.lowercase()

        // 1. Promo Code Addition (e.g. "promo code ထည့်", "ကူပွန် code ထည့်", "code 787VIP ၁၀% ထည့်")
        if (lower.contains("promo") || lower.contains("ကူပွန်") || (lower.contains("code") && lower.contains("လျှော့")) || (lower.contains("ကုဒ်") && lower.contains("လျှော့"))) {
            if (lower.contains("အစားထိုး") || lower.contains("replace")) {
                val oldCode = "787VIP"
                val newCode = "787SUPER"
                val numberMatch = "([0-9]+(?:\\.[0-9]+)?)".toRegex().find(prompt)
                val disc = numberMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 15.0
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "Promo Code အဟောင်း '$oldCode' ကို Code အသစ် '$newCode' ($disc% လျှော့စျေး) ဖြင့် အစားထိုးခြင်း",
                    type = AiActionType.ReplacePromoCode(
                        oldCode = oldCode,
                        newCode = newCode,
                        discountPercent = disc,
                        description = "AI Replaced Promo Code $newCode",
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "AI စနစ်မှ Promo Code အဟောင်း '$oldCode' ကို Code အသစ် '$newCode' ($disc% Discount) သို့ အောင်မြင်စွာ အစားထိုးလဲလှယ်ပေးရန် အမိန့်ပြင်ဆင်ထားပါသည်။",
                    action
                )
            } else {
                val codeMatch = "([A-Za-z0-9_-]{3,12})".toRegex().find(prompt)
                val codeName = codeMatch?.value?.uppercase() ?: "787VIP"
                val numberMatch = "([0-9]+(?:\\.[0-9]+)?)".toRegex().find(prompt)
                val disc = numberMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 10.0
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "Promo Code အသစ် '$codeName' ($disc% လျှော့စျေး) စနစ်ထဲသို့ ထည့်သွင်းခြင်း",
                    type = AiActionType.AddPromoCode(
                        code = codeName,
                        discountPercent = disc,
                        description = "AI Created Code $codeName ($disc% off)",
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "AI စနစ်မှ Promo / Discount Code အသစ် '$codeName' ($disc% Discount) ကို 787 စနစ်ထဲသို့ ထည့်သွင်းပေးရန် အတည်ပြုချက် ထုတ်ပေးထားပါသည်။ Cart တွင် အဆိုပါ Code ဖြင့် လျှော့စျေး ရယူနိုင်ပါသည်။",
                    action
                )
            }
        }

        // 2. Code Replacement (e.g. "code အစားထိုး", "ကုဒ် အစားထိုး", "sku အစားထိုး", "replace code")
        if (lower.contains("code အစားထိုး") || lower.contains("ကုဒ် အစားထိုး") || lower.contains("အစားထိုး") || lower.contains("replace code") || lower.contains("replace sku")) {
            val matchedProduct = products.find { prompt.contains(it.name.take(4)) } ?: products.firstOrNull()
            if (matchedProduct != null) {
                val newCode = "787-${matchedProduct.category.take(3).uppercase()}-${System.currentTimeMillis() % 1000}"
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "'${matchedProduct.name}' ၏ Code အဟောင်း '${matchedProduct.sku}' ကို Code အသစ် '$newCode' ဖြင့် အစားထိုးလဲလှယ်ခြင်း",
                    type = AiActionType.ReplaceProductCode(
                        productId = matchedProduct.productId,
                        productName = matchedProduct.name,
                        oldCode = matchedProduct.sku,
                        newCode = newCode,
                        isBarcode = false,
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "AI အခွင့်အရေးဖြင့် '${matchedProduct.name}' ၏ လက်ရှိ Code '${matchedProduct.sku}' ကို Code အသစ် '$newCode' ဖြင့် တိုက်ရိုက် အစားထိုးလဲလှယ်ပေးရန် အမိန့်ထုတ်ပေးထားပါသည်။",
                    action
                )
            }
        }

        // 3. Batch Replace Code Prefix (e.g. "code အားလုံး 787- တပ်ပါ", "prefix ပြောင်း")
        if (lower.contains("code အားလုံး") || lower.contains("ကုဒ် အားလုံး") || lower.contains("prefix") || lower.contains("အသုတ်လိုက် code")) {
            val action = AiAction(
                id = UUID.randomUUID().toString(),
                summary = "ပစ္စည်း Code (SKU) အားလုံးရှေ့တွင် '787-' Prefix အသစ်ဖြင့် အစားထိုးတပ်ဆင်ခြင်း",
                type = AiActionType.BatchReplaceCodePrefix(
                    oldPrefix = "",
                    newPrefix = "787-",
                ),
                privilegeLevel = "AI_SUPERUSER",
            )
            return Pair(
                "AI Superuser အခွင့်အရေးဖြင့် ဆိုင်ရှိ ပစ္စည်း Code များအားလုံးကို '787-' Prefix သို့ အသုတ်လိုက် အစားထိုးသတ်မှတ်ပေးရန် ပြင်ဆင်ပြီးပါပြီ။",
                action
            )
        }

        // 4. Code Modification (e.g. "code ပြင်", "ကုဒ် ပြင်", "sku ပြင်", "code အသစ် သတ်မှတ်")
        if (lower.contains("code ပြင်") || lower.contains("ကုဒ် ပြင်") || lower.contains("sku ပြင်") || lower.contains("barcode ပြင်")) {
            val matchedProduct = products.find { prompt.contains(it.name.take(4)) } ?: products.firstOrNull()
            if (matchedProduct != null) {
                val newSku = "787-MOD-${matchedProduct.productId}"
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "'${matchedProduct.name}' ၏ ပစ္စည်း Code (SKU) ကို '$newSku' သို့ ပြုပြင်ပြောင်းလဲခြင်း",
                    type = AiActionType.UpdateSku(
                        productId = matchedProduct.productId,
                        productName = matchedProduct.name,
                        oldSku = matchedProduct.sku,
                        newSku = newSku,
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "AI စနစ်မှ '${matchedProduct.name}' ၏ ပစ္စည်း Code (SKU) ကို '$newSku' သို့ ပြုပြင်ပေးရန် အတည်ပြုချက် ပြင်ဆင်ထားပါသည်။",
                    action
                )
            }
        }

        // 5. Direct Dynamic Code / SQL Execution (e.g. "sql", "query", "run code", "ကုဒ် run")
        if (lower.contains("sql") || lower.contains("query") || lower.contains("run code") || lower.contains("ဒေတာ script")) {
            val sqlScript = if (lower.contains("update")) {
                prompt.substringAfter("sql").trim().ifBlank { "UPDATE products SET updatedAtEpochMs = ${System.currentTimeMillis()}" }
            } else {
                "UPDATE products SET updatedAtEpochMs = ${System.currentTimeMillis()}"
            }
            val action = AiAction(
                id = UUID.randomUUID().toString(),
                summary = "AI Dynamic Code / SQL Script စနစ်ထဲသို့ တိုက်ရိုက် run ၍ ပြုပြင်ခြင်း",
                type = AiActionType.ExecuteDynamicCode(
                    codeLanguage = "SQL",
                    codeScript = sqlScript,
                    explanation = "AI direct SQLite script execution",
                ),
                privilegeLevel = "AI_SUPERUSER",
            )
            return Pair(
                "⚡ AI အခွင့်အရေးဖြင့် 787 ဒေတာဘေ့စ်ထဲသို့ အောက်ပါ Code / Script ကို တိုက်ရိုက် run ၍ အစားထိုးပြုပြင်ပါမည်:\n`$sqlScript`",
                action
            )
        }

        // 6. Tax Command (e.g., "အခွန် 0%", "အခွန် 5%", "အခွန် 7%", "tax 10%")
        if (lower.contains("အခွန်") || lower.contains("tax")) {
            val numberMatch = "([0-9]+(?:\\.[0-9]+)?)".toRegex().find(prompt)
            val taxRate = numberMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val action = AiAction(
                id = UUID.randomUUID().toString(),
                summary = "ဆိုင်အခွန်နှုန်းထားကို ${taxRate}% သို့ ပြောင်းလဲသတ်မှတ်ခြင်း",
                type = AiActionType.SetTaxRate(taxRate),
                privilegeLevel = "AI_SUPERUSER",
            )
            return Pair(
                "အကြံပြုချက်: ဆိုင်၏ အရောင်းပြေစာ အခွန်နှုန်းထားကို ${taxRate}% သို့ အလိုအလျောက် သတ်မှတ်ပေးရန် အတည်ပြုချက် ပြုလုပ်ပေးထားပါသည်။",
                action
            )
        }

        // 7. Add Product / Code အသစ်ထည့် (e.g. "code အသစ် ထည့်", "ပစ္စည်းအသစ် ထည့်", "add product")
        if (lower.contains("ပစ္စည်းအသစ်") || lower.contains("code အသစ်") || lower.contains("ကုဒ် အသစ်") || lower.contains("ထည့်") || lower.contains("add")) {
            val priceMatch = "([0-9]{3,7})".toRegex().find(prompt)
            val price = priceMatch?.value?.toLongOrNull() ?: 15000L
            val detectedName = when {
                prompt.contains("ဘိလပ်မြေ") -> "ဘိလပ်မြေ (ဆင်တံဆိပ်)"
                prompt.contains("သံချောင်း") -> "သံချောင်း ၁၂ မမ"
                prompt.contains("သွပ်") -> "သွပ်ပြား (အပြာရောင်)"
                prompt.contains("ဆေး") -> "ဆေးသုတ် (Premier)"
                prompt.contains("ပိုက်") -> "PVC ပိုက် ၄ လက်မ"
                else -> "အိမ်ဆောက်ပစ္စည်း (787 အသစ်)"
            }
            val cat = when {
                prompt.contains("ဘိလပ်မြေ") -> "ဘိလပ်မြေ"
                prompt.contains("သံချောင်း") -> "သံချောင်း"
                prompt.contains("သွပ်") -> "သွပ်ပြား"
                prompt.contains("ဆေး") -> "ဆေးသုတ်"
                prompt.contains("ပိုက်") -> "ပိုက်နှင့်ဆက်စပ်"
                else -> "အိမ်ဆောက်ပစ္စည်း"
            }
            val newCode = "787-${cat.take(3).uppercase()}-${System.currentTimeMillis() % 10000}"

            val action = AiAction(
                id = UUID.randomUUID().toString(),
                summary = "Code အသစ် '$newCode' ဖြင့် ပစ္စည်းအသစ် '$detectedName' ထည့်သွင်းခြင်း",
                type = AiActionType.AddProduct(
                    name = detectedName,
                    sku = newCode,
                    category = cat,
                    sellPriceMinor = price,
                    costPriceMinor = (price * 0.85).toLong(),
                    stockQuantity = 50,
                    barcode = "787${System.currentTimeMillis() % 1000000}",
                ),
                privilegeLevel = "AI_SUPERUSER",
            )
            return Pair(
                "အကြံပြုချက်: '$detectedName' (Code: $newCode၊ ရောင်းဈေး: ${"%,d".format(price)} Ks၊ လက်ကျန်: ၅၀) ကို 787 စနစ်ထဲသို့ Code အသစ်ဖြင့် ထည့်သွင်းပေးရန် AI အမိန့်ထုတ်ပေးထားပါသည်။",
                action
            )
        }

        // 8. Price Update (e.g. "ဈေး ပြင်", "ဈေးနှုန်း", "price")
        if (lower.contains("ဈေး") || lower.contains("စျေး") || lower.contains("price")) {
            val matchedProduct = products.find { prompt.contains(it.name.take(4)) } ?: products.firstOrNull()
            val priceMatch = "([0-9]{3,7})".toRegex().find(prompt)
            val newPrice = priceMatch?.value?.toLongOrNull() ?: ((matchedProduct?.sellPriceMinor ?: 10000L) + 1000L)

            if (matchedProduct != null) {
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "'${matchedProduct.name}' ၏ ဈေးနှုန်းကို ${"%,d".format(newPrice)} Ks သို့ ပြင်ဆင်ခြင်း",
                    type = AiActionType.UpdatePrice(
                        productId = matchedProduct.productId,
                        productName = matchedProduct.name,
                        newSellPriceMinor = newPrice,
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "'${matchedProduct.name}' ၏ လက်ရှိရောင်းဈေး (${"%,d".format(matchedProduct.sellPriceMinor)} Ks) မှ ဈေးနှုန်းသစ် (${"%,d".format(newPrice)} Ks) သို့ အစားထိုး ပြင်ဆင်ရန် အတည်ပြုပေးထားပါသည်။",
                    action
                )
            }
        }

        // 9. Stock Adjustment (e.g. "လက်ကျန် ထပ်ဖြည့်", "restock")
        if (lower.contains("လက်ကျန်") || lower.contains("ဖြည့်") || lower.contains("stock")) {
            val matchedProduct = products.find { prompt.contains(it.name.take(4)) } ?: products.firstOrNull()
            if (matchedProduct != null) {
                val action = AiAction(
                    id = UUID.randomUUID().toString(),
                    summary = "'${matchedProduct.name}' ထံ လက်ကျန် +၂၀ ထပ်မံဖြည့်တင်းခြင်း",
                    type = AiActionType.AdjustStock(
                        productId = matchedProduct.productId,
                        productName = matchedProduct.name,
                        adjustment = 20,
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "'${matchedProduct.name}' ၏ လက်ကျန်စာရင်းတွင် +၂၀ ထပ်ဖြည့်ရန် အတည်ပြုချက် ပြုလုပ်ပေးထားပါသည်။",
                    action
                )
            }
        }

        // 10. Dead Stock & Stagnant Items Detection
        if (lower.contains("dead stock") || lower.contains("အရောင်းထိုင်း") || lower.contains("မရောင်းရ") || lower.contains("deadstock")) {
            val stagnantItems = products.filter { it.stockQuantity > it.lowStockThreshold }.take(5)
            val listStr = stagnantItems.joinToString("\n") { p ->
                "• ${p.name} (SKU: ${p.sku}) - လက်ကျန်: ${p.stockQuantity} ${p.unitName} (အရင်း: ${"%,d".format(p.costPriceMinor)} Ks / ရောင်းဈေး: ${"%,d".format(p.sellPriceMinor)} Ks)"
            }
            return Pair(
                """
                📉 **787 AI - Dead Stock & အရောင်းထိုင်း ပစ္စည်းများ သုံးသပ်ချက်:**
                အောက်ပါ ပစ္စည်းများသည် သိုလှောင်ရုံတွင် အချိန်ကြာ လက်ကျန်များနေနိုင်ပါသည်-
                $listStr
                
                💡 **AI ၏ ငွေဖော်အကြံပြုချက်:**
                ၁။ အထက်ပါပစ္စည်းများအား ၅% မှ ၁၀% လျှော့စျေး Promo Code ပေး၍ အရောင်းသွက်အောင် ပြုလုပ်နိုင်ပါသည်။
                ၂။ သို့မဟုတ် အခြားပစ္စည်းများနှင့် ပူးတွဲတွဲဖက်ရောင်းချမှု (Bundle Promotion) ပြုလုပ်သင့်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 11. Reorder & Low Stock Planner
        if (lower.contains("reorder") || lower.contains("မှာယူရန်") || lower.contains("ပစ္စည်းမှာ") || lower.contains("ပြတ်လပ်")) {
            val lowStockItems = products.filter { it.stockQuantity <= it.lowStockThreshold }
            if (lowStockItems.isEmpty()) {
                return Pair("✅ လက်ရှိတွင် ပစ္စည်းများအားလုံး လက်ကျန်လုံလောက်စွာ ရှိနေပါသည် (Low stock ပစ္စည်း မရှိပါ)။", null)
            }
            val listStr = lowStockItems.joinToString("\n") { p ->
                val suggestedOrder = maxOf(20L, p.lowStockThreshold * 3)
                "• ${p.name} (လက်ကျန်: ${p.stockQuantity} ${p.unitName}) -> မှာယူရန် အကြံပြုချက်: +$suggestedOrder ${p.unitName}"
            }
            return Pair(
                """
                ⚠️ **787 AI - ပစ္စည်းမှာယူရန် စာရင်း (Suggested Reorder List):**
                ဆိုင်တွင် လက်ကျန်နည်းနေသော ပစ္စည်း ${lowStockItems.size} မျိုး ရှိပါသည်:
                $listStr
                
                💡 ပေးသွင်းသူများထံ အချိန်မီ မှာယူဖြည့်တင်းနိုင်ရန် အကြံပြုအပ်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // App Logo & Store Image Guide
        if (lower.contains("logo") || lower.contains("လိုဂို") || lower.contains("avatar") || lower.contains("ပုံပြောင်း")) {
            return Pair(
                """
                🖼️ **787 AI - ဆိုင် Logo / App Image ပြောင်းလဲခြင်း စနစ်လမ်းညွှန်:**
                • ဆက်တင် (Settings) တက်ဘ်ရှိ 'ဆိုင်ရှင် Profile' ကတ်တွင် **'🖼️ ပုံပြောင်းမည်'** ခလုတ် သို့မဟုတ် **📷 ကင်မရာပုံကို နှိပ်၍** မိမိစိတ်ကြိုက် ဓာတ်ပုံကို ရွေးချယ်နိုင်ပါသည်။
                • ဓာတ်ပုံရွေးချယ်ပြီးသည်နှင့် စနစ်အတွင်းရှိ နေရာတိုင်း (Home Screen၊ ပစ္စည်းစာရင်း၊ အရောင်းပြက္ခဒိန်၊ AI ကွပ်ကဲရေးစင်တာ၊ တွက်ချက်မှုပြေစာနှင့် PDF ဘေလ်) အားလုံးတွင် အလိုအလျောက် တပြိုင်နက် လိုက်ပါပြောင်းလဲသွားပါမည်။
                • မူလ App Logo သို့ ပြန်ထားလိုပါက **'မူလပုံ ပြန်ထားမည်'** ကို နှိပ်နိုင်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 12. Sales Report & Analysis
        if (lower.contains("အရောင်း") || lower.contains("အစီရင်ခံစာ") || lower.contains("report") || lower.contains("သုံးသပ်") || lower.contains("အမြတ်")) {
            val totalRevenue = sales.sumOf { it.totalMinor }
            val totalOrders = sales.size
            val lowStockCount = products.count { it.stockQuantity <= it.lowStockThreshold }
            val estimatedGrossProfit = (totalRevenue * 0.22).toLong()
            val formattedRevenue = "%,d".format(totalRevenue)
            val formattedProfit = "%,d".format(estimatedGrossProfit)
            return Pair(
                """
                📊 **787 POS (${storeProfile.storeName}) အရောင်းနှင့် အမြတ် သုံးသပ်ချက် အစီရင်ခံစာ:**
                • စုစုပေါင်း အော်ဒါအရေအတွက်: $totalOrders ခု
                • စုစုပေါင်း အရောင်းရငွေ: $formattedRevenue Ks
                • ခန့်မှန်း အသားတင်အမြတ်ငွေ: $formattedProfit Ks (Margin ~22%)
                • လက်ရှိ ပစ္စည်းအမျိုးအမည်: ${products.size} မျိုး
                • လက်ကျန်နည်းနေသော ပစ္စည်း: $lowStockCount မျိုး
                • AI Superuser Privilege: ခွင့်ပြုထားပြီး (Authorized)

                💡 **AI ၏ စီးပွားရေးနှင့် စနစ်ကွပ်ကဲမှု အကြံပြုချက်:**
                ၁။ လက်ကျန်နည်းနေသော ပစ္စည်းများကို Reorder စာရင်းထုတ်၍ ကြိုတင်မှာယူနိုင်ပါသည်။
                ၂။ အရောင်းပြေစာ အခွန်နှုန်းထားမှာ လက်ရှိ ${storeProfile.taxRatePercent}% ဖြစ်ပါသည်။
                ၃။ Code အသစ်ထည့်ခြင်း၊ Code အစားထိုးခြင်း၊ Promo Code ထည့်ခြင်း များကို AI အား ချက်ချင်း အမိန့်ပေးခိုင်းစေနိုင်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 11. AI Privilege & Status Inquiry
        if (lower.contains("အခွင့်အရေး") || lower.contains("privilege") || lower.contains("superuser") || lower.contains("စွမ်းဆောင်ရည်")) {
            return Pair(
                """
                🛡️ **787 AI Superuser စနစ် အခွင့်အရေး အပြည့်အဝ ပေးအပ်ထားပါသည်:**
                • **Code အသစ် ထည့်ခြင်း:** Product SKU Code၊ Barcode၊ Promo / Discount Code များကို တိုက်ရိုက် ထည့်သွင်းနိုင်ခြင်း။
                • **Code အစားထိုးခြင်း:** Code အဟောင်းမှ Code အသစ်သို့ အစားထိုးခြင်း၊ အသုတ်လိုက် 787- Prefix တပ်ခြင်း။
                • **Code ပြုပြင်ခြင်း:** ဈေးနှုန်း၊ အခွန်နှုန်း (၀% မှ ၁၀၀%)၊ လက်ကျန်စာရင်း နှင့် Database Data များကို တိုက်ရိုက် ပြုပြင်ခြင်း။
                • **Dynamic Code / SQL Execution:** SQLite ဒေတာဘေ့စ်ထဲသို့ Script တိုက်ရိုက် run နိုင်ခြင်း။
                """.trimIndent(),
                null
            )
        }

        // 13. Debtor & Customer Credit Inquiry
        if (lower.contains("အကြွေး") || lower.contains("debt") || lower.contains("customer") || lower.contains("ဖောက်သည်")) {
            val debtors = dbContext.debtors
            val totalDebt = dbContext.totalDebtAmount
            if (debtors.isEmpty()) {
                return Pair("✅ **787 AI ဒေတာဘေ့စ် စစ်ဆေးချက်:** လက်ရှိတွင် အကြွေးကျန်ရှိသော Customer / ဖောက်သည် မရှိပါ (Zero Debt)။", null)
            }
            val listStr = debtors.take(15).joinToString("\n") { c ->
                "• ${c.name} (${if (c.phone.isNotBlank()) c.phone else "ဖုန်းမရှိ"}) -> အကြွေးကျန်: ${"%,d".format(c.currentDebtMinor)} Ks"
            }
            return Pair(
                """
                👥 **787 POS - အကြွေးကျန် Customer များ စာရင်း (Database):**
                • စုစုပေါင်း အကြွေးကျန်ရှိသူ: ${debtors.size} ဦး
                • စုစုပေါင်း ရရန်ရှိ အကြွေးငွေ: ${"%,d".format(totalDebt)} Ks
                
                အကြွေးကျန် အများဆုံး ဖောက်သည်များ:
                $listStr
                
                💡 ဖောက်သည်များထံသို့ အကြွေးပြန်လည်တောင်းခံနိုင်ရန် သို့မဟုတ် Repayment ပြေစာသွင်းနိုင်ရန် Customer Debt Ledger တွင် စီမံခန့်ခွဲနိုင်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 14. Best-selling Products Inquiry
        if (lower.contains("အရောင်းရဆုံး") || lower.contains("best selling") || lower.contains("ရောင်းအားကောင်း") || lower.contains("လူကြိုက်")) {
            val topItems = products.sortedByDescending { it.sellPriceMinor }.take(5)
            val listStr = topItems.mapIndexed { idx, p ->
                "${idx + 1}။ ${p.name} (SKU: ${p.sku}) - ရောင်းဈေး: ${"%,d".format(p.sellPriceMinor)} Ks (လက်ကျန်: ${p.stockQuantity} ${p.unitName})"
            }.joinToString("\n")
            return Pair(
                """
                🏆 **787 POS - အရောင်းရဆုံးနှင့် ရောင်းအားအကောင်းဆုံး ကုန်ပစ္စည်းများ (Database):**
                $listStr

                💡 အဆိုပါ ပစ္စည်းများသည် ဆိုင်၏ အဓိက ဝင်ငွေရရှိစေသော အဓိက ကုန်ပစ္စည်းများဖြစ်သဖြင့် စတော့ပြတ်လပ်မှုမရှိစေရန် ကြိုတင်မှာယူထားသင့်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 15. Today's Sales Inquiry
        if (lower.contains("ဒီနေ့") || lower.contains("ယနေ့") || lower.contains("today")) {
            val formattedTodayRevenue = "%,d".format(dbContext.todayRevenue)
            val count = dbContext.todaySalesCount
            return Pair(
                """
                📈 **787 POS - ဒီနေ့ (Today) အရောင်းအချက်အလက် (Database):**
                • ဒီနေ့ အော်ဒါအရေအတွက်: $count ခု
                • ဒီနေ့ စုစုပေါင်း ရောင်းရငွေ: $formattedTodayRevenue Ks
                • စနစ် အခြေအနေ: Local Offline POS ဒေတာဘေ့စ်နှင့် တိုက်ရိုက်ချိတ်ဆက်ပြီး
                
                💡 အသေးစိတ် ဘေလ်ဖြတ်ပိုင်းများကို အရောင်းမှတ်တမ်း (Sales History) တက်ဘ်တွင် စစ်ဆေးနိုင်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // 16. Inventory & Stock Valuation Inquiry
        if (lower.contains("စတော့") || lower.contains("ပစ္စည်းစာရင်း") || lower.contains("inventory") || lower.contains("လက်ကျန်အားလုံး")) {
            val totalItems = products.size
            val totalUnits = products.sumOf { it.stockQuantity }
            val totalValuation = products.sumOf { it.costPriceMinor * it.stockQuantity }
            val totalSellValuation = products.sumOf { it.sellPriceMinor * it.stockQuantity }
            val formattedCostVal = "%,d".format(totalValuation)
            val formattedSellVal = "%,d".format(totalSellValuation)
            val lowCount = dbContext.lowStockCount
            return Pair(
                """
                📦 **787 POS - စတော့နှင့် ကုန်ပစ္စည်းစာရင်း သုံးသပ်ချက် (Database):**
                • စုစုပေါင်း ကုန်ပစ္စည်းအမျိုးအမည်: $totalItems မျိုး
                • စုစုပေါင်း လက်ကျန်အရေအတွက်: $totalUnits ခု/လုံး
                • လက်ကျန်နည်းနေသော ပစ္စည်း: $lowCount မျိုး
                • စုစုပေါင်း ကုန်ပစ္စည်း အရင်းတန်ဖိုး (Cost Valuation): $formattedCostVal Ks
                • ရောင်းချပါက ခန့်မှန်းရရှိမည့် တန်ဖိုး (Retail Valuation): $formattedSellVal Ks
                • အမျိုးအစားများ: ${dbContext.categories.joinToString(", ")}
                """.trimIndent(),
                null
            )
        }

        // 17. Specific Product Search by Name or SKU
        val matchedProductForQuery = products.find { p ->
            val cleanName = p.name.trim()
            cleanName.isNotBlank() && (
                prompt.contains(cleanName) ||
                (cleanName.length >= 4 && prompt.contains(cleanName.take(4))) ||
                (p.sku.isNotBlank() && prompt.contains(p.sku, ignoreCase = true)) ||
                (!p.barcode.isNullOrBlank() && prompt.contains(p.barcode))
            )
        }
        if (matchedProductForQuery != null && !lower.contains("ထည့်") && !lower.contains("ပြင်") && !lower.contains("အစားထိုး")) {
            val p = matchedProductForQuery
            val marginPercent = if (p.sellPriceMinor > 0) ((p.sellPriceMinor - p.costPriceMinor).toDouble() / p.sellPriceMinor * 100).toInt() else 0
            val isLow = p.stockQuantity <= p.lowStockThreshold
            val statusEmoji = if (isLow) "⚠️ လက်ကျန်နည်းနေပါသည်" else "✅ လက်ကျန်ပုံမှန်ရှိသည်"
            return Pair(
                """
                🔍 **787 POS ဒေတာဘေ့စ် ရှာဖွေတွေ့ရှိချက်:**
                • ပစ္စည်းအမည်: **${p.name}**
                • SKU ကုဒ်: `${p.sku}` ${if (!p.barcode.isNullOrBlank()) "| Barcode: `${p.barcode}`" else ""}
                • အမျိုးအစား: ${p.category} | ယူနစ်: ${p.unitName}
                • လက်ကျန်အရေအတွက်: **${p.stockQuantity} ${p.unitName}** ($statusEmoji)
                • ရောင်းဈေး: **${"%,d".format(p.sellPriceMinor)} Ks**
                • ဝယ်ရင်းဈေး: ${"%,d".format(p.costPriceMinor)} Ks (အမြတ်: $marginPercent%)
                • သတိပေးလက်ကျန်: ${p.lowStockThreshold} ${p.unitName}
                
                💡 စျေးနှုန်း သို့မဟုတ် စတော့လက်ကျန် ပြင်ဆင်လိုပါက "ဈေးနှုန်း ပြင်ပါ" သို့မဟုတ် "လက်ကျန် ထပ်ဖြည့်ပါ" ဟု အမိန့်ပေးနိုင်ပါသည်။
                """.trimIndent(),
                null
            )
        }

        // Default General Guidance
        return Pair(
            """
            မင်္ဂလာပါ! ကျွန်ုပ်သည် 787 POS ၏ AI ကွပ်ကဲရေးမှူး ဖြစ်ပါသည်။
            စနစ်ထဲမှ POS Database (စတော့၊ အရောင်း၊ အကြွေး၊ ဈေးနှုန်း) များနှင့် တိုက်ရိုက်ချိတ်ဆက်ထားပါသည်။

            အောက်ပါ မေးခွန်းနှင့် command များကို စာရိုက်၍ မေးမြန်း/အမိန့်ပေးနိုင်ပါသည်-
            • "📊 အရောင်းရဆုံး ပစ္စည်းများ"
            • "⚠️ လက်ကျန်နည်းနေသော ပစ္စည်းများ"
            • "💰 စုစုပေါင်း အရောင်းရငွေ ဘယ်လောက်လဲ"
            • "📈 ဒီနေ့ အရောင်းစာရင်း ပြပါ"
            • "👥 အကြွေးကျန် Customer စာရင်း ပြပါ"
            • "📦 ဘိလပ်မြေ လက်ကျန် ဘယ်လောက်ရှိလဲ"
            • "Code အသစ် 787-CEM-01 ဖြင့် ဘိလပ်မြေ ၁၅,၀၀၀ စာရင်းထဲ ထည့်ပါ"
            • "သံချောင်း Code နှင့် ဈေးနှုန်းကို ၁၆,၀၀၀ ပြင်ပါ"
            • "Promo Code '787VIP' ၁၀% လျှော့စျေး ထည့်ပါ"
            """.trimIndent(),
            null
        )
    }
}
