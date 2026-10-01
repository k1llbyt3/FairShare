package com.fairshare.android.core.domain.transactions

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.PaymentMode

enum class CandidateSource {
    NOTIFICATION,
    SMS_PASTED,
    IMPORT,
    STATEMENT
}

enum class CandidateStatus {
    PENDING,
    CONFIRMED,
    DISCARDED
}

data class TransactionCandidate(
    val id: String,
    val groupId: String? = null,
    val rawSource: CandidateSource,
    val rawText: String,
    val amount: Money,
    val detectedMerchant: String?,
    val detectedTimestamp: Long,
    val inferredCategory: ExpenseCategory = ExpenseCategory.OTHER,
    val inferredMode: PaymentMode = PaymentMode.ONLINE,
    val status: CandidateStatus = CandidateStatus.PENDING,
    val isDuplicate: Boolean = false,
    val duplicateReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
