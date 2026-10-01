package com.fairshare.android.core.domain.natural

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod

data class ParsedExpenseDraft(
    val amount: Money,
    val description: String,
    val payerId: String?,
    val payerName: String?,
    val participantIds: List<String>,
    val category: ExpenseCategory,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val mode: PaymentMode = PaymentMode.ONLINE,
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val rawText: String,
    val isPayerConfident: Boolean,
    val isParticipantsConfident: Boolean
)
