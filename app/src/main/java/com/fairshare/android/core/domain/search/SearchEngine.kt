package com.fairshare.android.core.domain.search

import com.fairshare.android.core.domain.collaboration.GroupActivityItem
import com.fairshare.android.core.domain.collaboration.GroupMessage
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment

object SearchEngine {

    fun executeSearch(
        filter: SearchFilter,
        expenses: List<Expense> = emptyList(),
        payments: List<SettlementPayment> = emptyList(),
        members: List<Member> = emptyList(),
        activities: List<GroupActivityItem> = emptyList(),
        messages: List<GroupMessage> = emptyList()
    ): List<SearchResult> {
        val q = filter.query.trim().lowercase()
        val results = mutableListOf<SearchResult>()

        // 1. Search Expenses
        if (filter.entityTypes.contains(SearchEntityType.EXPENSE)) {
            val memberMap = members.associateBy { it.id }
            for (exp in expenses) {
                if (filter.groupId != null && exp.groupId.isNotBlank() && exp.groupId != filter.groupId) continue
                if (filter.category != null && exp.category != filter.category) continue

                // Check member filter (payer or participant)
                if (filter.memberId != null) {
                    val isPayer = exp.payers.any { it.memberId == filter.memberId }
                    val isParticipant = exp.participants.any { it.memberId == filter.memberId }
                    if (!isPayer && !isParticipant) continue
                }

                // Check amount range
                if (filter.minAmountMinor != null && exp.totalAmount.amountMinor < filter.minAmountMinor) continue
                if (filter.maxAmountMinor != null && exp.totalAmount.amountMinor > filter.maxAmountMinor) continue

                // Check date range
                if (filter.startDateEpochMs != null && exp.timestamp < filter.startDateEpochMs) continue
                if (filter.endDateEpochMs != null && exp.timestamp > filter.endDateEpochMs) continue

                // Text query match: description, notes, category, merchant, payer name
                val payerNames = exp.payers.mapNotNull { memberMap[it.memberId]?.name }.joinToString(" ")
                val match = q.isBlank() ||
                        exp.description.lowercase().contains(q) ||
                        exp.category.displayName.lowercase().contains(q) ||
                        (exp.notes?.lowercase()?.contains(q) == true) ||
                        payerNames.lowercase().contains(q)

                if (match) {
                    results.add(
                        SearchResult(
                            id = "exp_${exp.id}",
                            entityId = exp.id,
                            groupId = exp.groupId,
                            type = SearchEntityType.EXPENSE,
                            title = exp.description,
                            subtitle = "${exp.category.displayName} • Paid by $payerNames",
                            amountFormatted = exp.totalAmount.formatted(),
                            timestamp = exp.timestamp
                        )
                    )
                }
            }
        }

        // 2. Search Settlements
        if (filter.entityTypes.contains(SearchEntityType.SETTLEMENT)) {
            val memberMap = members.associateBy { it.id }
            for (p in payments) {
                if (filter.category != null) continue
                if (filter.groupId != null && p.groupId.isNotBlank() && p.groupId != filter.groupId) continue
                if (filter.memberId != null && p.fromMemberId != filter.memberId && p.toMemberId != filter.memberId) continue
                if (filter.minAmountMinor != null && p.amount.amountMinor < filter.minAmountMinor) continue
                if (filter.maxAmountMinor != null && p.amount.amountMinor > filter.maxAmountMinor) continue
                if (filter.startDateEpochMs != null && p.timestamp < filter.startDateEpochMs) continue
                if (filter.endDateEpochMs != null && p.timestamp > filter.endDateEpochMs) continue

                val fromName = memberMap[p.fromMemberId]?.name ?: "Member"
                val toName = memberMap[p.toMemberId]?.name ?: "Member"
                val textToMatch = "$fromName to $toName ${p.note ?: ""}".lowercase()

                if (q.isBlank() || textToMatch.contains(q)) {
                    results.add(
                        SearchResult(
                            id = "pay_${p.id}",
                            entityId = p.id,
                            groupId = p.groupId,
                            type = SearchEntityType.SETTLEMENT,
                            title = "$fromName → $toName",
                            subtitle = "Settlement Payment" + (p.note?.let { " • $it" } ?: ""),
                            amountFormatted = p.amount.formatted(),
                            timestamp = p.timestamp
                        )
                    )
                }
            }
        }

        // 3. Search Members
        if (filter.entityTypes.contains(SearchEntityType.MEMBER) && q.isNotBlank()) {
            for (m in members) {
                if (m.name.lowercase().contains(q)) {
                    results.add(
                        SearchResult(
                            id = "mem_${m.id}",
                            entityId = m.id,
                            groupId = filter.groupId ?: "",
                            type = SearchEntityType.MEMBER,
                            title = m.name,
                            subtitle = if (m.isCurrentUser) "You" else "Group Member",
                            amountFormatted = null,
                            timestamp = 0L
                        )
                    )
                }
            }
        }

        // 4. Search Activities
        if (filter.entityTypes.contains(SearchEntityType.ACTIVITY) && q.isNotBlank()) {
            for (a in activities) {
                if (filter.groupId != null && a.groupId != filter.groupId) continue
                if (a.summaryText.lowercase().contains(q) || a.actorName.lowercase().contains(q)) {
                    results.add(
                        SearchResult(
                            id = "act_${a.id}",
                            entityId = a.entityId ?: a.id,
                            groupId = a.groupId,
                            type = SearchEntityType.ACTIVITY,
                            title = a.summaryText,
                            subtitle = a.actorName,
                            amountFormatted = null,
                            timestamp = a.timestamp
                        )
                    )
                }
            }
        }

        // 5. Search Messages
        if (filter.entityTypes.contains(SearchEntityType.MESSAGE) && q.isNotBlank()) {
            for (msg in messages) {
                if (filter.groupId != null && msg.groupId != filter.groupId) continue
                if (msg.content.lowercase().contains(q) || msg.senderName.lowercase().contains(q)) {
                    results.add(
                        SearchResult(
                            id = "msg_${msg.id}",
                            entityId = msg.referencedEntityId ?: msg.id,
                            groupId = msg.groupId,
                            type = SearchEntityType.MESSAGE,
                            title = msg.content,
                            subtitle = "Chat message from ${msg.senderName}",
                            amountFormatted = null,
                            timestamp = msg.timestamp
                        )
                    )
                }
            }
        }

        // Sort results
        return when (filter.sortBy) {
            SearchSortBy.DATE_DESC -> results.sortedByDescending { it.timestamp }
            SearchSortBy.DATE_ASC -> results.sortedBy { it.timestamp }
            SearchSortBy.AMOUNT_DESC -> results.sortedByDescending {
                extractAmountMinor(it.amountFormatted)
            }
            SearchSortBy.AMOUNT_ASC -> results.sortedBy {
                extractAmountMinor(it.amountFormatted)
            }
            SearchSortBy.TITLE_ASC -> results.sortedBy { it.title.lowercase() }
        }
    }

    private fun extractAmountMinor(formatted: String?): Long {
        if (formatted == null) return 0L
        val clean = formatted.replace(Regex("""[^0-9.]"""), "")
        return try {
            val d = clean.toDouble()
            (d * 100).toLong()
        } catch (_: Exception) {
            0L
        }
    }
}
