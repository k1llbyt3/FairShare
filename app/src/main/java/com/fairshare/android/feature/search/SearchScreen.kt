package com.fairshare.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.search.SearchEngine
import com.fairshare.android.core.domain.search.SearchFilter
import com.fairshare.android.core.domain.search.SearchResult
import com.fairshare.android.core.domain.search.SearchSortBy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    groupId: String? = null,
    container: FairShareAppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (type: String, entityId: String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedSort by remember { mutableStateOf(SearchSortBy.DATE_DESC) }

    var allExpenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var allPayments by remember { mutableStateOf<List<SettlementPayment>>(emptyList()) }
    var allMembers by remember { mutableStateOf<List<Member>>(emptyList()) }

    LaunchedEffect(groupId) {
        val groups = if (groupId != null) listOf(groupId) else container.database.groupDao().getAllActiveGroups().map { it.id }
        val exps = mutableListOf<Expense>()
        val pays = mutableListOf<SettlementPayment>()
        val mems = mutableListOf<Member>()

        for (gid in groups) {
            exps.addAll(container.expenseRepository.getExpenses(gid))
            pays.addAll(container.settlementRepository.getPayments(gid))
            mems.addAll(container.groupRepository.getMembers(gid))
        }
        allExpenses = exps
        allPayments = pays
        allMembers = mems.distinctBy { it.id }
    }

    val results = remember(queryText, selectedCategory, selectedSort, allExpenses, allPayments) {
        SearchEngine.executeSearch(
            filter = SearchFilter(
                query = queryText,
                groupId = groupId,
                category = selectedCategory,
                sortBy = selectedSort
            ),
            expenses = allExpenses,
            payments = allPayments,
            members = allMembers
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search & Filter",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FairShareTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FairShareTheme.colors.surface)
            )
        },
        containerColor = FairShareTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input Field
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = {
                        Text(
                            text = "Search expenses, merchants, members...",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textTertiary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (queryText.isNotBlank()) {
                            IconButton(onClick = { queryText = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    singleLine = true
                )
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Categories") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FairShareTheme.colors.accent,
                        selectedLabelColor = FairShareTheme.colors.background
                    )
                )

                ExpenseCategory.values().forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FairShareTheme.colors.accent,
                            selectedLabelColor = FairShareTheme.colors.background
                        )
                    )
                }
            }

            // Results Count & Sorting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${results.size} records found",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Text(
                    text = selectedSort.label,
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.accent,
                    modifier = Modifier.clickable {
                        selectedSort = when (selectedSort) {
                            SearchSortBy.DATE_DESC -> SearchSortBy.AMOUNT_DESC
                            SearchSortBy.AMOUNT_DESC -> SearchSortBy.AMOUNT_ASC
                            SearchSortBy.AMOUNT_ASC -> SearchSortBy.TITLE_ASC
                            SearchSortBy.TITLE_ASC -> SearchSortBy.DATE_DESC
                            else -> SearchSortBy.DATE_DESC
                        }
                    }
                )
            }

            // Results List
            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching records found.",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { item ->
                        SearchResultRow(
                            result = item,
                            onClick = { onNavigateToResult(item.type.name, item.entityId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit
) {
    val dateStr = remember(result.timestamp) {
        if (result.timestamp > 0) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(result.timestamp)) else ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = FairShareTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.subtitle,
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (result.amountFormatted != null) {
                    Text(
                        text = result.amountFormatted,
                        style = FairShareTheme.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = FairShareTheme.colors.textPrimary
                    )
                }
                if (dateStr.isNotBlank()) {
                    Text(
                        text = dateStr,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}
