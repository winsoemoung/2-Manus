package com.example.offlinepos.Sync

import com.example.offlinepos.data.local.ErrorClass
import com.example.offlinepos.data.local.SyncState

sealed interface SyncDecision {
    data object Acked : SyncDecision
    data class Retry(val errorClass: String, val reason: String) : SyncDecision
    data class Permanent(val errorClass: String, val reason: String) : SyncDecision
    data class Conflict(val reason: String) : SyncDecision
    data class Quarantine(val reason: String) : SyncDecision
}

object SyncClassifier {
    fun classify(httpCode: Int, bodyLooksValid: Boolean): SyncDecision = when {
        !bodyLooksValid -> SyncDecision.Quarantine("Payload/schema mismatch")
        httpCode in 200..299 -> SyncDecision.Acked
        httpCode == 401 || httpCode == 403 -> SyncDecision.Permanent(ErrorClass.AUTH, "Re-authenticate")
        httpCode == 409 -> SyncDecision.Conflict("Server version mismatch")
        httpCode == 429 -> SyncDecision.Retry(ErrorClass.RATE_LIMIT, "Throttled")
        httpCode in 500..599 -> SyncDecision.Retry(ErrorClass.SERVER, "Server error")
        httpCode == 408 -> SyncDecision.Retry(ErrorClass.NETWORK, "Timeout")
        httpCode in 400..499 -> SyncDecision.Permanent(ErrorClass.VALIDATION, "Rejected by server")
        else -> SyncDecision.Permanent(ErrorClass.VALIDATION, "Unclassified status $httpCode")
    }
}

fun stateFor(decision: SyncDecision): String = when (decision) {
    is SyncDecision.Acked -> SyncState.ACKED
    is SyncDecision.Retry -> SyncState.RETRYABLE_FAILURE
    is SyncDecision.Permanent -> SyncState.PERMANENT_FAILURE
    is SyncDecision.Conflict -> SyncState.CONFLICT
    is SyncDecision.Quarantine -> SyncState.QUARANTINED
}

fun nextAttemptDelayMs(retryCount: Int): Long {
    val capped = minOf(retryCount, 6)
    return 30_000L * (1L shl capped)
}
