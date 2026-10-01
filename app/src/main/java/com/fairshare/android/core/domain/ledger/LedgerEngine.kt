package com.fairshare.android.core.domain.ledger

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Adjustment
import com.fairshare.android.core.domain.model.AdjustmentType
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.split.ExpenseSplitEngine

/**
 * Authoritative Deterministic Ledger Engine.
 * Integrates expenses, direct settlement payments, and adjustments.
 * Enforces the zero-sum ledger conservation invariant: sum(net balances) == 0.
 */
object LedgerEngine {

    fun calculateLedger(
        members: List<Member>,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        adjustments: List<Adjustment> = emptyList(),
        currency: Currency = Currency.INR
    ): GroupLedger {
        val memberIds = members.map { it.id }.toSet()

        val paidMap = HashMap<String, Long>()
        val consumedMap = HashMap<String, Long>()
        val adjustmentMap = HashMap<String, Long>()

        for (id in memberIds) {
            paidMap[id] = 0L
            consumedMap[id] = 0L
            adjustmentMap[id] = 0L
        }

        var totalExpenseSpendMinor = 0L
        var totalDirectTransfersMinor = 0L
        var totalAdjustmentsMinor = 0L

        // 1. Process active expenses
        for (expense in expenses.filter { !it.isDeleted }) {
            totalExpenseSpendMinor += expense.totalAmount.amountMinor

            // Process payers: whoever contributed gets credited to 'paid'
            for (payer in expense.payers) {
                val current = paidMap[payer.memberId] ?: 0L
                paidMap[payer.memberId] = current + payer.amount.amountMinor
            }

            // Process splits: participants get debited to 'consumed'
            val splits = ExpenseSplitEngine.calculateSplits(expense)
            for ((memberId, share) in splits) {
                val current = consumedMap[memberId] ?: 0L
                consumedMap[memberId] = current + share.amountMinor
            }
        }

        // 2. Process active direct payments (From paid To)
        // From paid money (credited), To received value / debt satisfaction (debited)
        for (payment in payments.filter { !it.isDeleted }) {
            val amountMinor = payment.amount.amountMinor
            totalDirectTransfersMinor += amountMinor

            val fromPaid = paidMap[payment.fromMemberId] ?: 0L
            paidMap[payment.fromMemberId] = fromPaid + amountMinor

            val toConsumed = consumedMap[payment.toMemberId] ?: 0L
            consumedMap[payment.toMemberId] = toConsumed + amountMinor
        }

        // 3. Process active adjustments
        for (adj in adjustments.filter { !it.isDeleted }) {
            val amountMinor = adj.amount.amountMinor
            totalAdjustmentsMinor += amountMinor

            val currentAdj = adjustmentMap[adj.memberId] ?: 0L
            val netDelta = when (adj.type) {
                AdjustmentType.REFUND -> amountMinor // Credited back to member
                AdjustmentType.REVERSAL -> -amountMinor // Debited from member or reversed
                AdjustmentType.MANUAL_ADJUSTMENT -> amountMinor
            }
            adjustmentMap[adj.memberId] = currentAdj + netDelta
        }

        // 4. Build MemberLedger entries
        val memberLedgers = HashMap<String, MemberLedger>()
        for (member in members) {
            val p = paidMap[member.id] ?: 0L
            val c = consumedMap[member.id] ?: 0L
            val a = adjustmentMap[member.id] ?: 0L
            val net = p - c + a

            memberLedgers[member.id] = MemberLedger(
                memberId = member.id,
                paid = Money(p, currency),
                consumed = Money(c, currency),
                adjustmentNet = Money(a, currency),
                netBalance = Money(net, currency)
            )
        }

        val groupLedger = GroupLedger(
            currency = currency,
            memberLedgers = memberLedgers,
            totalExpenseSpend = Money(totalExpenseSpendMinor, currency),
            totalDirectTransfers = Money(totalDirectTransfersMinor, currency),
            totalAdjustments = Money(totalAdjustmentsMinor, currency)
        )

        // Strict verification of zero-sum invariant
        check(groupLedger.isBalanced) {
            "Ledger conservation invariant violation: sum of net balances is ${groupLedger.memberLedgers.values.sumOf { it.netBalance.amountMinor }} != 0"
        }

        return groupLedger
    }
}
