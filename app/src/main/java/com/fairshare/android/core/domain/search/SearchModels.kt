package com.fairshare.android.core.domain.search

import com.fairshare.android.core.domain.model.ExpenseCategory

enum class SearchEntityType {
    EXPENSE,
    SETTLEMENT,
    MEMBER,
    ACTIVITY,
    MESSAGE
}

enum class SearchSortBy(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    AMOUNT_DESC("Highest amount"),
    AMOUNT_ASC("Lowest amount"),
    TITLE_ASC("Alphabetical")
}

data class SearchFilter(
    val query: String = "",
    val groupId: String? = null,
    val category: ExpenseCategory? = null,
    val memberId: String? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val startDateEpochMs: Long? = null,
    val endDateEpochMs: Long? = null,
    val entityTypes: Set<SearchEntityType> = SearchEntityType.values().toSet(),
    val sortBy: SearchSortBy = SearchSortBy.DATE_DESC
)

data class SearchResult(
    val id: String,
    val entityId: String,
    val groupId: String,
    val type: SearchEntityType,
    val title: String,
    val subtitle: String,
    val amountFormatted: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
