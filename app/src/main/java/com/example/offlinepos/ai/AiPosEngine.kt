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
        products: List<ProductEntity>,
        sales: List<SaleEntity>,
        storeProfile: StoreProfile,
    ): Pair<String, AiAction?> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        if (apiKey.isNotBlank()) {
            try {
                val result = callGeminiApi(apiKey, userPrompt, products, sales, storeProfile)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                // Fallback to offline rule-based NLP parser
            }
        }

        // Offline Rule-based NLP Engine
        processOfflineNlpCommand(userPrompt, products, sales, storeProfile)
    }

    private fun callGeminiApi(
        apiKey: String,
        userPrompt: String,
        products: List<ProductEntity>,
        sales: List<SaleEntity>,
        storeProfile: StoreProfile,
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

        // Build context summary
        val catalogSummary = products.take(25).joinToString("\n") { p ->
            "- ID:${p.productId}, Name:${p.name}, SKU:${p.sku}, Barcode:${p.barcode ?: "-"}, Sell:${p.sellPriceMinor}Ks, Cost:${p.costPriceMinor}Ks, Stock:${p.stockQuantity}, Cat:${p.category}"
        }
        val promoSummary = storeConfig.getPromoCodes().joinToString(", ") { "${it.code} (${if (it.discountPercent > 0) "${it.discountPercent}%" else "${it.discountFlatMinor}Ks"})" }
        val totalRevenue = sales.sumOf { it.totalMinor }

        val systemPrompt = """
            You are the intelligent Executive AI Chief & Superuser Commander of '787 POS System' (Offline POS: ${storeProfile.storeName}).
            You possess FULL SYSTEM PRIVILEGES (Superuser) to:
            1. ADD NEW CODE: Add new products with SKU codes, barcodes, or add new promo/discount codes.
            2. REPLACE CODE: Replace old SKU/barcode codes with new codes across the store catalog, batch replace code prefixes, or replace promo codes.
            3. MODIFY CODE & DATA: Edit existing SKU/barcodes, modify prices, alter tax rates (0% to 100%), restock quantities, or execute direct SQL updates on the database.

            Store Details:
            - Current Tax Rate: ${storeProfile.taxRatePercent}%.
            - Total Products: ${products.size}.
            - Total Sales Count: ${sales.size}, Total Sales Revenue: $totalRevenue Ks.
            - Active Promo Codes: $promoSummary
            - Sample Product Catalog:
            $catalogSummary

            Always answer courteously in Myanmar (Burmese) language.
            When an actionable command is detected, write a concise explanation in Burmese AND append a valid JSON block enclosed in ```json and ``` at the end of your response:
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
                        put(JSONObject().put("text", "$systemPrompt\n\nUser Command: $userPrompt"))
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
        products: List<ProductEntity>,
        sales: List<SaleEntity>,
        storeProfile: StoreProfile,
    ): Pair<String, AiAction?> {
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
                "အကြံပြုချက်: '$detectedName' (Code: $newCode၊ ရောင်းဈေး: %,d Ks၊ လက်ကျန်: ၅၀) ကို 787 စနစ်ထဲသို့ Code အသစ်ဖြင့် ထည့်သွင်းပေးရန် AI အမိန့်ထုတ်ပေးထားပါသည်။".format(price),
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
                    summary = "'${matchedProduct.name}' ၏ ဈေးနှုန်းကို %,d Ks သို့ ပြင်ဆင်ခြင်း".format(newPrice),
                    type = AiActionType.UpdatePrice(
                        productId = matchedProduct.productId,
                        productName = matchedProduct.name,
                        newSellPriceMinor = newPrice,
                    ),
                    privilegeLevel = "AI_SUPERUSER",
                )
                return Pair(
                    "'${matchedProduct.name}' ၏ လက်ရှိရောင်းဈေး (%,d Ks) မှ ဈေးနှုန်းသစ် (%,d Ks) သို့ အစားထိုး ပြင်ဆင်ရန် အတည်ပြုပေးထားပါသည်။".format(matchedProduct.sellPriceMinor, newPrice),
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
                "• ${p.name} (SKU: ${p.sku}) - လက်ကျန်: ${p.stockQuantity} ${p.unitName} (အရင်း: %,d Ks / ရောင်းဈေး: %,d Ks)".format(p.costPriceMinor, p.sellPriceMinor)
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

        // 12. Sales Report & Analysis
        if (lower.contains("အရောင်း") || lower.contains("အစီရင်ခံစာ") || lower.contains("report") || lower.contains("သုံးသပ်") || lower.contains("အမြတ်")) {
            val totalRevenue = sales.sumOf { it.totalMinor }
            val totalOrders = sales.size
            val lowStockCount = products.count { it.stockQuantity <= it.lowStockThreshold }
            val estimatedGrossProfit = (totalRevenue * 0.22).toLong()
            return Pair(
                """
                📊 **787 POS (${storeProfile.storeName}) အရောင်းနှင့် အမြတ် သုံးသပ်ချက် အစီရင်ခံစာ:**
                • စုစုပေါင်း အော်ဒါအရေအတွက်: $totalOrders ခု
                • စုစုပေါင်း အရောင်းရငွေ: %,d Ks
                • ခန့်မှန်း အသားတင်အမြတ်ငွေ: %,d Ks (Margin ~22%)
                • လက်ရှိ ပစ္စည်းအမျိုးအမည်: ${products.size} မျိုး
                • လက်ကျန်နည်းနေသော ပစ္စည်း: $lowStockCount မျိုး
                • AI Superuser Privilege: ခွင့်ပြုထားပြီး (Authorized)

                💡 **AI ၏ စီးပွားရေးနှင့် စနစ်ကွပ်ကဲမှု အကြံပြုချက်:**
                ၁။ လက်ကျန်နည်းနေသော ပစ္စည်းများကို Reorder စာရင်းထုတ်၍ ကြိုတင်မှာယူနိုင်ပါသည်။
                ၂။ အရောင်းပြေစာ အခွန်နှုန်းထားမှာ လက်ရှိ ${storeProfile.taxRatePercent}% ဖြစ်ပါသည်။
                ၃။ Code အသစ်ထည့်ခြင်း၊ Code အစားထိုးခြင်း၊ Promo Code ထည့်ခြင်း များကို AI အား ချက်ချင်း အမိန့်ပေးခိုင်းစေနိုင်ပါသည်။
                """.trimIndent().format(totalRevenue, estimatedGrossProfit),
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

        // Default General Guidance
        return Pair(
            """
            မင်္ဂလာပါ! ကျွန်ုပ်သည် 787 POS ၏ AI ကွပ်ကဲရေးမှူး ဖြစ်ပါသည်။
            စနစ်ထဲမှ Code အသစ် ထည့်ခြင်း၊ အစားထိုးခြင်း၊ ပြုပြင်ခြင်း အခွင့်အရေးများ အပြည့်အဝ ရရှိထားပါသည်။

            အောက်ပါ command များကို စာရိုက်၍ အမိန့်ပေးခိုင်းစေနိုင်ပါသည်-
            • "Code အသစ် 787-CEM-01 ဖြင့် ဘိလပ်မြေ ၁၅,၀၀၀ စာရင်းထဲ ထည့်ပါ"
            • "ဘိလပ်မြေ Code အဟောင်းကို '787-CEM-VIP' ဖြင့် အစားထိုးပါ"
            • "Promo Code '787VIP' ၁၀% လျှော့စျေး ထည့်ပါ"
            • "သံချောင်း Code နှင့် ဈေးနှုန်းကို ၁၆,၀၀၀ ပြင်ပါ"
            • "အခွန်နှုန်းကို ၀% သို့ ပြင်ဆင်ပါ" (သို့မဟုတ် ၇% သတ်မှတ်ပါ)
            • "ပစ္စည်း Code အားလုံးကို 787- prefix ဖြင့် အစားထိုးပါ"
            • "အရောင်းသုံးသပ်ချက် အစီရင်ခံစာ ထုတ်ပေးပါ"
            """.trimIndent(),
            null
        )
    }
}
