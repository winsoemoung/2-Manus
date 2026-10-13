package com.example.offlinepos.domain

data class ProductDraft(
    val name: String,
    val barcode: String?,
    val costPriceMinor: Long,
    val sellPriceMinor: Long,
    val stockQuantity: Long,
    val category: String = "အထွေထွေ",
    val isRoofingSheet: Boolean = false,
    val lowStockThreshold: Long = 3,
    val imageUri: String? = null,
) {
    fun validate(): List<String> = buildList {
        if (name.isBlank()) add("ကုန်ပစ္စည်း အမည် ထည့်ရန် လိုအပ်ပါသည်")
        if (sellPriceMinor <= 0) add("ရောင်းစျေးသည် ၀ ထက် ကြီးရပါမည်")
        if (costPriceMinor < 0) add("ဝယ်စျေး မမှန်ကန်ပါ")
        if (stockQuantity < 0) add("လက်ကျန်အရေအတွက် မမှန်ကန်ပါ")
        if (lowStockThreshold < 0) add("အနည်းဆုံး သတိပေး လက်ကျန် မမှန်ကန်ပါ")
        val normalized = barcode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (normalized != null) {
            if (normalized.length !in setOf(8, 12, 13)) {
                add("Barcode သည် ၈၊ ၁၂ သို့မဟုတ် ၁၃ လုံး ဖြစ်ရပါမည်")
            }
            if (!normalized.all { it.isDigit() }) add("Barcode တွင် ဂဏန်းများသာ ပါဝင်ရပါမည်")
        }
    }
}