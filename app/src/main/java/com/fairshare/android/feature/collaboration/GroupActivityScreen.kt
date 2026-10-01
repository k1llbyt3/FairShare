package com.fairshare.android.feature.collaboration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.collaboration.GroupActivityItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActivityScreen(
    groupId: String,
    groupName: String,
    container: FairShareAppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToEntity: (entityType: String?, entityId: String?) -> Unit
) {
    val repo = container.collaborationRepository
    val activityFlow = remember(groupId) { repo.getActivitiesFlow(groupId) }
    val activities by activityFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Activity Timeline",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = groupName,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FairShareTheme.colors.surface
                )
            )
        },
        containerColor = FairShareTheme.colors.background
    ) { padding ->
        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No activity recorded yet.",
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities, key = { it.id }) { item ->
                    ActivityItemRow(
                        item = item,
                        onClick = {
                            if (item.entityId != null) {
                                onNavigateToEntity(item.entityType, item.entityId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityItemRow(
    item: GroupActivityItem,
    onClick: () -> Unit
) {
    val dateStr = remember(item.timestamp) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
            .clickable(enabled = item.entityId != null, onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.actorName,
                    style = FairShareTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = FairShareTheme.colors.textPrimary
                )
                Text(
                    text = dateStr,
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.summaryText,
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textSecondary
            )

            if (item.entityId != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to view ${item.entityType?.lowercase() ?: "record"}",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.accent
                )
            }
        }
    }
}
