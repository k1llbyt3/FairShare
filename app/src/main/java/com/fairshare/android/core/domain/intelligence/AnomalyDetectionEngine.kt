package com.fairshare.android.core.domain.intelligence

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Group

enum class AnomalyType {
    UNUSUALLY_LARGE,
    POSSIBLE_DUPLICATE,
    CATEGORY_SPIKE,
    DATE_OUTSIDE_TRIP,
    RAPID_FREQUENCY
}

data class AnomalyAlert(
    val type: AnomalyType,
    val title: String,
    val explanation: String,
    val expenseId: String? = null
)

object AnomalyDetectionEngine {

    fun detectAnomalies(
        targetExpense: Expense,
        existingExpenses: List<Expense>,
        group: Group? = null
    ): List<AnomalyAlert> {
        val alerts = mutableListOf<AnomalyAlert>()
        val otherExpenses = existingExpenses.filter { it.id != targetExpense.id && !it.isDeleted }

        // 1. Possible duplicate detection
        val duplicateAlert = checkDuplicate(targetExpense, otherExpenses)
        if (duplicateAlert != null) {
            alerts.add(duplicateAlert)
        }

        // 2. Unusually large expense compared with group history
        if (otherExpenses.size >= 3) {
            val amounts = otherExpenses.map { it.totalAmount.amountMinor }
            val average = amounts.average()
            val targetAmount = targetExpense.totalAmount.amountMinor

            if (average > 0 && targetAmount > (average * 2.5) && targetAmount > 200000L) { // > 2.5x avg and > ₹2,000
                alerts.add(
                    AnomalyAlert(
                        type = AnomalyType.UNUSUALLY_LARGE,
                        title = "Higher than usual",
                        explanation = "${targetExpense.totalAmount.formatted()} is significantly higher than your recent average expense (${Money(average.toLong(), targetExpense.totalAmount.currency).formatted()}) in this group.",
                        expenseId = targetExpense.id
                    )
                )
            }

            // Category specific comparison
            val sameCategoryExpenses = otherExpenses.filter { it.category == targetExpense.category }
            if (sameCategoryExpenses.size >= 2) {
                val catAvg = sameCategoryExpenses.map { it.totalAmount.amountMinor }.average()
                if (catAvg > 0 && targetAmount > (catAvg * 3.0)) {
                    alerts.add(
                        AnomalyAlert(
                            type = AnomalyType.CATEGORY_SPIKE,
                            title = "Unusual category amount",
                            explanation = "${targetExpense.totalAmount.formatted()} is notably higher than usual ${targetExpense.category.displayName} expenses in this group.",
                            expenseId = targetExpense.id
                        )
                    )
                }
            }
        }

        // 3. Date outside trip boundaries
        if (group != null && group.startDate != null && group.endDate != null) {
            val start = group.startDate
            val end = group.endDate + 86_400_000L // allow 1 day buffer
            if (targetExpense.timestamp < start || targetExpense.timestamp > end) {
                alerts.add(
                    AnomalyAlert(
                        type = AnomalyType.DATE_OUTSIDE_TRIP,
                        title = "Date outside trip schedule",
                        explanation = "Expense date falls outside the planned trip dates for ${group.name}.",
                        expenseId = targetExpense.id
                    )
                )
            }
        }

        return alerts
    }

    private fun checkDuplicate(target: Expense, others: List<Expense>): AnomalyAlert? {
        val targetAmount = target.totalAmount.amountMinor
        val targetDesc = target.description.trim().lowercase()

        for (other in others) {
            val sameAmount = other.totalAmount.amountMinor == targetAmount
            val otherDesc = other.description.trim().lowercase()
            val sameDesc = targetDesc.isNotEmpty() && otherDesc.isNotEmpty() &&
                    (targetDesc == otherDesc || targetDesc.contains(otherDesc) || otherDesc.contains(targetDesc))
            val timeDiffMillis = kotlin.math.abs(target.timestamp - other.timestamp)
            val isWithinTwoHours = timeDiffMillis <= (2 * 3600 * 1000L)

            if (sameAmount && sameDesc && isWithinTwoHours) {
                return AnomalyAlert(
                    type = AnomalyType.POSSIBLE_DUPLICATE,
                    title = "Possible duplicate",
                    explanation = "A similar expense '${other.description}' for ${other.totalAmount.formatted()} was already recorded recently.",
                    expenseId = target.id
                )
            }
        }
        return null
    }
}
