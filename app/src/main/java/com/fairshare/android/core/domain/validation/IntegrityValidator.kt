package com.fairshare.android.core.domain.validation

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.ledger.GroupLedger
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod

/**
 * Deterministic Financial Integrity Validator.
 * Enforces all non-negotiable financial constraints, boundary conditions,
 * and mathematical conservation invariants before records enter the ledger.
 */
object IntegrityValidator {

    fun validateExpense(expense: Expense): ValidationResult {
        val errors = ArrayList<String>()

        if (!expense.totalAmount.isPositive) {
            errors.add("Expense total amount must be strictly positive")
        }

        if (expense.payers.isEmpty()) {
            errors.add("Expense must have at least one payer")
        } else {
            val payersSum = expense.payers.sumOf { it.amount.amountMinor }
            if (payersSum != expense.totalAmount.amountMinor) {
                errors.add("Sum of payer contributions ($payersSum) must equal total expense amount (${expense.totalAmount.amountMinor})")
            }
            for (p in expense.payers) {
                if (p.memberId.isBlank()) {
                    errors.add("Payer memberId cannot be blank")
                }
                if (!p.amount.isPositive) {
                    errors.add("Payer contribution for ${p.memberId} must be positive")
                }
            }
        }

        val activeParticipants = expense.participants.filter { !it.excluded }
        if (activeParticipants.isEmpty()) {
            errors.add("Expense must have at least one active participant")
        }

        when (expense.splitMethod) {
            SplitMethod.EQUAL -> {
                // Handled by activeParticipants check
            }
            SplitMethod.EXACT -> {
                val assignedSum = activeParticipants.sumOf { it.exactAmount?.amountMinor ?: 0L }
                if (assignedSum != expense.totalAmount.amountMinor) {
                    errors.add("Exact split amounts sum ($assignedSum) must equal total amount (${expense.totalAmount.amountMinor})")
                }
            }
            SplitMethod.PERCENTAGE -> {
                val totalBp = activeParticipants.sumOf { it.percentageBasisPoints?.toLong() ?: 0L }
                if (totalBp != 10000L) {
                    errors.add("Percentage split must sum to exactly 10000 basis points (100.00%), got $totalBp")
                }
            }
            SplitMethod.SHARES -> {
                val totalShares = activeParticipants.sumOf { it.shares?.toLong() ?: 0L }
                if (totalShares <= 0L) {
                    errors.add("Shares split must have positive total shares, got $totalShares")
                }
                for (ap in activeParticipants) {
                    if ((ap.shares ?: 0) <= 0) {
                        errors.add("Participant ${ap.memberId} shares must be > 0")
                    }
                }
            }
            SplitMethod.ITEMIZED -> {
                if (expense.items.isEmpty()) {
                    errors.add("Itemized split requires at least one expense item")
                } else {
                    val activeIds = activeParticipants.map { it.memberId }.toSet()
                    for (item in expense.items) {
                        if (!item.amount.isPositive) {
                            errors.add("Item '${item.name}' amount must be positive")
                        }
                        val itemActive = item.participantMemberIds.filter { activeIds.contains(it) }
                        if (itemActive.isEmpty()) {
                            errors.add("Item '${item.name}' must have at least one active participant")
                        }
                    }
                }
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun validateSplit(expense: Expense, splits: Map<String, Money>): ValidationResult {
        val errors = ArrayList<String>()
        val totalMinor = expense.totalAmount.amountMinor

        val sumMinor = splits.values.sumOf { it.amountMinor }
        if (sumMinor != totalMinor) {
            errors.add("Splits sum ($sumMinor) must strictly equal expense total ($totalMinor)")
        }

        for ((id, money) in splits) {
            if (money.amountMinor < 0L) {
                errors.add("Split allocation for member $id cannot be negative: ${money.amountMinor}")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun validatePayment(payment: SettlementPayment): ValidationResult {
        val errors = ArrayList<String>()

        if (!payment.amount.isPositive) {
            errors.add("Payment amount must be strictly positive")
        }
        if (payment.fromMemberId.isBlank()) {
            errors.add("Sender (fromMemberId) cannot be blank")
        }
        if (payment.toMemberId.isBlank()) {
            errors.add("Receiver (toMemberId) cannot be blank")
        }
        if (payment.fromMemberId == payment.toMemberId) {
            errors.add("Sender and receiver cannot be the same member (${payment.fromMemberId})")
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun validateLedger(ledger: GroupLedger): ValidationResult {
        val errors = ArrayList<String>()
        val sumNet = ledger.memberLedgers.values.sumOf { it.netBalance.amountMinor }
        if (sumNet != 0L) {
            errors.add("Ledger zero-sum conservation violation: sum(net balances) = $sumNet (must be 0)")
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
