package com.fairshare.android.core.domain.split

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.rounding.RoundingEngine

/**
 * Deterministic Expense Split Engine.
 * Supports EQUAL, EXACT, PERCENTAGE, SHARES, and ITEMIZED split modes,
 * with payer-excluded and participant-exclusion support.
 */
object ExpenseSplitEngine {

    /**
     * Calculates the exact minor units allocated to each participant.
     * Returns a map of memberId to Money (consumed amount).
     * Guarantees that the sum of returned Money values strictly equals expense.totalAmount.
     */
    fun calculateSplits(expense: Expense): Map<String, Money> {
        val totalMinor = expense.totalAmount.amountMinor
        val currency = expense.totalAmount.currency

        if (totalMinor == 0L) {
            return expense.participants.associate { it.memberId to Money.zero(currency) }
        }

        val activeParticipants = expense.participants.filter { !it.excluded }
        require(activeParticipants.isNotEmpty()) {
            "Expense must have at least one non-excluded participant"
        }

        val activeIds = activeParticipants.map { it.memberId }

        val minorAllocations: Map<String, Long> = when (expense.splitMethod) {
            SplitMethod.EQUAL -> {
                RoundingEngine.distributeEqual(totalMinor, activeIds)
            }

            SplitMethod.EXACT -> {
                val assignedSum = activeParticipants.sumOf { it.exactAmount?.amountMinor ?: 0L }
                require(assignedSum == totalMinor) {
                    "Exact amounts sum ($assignedSum) must equal expense total ($totalMinor)"
                }
                activeParticipants.associate {
                    it.memberId to (it.exactAmount?.amountMinor ?: 0L)
                }
            }

            SplitMethod.PERCENTAGE -> {
                val totalBasisPoints = activeParticipants.sumOf { it.percentageBasisPoints?.toLong() ?: 0L }
                require(totalBasisPoints == 10000L) {
                    "Percentage basis points must sum to exactly 10000 (100%), got $totalBasisPoints"
                }
                val weighted = activeParticipants.map {
                    RoundingEngine.WeightedParticipant(
                        id = it.memberId,
                        weight = it.percentageBasisPoints?.toLong() ?: 0L
                    )
                }
                RoundingEngine.distribute(totalMinor, weighted)
            }

            SplitMethod.SHARES -> {
                val weighted = activeParticipants.map {
                    val shares = it.shares?.toLong() ?: 1L
                    require(shares > 0L) { "Shares for member ${it.memberId} must be > 0" }
                    RoundingEngine.WeightedParticipant(id = it.memberId, weight = shares)
                }
                RoundingEngine.distribute(totalMinor, weighted)
            }

            SplitMethod.ITEMIZED -> {
                require(expense.items.isNotEmpty()) {
                    "Itemized split requires at least one expense item"
                }

                // Sum of items per participant
                val itemShares = HashMap<String, Long>()
                for (id in activeIds) {
                    itemShares[id] = 0L
                }

                var itemsTotalMinor = 0L
                for (item in expense.items) {
                    val itemMinor = item.amount.amountMinor
                    require(itemMinor >= 0L) { "Item amount must be non-negative" }
                    itemsTotalMinor += itemMinor

                    val itemActiveParticipants = item.participantMemberIds.filter { activeIds.contains(it) }
                    require(itemActiveParticipants.isNotEmpty()) {
                        "Item '${item.name}' must have at least one active participant"
                    }

                    val split = RoundingEngine.distributeEqual(itemMinor, itemActiveParticipants)
                    for ((id, share) in split) {
                        itemShares[id] = (itemShares[id] ?: 0L) + share
                    }
                }

                if (itemsTotalMinor == totalMinor) {
                    itemShares
                } else {
                    // There is a tax, tip, fee, or discount (totalMinor != itemsTotalMinor)
                    // Distribute difference proportionally to each participant's item share
                    if (itemsTotalMinor > 0L) {
                        val weighted = activeIds.map { id ->
                            RoundingEngine.WeightedParticipant(id = id, weight = itemShares[id] ?: 0L)
                        }
                        RoundingEngine.distribute(totalMinor, weighted)
                    } else {
                        // If items were 0 but total > 0, fallback to equal distribution
                        RoundingEngine.distributeEqual(totalMinor, activeIds)
                    }
                }
            }
        }

        // Build result including 0 for excluded participants
        val resultMap = HashMap<String, Money>(expense.participants.size)
        for (p in expense.participants) {
            val amount = minorAllocations[p.memberId] ?: 0L
            resultMap[p.memberId] = Money(amount, currency)
        }

        return resultMap
    }
}
