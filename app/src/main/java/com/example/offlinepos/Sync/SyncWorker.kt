package com.example.offlinepos.Sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,
    params) {
    override suspend fun doWork(): Result {
        val outbox = loadPending()
        if (outbox.isEmpty()) return Result.success()
        return when (val decision = pushToServer(outbox)) {
            is SyncDecision.Acked -> Result.success()
            is SyncDecision.Retry -> Result.retry()
            is SyncDecision.Permanent -> {
                recordForOperatorReview(decision)
                Result.failure()
            }
            is SyncDecision.Conflict -> Result.retry()
            is SyncDecision.Quarantine -> {
                recordForOperatorReview(decision)
                Result.failure()
            }
        }
    }
    private suspend fun loadPending(): List<Any> = emptyList()
    private suspend fun pushToServer(rows: List<Any>): SyncDecision = SyncDecision.Acked
    private suspend fun recordForOperatorReview(decision: SyncDecision) = Unit
    companion object {
        fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
    }
}