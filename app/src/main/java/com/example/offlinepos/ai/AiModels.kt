package com.example.offlinepos.ai

enum class MessageSender {
    USER, AI
}

sealed class AiActionType {
    data class AddProduct(
        val name: String,
        val sku: String,
        val category: String,
        val sellPriceMinor: Long,
        val costPriceMinor: Long,
        val stockQuantity: Long,
        val barcode: String? = null,
    ) : AiActionType()

    data class UpdatePrice(
        val productId: Long,
        val productName: String,
        val newSellPriceMinor: Long,
    ) : AiActionType()

    data class UpdateSku(
        val productId: Long,
        val productName: String,
        val oldSku: String,
        val newSku: String,
    ) : AiActionType()

    data class UpdateBarcode(
        val productId: Long,
        val productName: String,
        val oldBarcode: String?,
        val newBarcode: String,
    ) : AiActionType()

    data class ReplaceProductCode(
        val productId: Long,
        val productName: String,
        val oldCode: String,
        val newCode: String,
        val isBarcode: Boolean = false,
    ) : AiActionType()

    data class BatchReplaceCodePrefix(
        val oldPrefix: String,
        val newPrefix: String,
    ) : AiActionType()

    data class AdjustStock(
        val productId: Long,
        val productName: String,
        val adjustment: Long,
    ) : AiActionType()

    data class SetTaxRate(
        val newTaxPercent: Double,
    ) : AiActionType()

    data class UpdateStoreProfile(
        val storeName: String? = null,
        val address: String? = null,
        val phone: String? = null,
    ) : AiActionType()

    data class BatchReplaceCategory(
        val oldCategory: String,
        val newCategory: String,
    ) : AiActionType()

    data class AddPromoCode(
        val code: String,
        val discountPercent: Double = 0.0,
        val discountFlatMinor: Long = 0L,
        val description: String = "",
    ) : AiActionType()

    data class ReplacePromoCode(
        val oldCode: String,
        val newCode: String,
        val discountPercent: Double = 0.0,
        val description: String = "",
    ) : AiActionType()

    data class ExecuteDynamicCode(
        val codeLanguage: String, // "SQL" or "SCRIPT"
        val codeScript: String,
        val explanation: String,
    ) : AiActionType()

    data class ApplyCartDiscountByCode(
        val promoCode: String,
        val discountPercent: Double,
    ) : AiActionType()
}

data class AiAction(
    val id: String,
    val summary: String,
    val type: AiActionType,
    var isExecuted: Boolean = false,
    val privilegeLevel: String = "AI_SUPERUSER",
)

data class AiChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestampMs: Long,
    val action: AiAction? = null,
)
