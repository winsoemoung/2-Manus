package com.example.offlinepos.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class StoreProfile(
    val storeName: String,
    val address: String,
    val phone: String,
    val headerNote: String,
    val footerMessage: String,
    val autoBackupSchedule: String,
    val taxRatePercent: Double = 0.0,
    val customLogoPath: String? = null,
)

data class PromoCode(
    val code: String,
    val discountPercent: Double = 0.0,
    val discountFlatMinor: Long = 0L,
    val description: String = "",
    val isActive: Boolean = true,
)

class StoreConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("santhit_store_prefs", Context.MODE_PRIVATE)

    fun getProfile(): StoreProfile {
        return StoreProfile(
            storeName = prefs.getString("store_name", "ဆန်းသစ် အိမ်ဆောက်ပစ္စည်းဆိုင်") ?: "ဆန်းသစ် အိမ်ဆောက်ပစ္စည်းဆိုင်",
            address = prefs.getString("store_address", "အမှတ် (၁၂)၊ ဗိုလ်ချုပ်လမ်း၊ မြို့မရပ်ကွက်") ?: "အမှတ် (၁၂)၊ ဗိုလ်ချုပ်လမ်း၊ မြို့မရပ်ကွက်",
            phone = prefs.getString("store_phone", "၀၉-၄၅၀၀၁၂၃၄၅ ၊ ၀၉-၇၉၀၀၆၇၈၉၀") ?: "၀၉-၄၅၀၀၁၂၃၄၅ ၊ ၀၉-၇၉၀၀၆၇၈၉၀",
            headerNote = prefs.getString("store_header", "အိမ်ဆောက်ပစ္စည်း အမျိုးမျိုး လက်လီ/လက်ကား") ?: "အိမ်ဆောက်ပစ္စည်း အမျိုးမျိုး လက်လီ/လက်ကား",
            footerMessage = prefs.getString("store_footer", "ဝယ်ယူအားပေးမှုကို အထူးကျေးဇူးတင်ပါသည်\nပစ္စည်းမှန်၊ စျေးနှုန်းမှန်၊ အချိန်မှန်") ?: "ဝယ်ယူအားပေးမှုကို အထူးကျေးဇူးတင်ပါသည်\nပစ္စည်းမှန်၊ စျေးနှုန်းမှန်၊ အချိန်မှန်",
            autoBackupSchedule = prefs.getString("auto_backup_schedule", "DAILY") ?: "DAILY",
            taxRatePercent = prefs.getFloat("store_tax_percent", 0.0f).toDouble(),
            customLogoPath = prefs.getString("store_custom_logo_path", null),
        )
    }

    fun saveProfile(profile: StoreProfile) {
        prefs.edit()
            .putString("store_name", profile.storeName)
            .putString("store_address", profile.address)
            .putString("store_phone", profile.phone)
            .putString("store_header", profile.headerNote)
            .putString("store_footer", profile.footerMessage)
            .putString("auto_backup_schedule", profile.autoBackupSchedule)
            .putFloat("store_tax_percent", profile.taxRatePercent.toFloat())
            .putString("store_custom_logo_path", profile.customLogoPath)
            .apply()
    }

    fun isAiSuperuserGranted(): Boolean {
        // AI Superuser Privileges are enabled by default as requested by user
        return prefs.getBoolean("ai_superuser_granted", true)
    }

    fun setAiSuperuserGranted(granted: Boolean) {
        prefs.edit().putBoolean("ai_superuser_granted", granted).apply()
    }

    fun getPromoCodes(): List<PromoCode> {
        val raw = prefs.getString("promo_codes_json", null)
        if (raw.isNullOrBlank()) {
            return listOf(
                PromoCode(code = "787VIP", discountPercent = 10.0, description = "787 VIP Discount (10%)"),
                PromoCode(code = "PROMO787", discountPercent = 5.0, description = "787 Promo Discount (5%)"),
                PromoCode(code = "WELCOME", discountFlatMinor = 2000L, description = "Welcome Gift (-2,000 Ks)"),
            )
        }
        val list = mutableListOf<PromoCode>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PromoCode(
                        code = obj.getString("code"),
                        discountPercent = obj.optDouble("discountPercent", 0.0),
                        discountFlatMinor = obj.optLong("discountFlatMinor", 0L),
                        description = obj.optString("description", ""),
                        isActive = obj.optBoolean("isActive", true),
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }
        return list
    }

    fun savePromoCode(promo: PromoCode) {
        val current = getPromoCodes().toMutableList()
        val existingIndex = current.indexOfFirst { it.code.equals(promo.code, ignoreCase = true) }
        if (existingIndex >= 0) {
            current[existingIndex] = promo
        } else {
            current.add(promo)
        }
        savePromoCodesList(current)
    }

    fun replacePromoCode(oldCode: String, newPromo: PromoCode): Boolean {
        val current = getPromoCodes().toMutableList()
        val index = current.indexOfFirst { it.code.equals(oldCode, ignoreCase = true) }
        if (index >= 0) {
            current[index] = newPromo
            savePromoCodesList(current)
            return true
        } else {
            current.add(newPromo)
            savePromoCodesList(current)
            return false
        }
    }

    fun deletePromoCode(code: String) {
        val current = getPromoCodes().filter { !it.code.equals(code, ignoreCase = true) }
        savePromoCodesList(current)
    }

    private fun savePromoCodesList(list: List<PromoCode>) {
        val jsonArray = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("code", p.code.uppercase().trim())
                put("discountPercent", p.discountPercent)
                put("discountFlatMinor", p.discountFlatMinor)
                put("description", p.description)
                put("isActive", p.isActive)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("promo_codes_json", jsonArray.toString()).apply()
    }
}
