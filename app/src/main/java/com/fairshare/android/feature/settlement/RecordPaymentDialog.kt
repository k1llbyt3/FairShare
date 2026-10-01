package com.fairshare.android.feature.settlement

import androidx.compose.runtime.Composable
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment

@Composable
fun RecordPaymentDialog(
    members: List<Member>,
    groupId: String = "",
    initialFromMemberId: String? = null,
    initialToMemberId: String? = null,
    suggestedAmount: Money? = null,
    onDismiss: () -> Unit,
    onSavePayment: suspend (SettlementPayment) -> Unit
) {
    com.fairshare.android.feature.expenses.RecordPaymentDialog(
        members = members,
        groupId = groupId,
        initialFromMemberId = initialFromMemberId,
        initialToMemberId = initialToMemberId,
        suggestedAmount = suggestedAmount,
        onDismiss = onDismiss,
        onSavePayment = onSavePayment
    )
}
