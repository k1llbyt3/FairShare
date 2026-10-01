package com.fairshare.android.feature.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementTransfer

@Composable
fun TransferDetailDialog(
    transfer: SettlementTransfer,
    members: List<Member>,
    expenses: List<Expense>,
    payments: List<SettlementPayment>,
    onDismiss: () -> Unit,
    onRecordPaymentClick: (fromMemberId: String, toMemberId: String, amount: Money) -> Unit,
    onSimulatePaymentClick: (fromMemberId: String, toMemberId: String, amount: Money) -> Unit
) {
    val currency = transfer.amount.currency

    // Calculate canonical group balances for both parties
    val memberBalances = remember(members, expenses, payments) {
        BalanceEngine.calculateGroupBalances(members, expenses, payments, emptyList(), currency)
    }

    val payerBalance = memberBalances.firstOrNull { it.memberId == transfer.fromMemberId }
    val receiverBalance = memberBalances.firstOrNull { it.memberId == transfer.toMemberId }

    // Find relevant expenses that contributed to this debt:
    // 1. Where creditor paid and debtor was a participant (direct fronted spend)
    // 2. Where debtor paid and creditor was a participant (offsetting spend)
    // 3. Any other shared expenses involving both
    val creditorFrontedExpenses = remember(expenses, transfer) {
        expenses.filter { exp ->
            exp.payerId == transfer.toMemberId &&
                exp.participants.any { it.memberId == transfer.fromMemberId && !it.excluded }
        }
    }

    val debtorFrontedExpenses = remember(expenses, transfer) {
        expenses.filter { exp ->
            exp.payerId == transfer.fromMemberId &&
                exp.participants.any { it.memberId == transfer.toMemberId && !it.excluded }
        }
    }

    // Direct payments already recorded between them
    val directPayments = remember(payments, transfer) {
        payments.filter { p ->
            (p.fromMemberId == transfer.fromMemberId && p.toMemberId == transfer.toMemberId) ||
            (p.fromMemberId == transfer.toMemberId && p.toMemberId == transfer.fromMemberId)
        }
    }

    val totalDirectPaidFromDebtor = remember(directPayments, transfer) {
        val sumMinor = directPayments
            .filter { it.fromMemberId == transfer.fromMemberId && it.toMemberId == transfer.toMemberId }
            .sumOf { it.amount.amountMinor }
        Money(sumMinor, currency)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settlement Obligation",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    FSStatusBadge(
                        text = "Verified Fact",
                        textColor = FairShareTheme.colors.accent,
                        backgroundColor = FairShareTheme.colors.accentSoft
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hero Transfer Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
                        .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = transfer.fromMemberName,
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.negative
                                )
                                Text(
                                    text = "pays",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = transfer.toMemberName,
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.positive
                                )
                            }
                            Text(
                                text = transfer.amount.formatted(),
                                style = FairShareTheme.typography.display,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Why This Settlement (Mathematical and Algorithmic Explanation)
                Text(
                    text = "Why This Settlement",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val debtorNet = payerBalance?.netBalance ?: Money.zero(currency)
                        val creditorNet = receiverBalance?.netBalance ?: Money.zero(currency)

                        Text(
                            text = "Algorithmic Engine Resolution",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• ${transfer.fromMemberName} has an overall group net deficit of ${debtorNet.formatted()} (Total Paid: ${payerBalance?.paid?.formatted() ?: "₹0"}, Total Share: ${payerBalance?.consumed?.formatted() ?: "₹0"}).",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• ${transfer.toMemberName} is owed an overall group net credit of ${creditorNet.formatted()} (Total Paid: ${receiverBalance?.paid?.formatted() ?: "₹0"}, Total Share: ${receiverBalance?.consumed?.formatted() ?: "₹0"}).",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• To extinguish group debts with the fewest total transactions and zero circular payments, SettlementEngine assigned an exact transfer of ${transfer.amount.formatted()} directly between them.",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Net Balance Positions
                Text(
                    text = "Ledger Audit & Net Balances",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Debtor position breakdown
                if (payerBalance != null) {
                    MemberBreakdownCard(
                        roleLabel = "Debtor: ${transfer.fromMemberName}",
                        paid = payerBalance.paid,
                        consumed = payerBalance.consumed,
                        net = payerBalance.netBalance,
                        isNegative = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Creditor position breakdown
                if (receiverBalance != null) {
                    MemberBreakdownCard(
                        roleLabel = "Creditor: ${transfer.toMemberName}",
                        paid = receiverBalance.paid,
                        consumed = receiverBalance.consumed,
                        net = receiverBalance.netBalance,
                        isNegative = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. Relevant Contributing Expenses
                if (creditorFrontedExpenses.isNotEmpty() || debtorFrontedExpenses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Contributing Shared Expenses",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (creditorFrontedExpenses.isNotEmpty()) {
                        Text(
                            text = "Paid by ${transfer.toMemberName} (${transfer.fromMemberName} consumed share):",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        creditorFrontedExpenses.forEach { exp ->
                            val splits = com.fairshare.android.core.domain.split.ExpenseSplitEngine.calculateSplits(exp)
                            val debtorShare = splits[transfer.fromMemberId] ?: Money.zero(currency)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exp.description,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Total: ${exp.totalAmount.formatted()}",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${transfer.fromMemberName}'s share:",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = debtorShare.formatted(),
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.negative,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (debtorFrontedExpenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Offsetting: Paid by ${transfer.fromMemberName} (${transfer.toMemberName} consumed share):",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        debtorFrontedExpenses.forEach { exp ->
                            val splits = com.fairshare.android.core.domain.split.ExpenseSplitEngine.calculateSplits(exp)
                            val creditorShare = splits[transfer.toMemberId] ?: Money.zero(currency)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exp.description,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Total: ${exp.totalAmount.formatted()}",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${transfer.toMemberName}'s share:",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = creditorShare.formatted(),
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.positive,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // 4. Direct Payments Already Recorded
                if (directPayments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prior Direct Payments Recorded (${totalDirectPaidFromDebtor.formatted()} applied)",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    directPayments.forEach { p ->
                        val fromName = members.firstOrNull { it.id == p.fromMemberId }?.name ?: "Unknown"
                        val toName = members.firstOrNull { it.id == p.toMemberId }?.name ?: "Unknown"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$fromName paid $toName",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = p.amount.formatted(),
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Simulate",
                        onClick = {
                            onDismiss()
                            onSimulatePaymentClick(transfer.fromMemberId, transfer.toMemberId, transfer.amount)
                        },
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Record Payment",
                        onClick = {
                            onDismiss()
                            onRecordPaymentClick(transfer.fromMemberId, transfer.toMemberId, transfer.amount)
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                FSButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MemberBreakdownCard(
    roleLabel: String,
    paid: Money,
    consumed: Money,
    net: Money,
    isNegative: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = roleLabel,
            style = FairShareTheme.typography.body,
            color = FairShareTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Paid:",
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textSecondary
            )
            Text(
                text = paid.formatted(),
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Fair Share (Consumed):",
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textSecondary
            )
            Text(
                text = consumed.formatted(),
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Net Balance:",
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textSecondary
            )
            Text(
                text = (if (net.amountMinor > 0) "+" else "") + net.formatted(),
                style = FairShareTheme.typography.body,
                color = if (isNegative) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
