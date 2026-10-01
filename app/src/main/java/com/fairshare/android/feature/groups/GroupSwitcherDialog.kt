package com.fairshare.android.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme

@Composable
fun GroupSwitcherDialog(
    groups: List<GroupEntity>,
    selectedGroupId: String,
    onSelectGroup: (GroupEntity) -> Unit,
    onCreateNewGroupClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onManageAllGroupsClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Switch Group",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = "View All →",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        modifier = Modifier.clickable {
                            onDismiss()
                            onManageAllGroupsClick()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select an active group or manage all groups.",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups) { group ->
                        val isSelected = group.id == selectedGroupId
                        FSCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectGroup(group)
                                    onDismiss()
                                },
                            backgroundColor = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.name,
                                        style = FairShareTheme.typography.section,
                                        color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary
                                    )
                                    if (group.description.isNotBlank()) {
                                        Text(
                                            text = group.description,
                                            style = FairShareTheme.typography.supporting,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text(
                                        text = "Active",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "+ New Group",
                        onClick = {
                            onDismiss()
                            onCreateNewGroupClick()
                        },
                        modifier = Modifier.weight(1f),
                        variant = FSButtonVariant.Primary
                    )
                    FSButton(
                        text = "Join with Code",
                        onClick = {
                            onDismiss()
                            onJoinGroupClick()
                        },
                        modifier = Modifier.weight(1f),
                        variant = FSButtonVariant.Secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FSButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    variant = FSButtonVariant.Secondary
                )
            }
        }
    }
}
