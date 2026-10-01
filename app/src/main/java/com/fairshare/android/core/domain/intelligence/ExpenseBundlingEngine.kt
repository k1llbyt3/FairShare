package com.fairshare.android.core.domain.intelligence

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import java.util.UUID

data class ExpenseBundle(
    val id: String,
    val title: String,
    val expenseIds: List<String>,
    val totalAmount: Money,
    val expenseCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

object ExpenseBundlingEngine {

    fun calculateBundleTotal(expenses: List<Expense>, currency: Currency = Currency.INR): Money {
        val totalMinor = expenses.filter { !it.isDeleted }.sumOf { it.totalAmount.amountMinor }
        return Money(totalMinor, currency)
    }

    fun createBundle(
        title: String,
        expenses: List<Expense>,
        id: String = UUID.randomUUID().toString()
    ): ExpenseBundle {
        val active = expenses.filter { !it.isDeleted }
        val currency = active.firstOrNull()?.totalAmount?.currency ?: Currency.INR
        val total = calculateBundleTotal(active, currency)
        return ExpenseBundle(
            id = id,
            title = title,
            expenseIds = active.map { it.id },
            totalAmount = total,
            expenseCount = active.size
        )
    }

    /**
     * Suggests possible bundles based on expenses occurring within close time windows (e.g. 6 hours)
     * across complementary categories like Food, Transport, and Activities.
     */
    fun suggestBundles(expenses: List<Expense>): List<ExpenseBundle> {
        val active = expenses.filter { !it.isDeleted }.sortedBy { it.timestamp }
        if (active.size < 2) return emptyList()

        val suggestions = mutableListOf<ExpenseBundle>()
        val visitedIds = mutableSetOf<String>()

        var i = 0
        while (i < active.size) {
            val current = active[i]
            if (visitedIds.contains(current.id)) {
                i++
                continue
            }

            val cluster = mutableListOf(current)
            var j = i + 1
            while (j < active.size) {
                val candidate = active[j]
                val timeDiff = candidate.timestamp - current.timestamp
                if (timeDiff <= 6 * 3600 * 1000L && !visitedIds.contains(candidate.id)) { // within 6 hours
                    cluster.add(candidate)
                }
                j++
            }

            if (cluster.size >= 2) {
                // Check if cluster spans meaningful night-out / day activity categories
                val categories = cluster.map { it.category }.toSet()
                val isActivityLike = categories.contains(ExpenseCategory.FOOD) ||
                        categories.contains(ExpenseCategory.TRANSPORT) ||
                        categories.contains(ExpenseCategory.ACTIVITIES)

                if (isActivityLike) {
                    val title = deriveBundleTitle(cluster)
                    val bundle = createBundle(title, cluster)
                    suggestions.add(bundle)
                    cluster.forEach { visitedIds.add(it.id) }
                }
            }
            i++
        }

        return suggestions
    }

    private fun deriveBundleTitle(cluster: List<Expense>): String {
        val categories = cluster.map { it.category }
        return when {
            categories.contains(ExpenseCategory.FOOD) && categories.contains(ExpenseCategory.TRANSPORT) -> "Evening Out"
            categories.contains(ExpenseCategory.ACTIVITIES) -> "Activity Group"
            categories.contains(ExpenseCategory.FOOD) -> "Meal & Drinks"
            else -> "Group Activity"
        }
    }
}
