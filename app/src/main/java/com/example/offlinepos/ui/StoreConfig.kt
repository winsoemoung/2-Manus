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
        val rawStoreName = prefs.getString("store_name", "san thit") ?: "san thit"
        val effectiveStoreName = if (rawStoreName.contains("ဆန်းသစ်") || rawStoreName.contains("ဆနးသစ်")) "san thit" else rawStoreName
        return StoreProfile(
            storeName = effectiveStoreName,
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

    // Owner PIN (Default: 7870)
    fun getOwnerPin(): String = prefs.getString("store_owner_pin", "7870") ?: "7870"
    fun setOwnerPin(newPin: String) {
        prefs.edit().putString("store_owner_pin", newPin).apply()
    }

    // Manager PIN (Default: 1234)
    fun getManagerPin(): String = prefs.getString("store_manager_pin", "1234") ?: "1234"
    fun setManagerPin(newPin: String) {
        prefs.edit().putString("store_manager_pin", newPin).apply()
    }

    // Google Drive Backup Account
    fun getGoogleAccount(): String? = prefs.getString("google_backup_account", null)
    fun setGoogleAccount(email: String) {
        prefs.edit().putString("google_backup_account", email).apply()
    }
    fun clearGoogleAccount() {
        prefs.edit().remove("google_backup_account").apply()
    }

    fun getLastGoogleBackupTime(): Long = prefs.getLong("last_google_backup_time", 0L)
    fun setLastGoogleBackupTime(timeMs: Long) {
        prefs.edit().putLong("last_google_backup_time", timeMs).apply()
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

    // App Mode: Server POS (False) vs Staff Client Mode (True)
    fun isStaffClientMode(): Boolean = prefs.getBoolean("is_staff_client_mode", false)
    fun setStaffClientMode(isClient: Boolean) {
        prefs.edit().putBoolean("is_staff_client_mode", isClient).apply()
    }

    fun getServerPort(): Int = prefs.getInt("local_server_port", 8787)
    fun setServerPort(port: Int) {
        prefs.edit().putInt("local_server_port", port).apply()
    }

    fun getLastConnectedServerUrl(): String = prefs.getString("last_connected_server_url", "http://192.168.1.10:8787") ?: "http://192.168.1.10:8787"
    fun setLastConnectedServerUrl(url: String) {
        prefs.edit().putString("last_connected_server_url", url).apply()
    }

    fun getStaffName(): String = prefs.getString("staff_client_name", "ဆိုင်ဝန်ထမ်း #1") ?: "ဆိုင်ဝန်ထမ်း #1"
    fun setStaffName(name: String) {
        prefs.edit().putString("staff_client_name", name).apply()
    }
}
