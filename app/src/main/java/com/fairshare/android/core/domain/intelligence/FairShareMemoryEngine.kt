package com.fairshare.android.core.domain.intelligence

import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SplitMethod

data class MemorySplitSuggestion(
    val category: ExpenseCategory,
    val suggestedPayerId: String?,
    val suggestedPayerName: String?,
    val suggestedSplitMethod: SplitMethod,
    val excludedMemberIds: List<String>,
    val explanation: String
)

object FairShareMemoryEngine {

    fun learnPatternForCategory(
        category: ExpenseCategory,
        existingExpenses: List<Expense>,
        members: List<Member>
    ): MemorySplitSuggestion? {
        val categoryExpenses = existingExpenses.filter { it.category == category && !it.isDeleted }
        if (categoryExpenses.isEmpty()) return null

        val memberMap = members.associateBy { it.id }

        // 1. Identify most frequent payer for this category
        val payerCounts = mutableMapOf<String, Int>()
        for (exp in categoryExpenses) {
            val payerId = exp.payerId
            if (payerId.isNotBlank()) {
                payerCounts[payerId] = (payerCounts[payerId] ?: 0) + 1
            }
        }
        val topPayerEntry = payerCounts.maxByOrNull { it.value }
        val topPayerId = if (topPayerEntry != null && topPayerEntry.value >= 2) topPayerEntry.key else null
        val topPayerName = topPayerId?.let { memberMap[it]?.name }

        // 2. Identify preferred split method
        val methodCounts = categoryExpenses.groupingBy { it.splitMethod }.eachCount()
        val topMethod = methodCounts.maxByOrNull { it.value }?.key ?: SplitMethod.EQUAL

        // 3. Identify frequently excluded members for this category
        val excludedCounts = mutableMapOf<String, Int>()
        for (exp in categoryExpenses) {
            for (p in exp.participants) {
                if (p.excluded) {
                    excludedCounts[p.memberId] = (excludedCounts[p.memberId] ?: 0) + 1
                }
            }
        }
        val frequentExclusions = excludedCounts.filter { it.value >= 2 }.keys.toList()

        val explanation = buildString {
            if (topPayerName != null) {
                append("$topPayerName usually pays for ${category.displayName.lowercase()}. ")
            }
            if (frequentExclusions.isNotEmpty()) {
                val excludedNames = frequentExclusions.mapNotNull { memberMap[it]?.name }.joinToString(", ")
                append("Previous entries often excluded $excludedNames. ")
            }
            append("Split method was usually ${topMethod.name.lowercase().replaceFirstChar { it.titlecase() }}.")
        }.trim()

        return MemorySplitSuggestion(
            category = category,
            suggestedPayerId = topPayerId,
            suggestedPayerName = topPayerName,
            suggestedSplitMethod = topMethod,
            excludedMemberIds = frequentExclusions,
            explanation = explanation
        )
    }
}
