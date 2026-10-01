package com.fairshare.android.feature.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.sync.SyncConflictRecord
import com.fairshare.android.core.sync.SyncEntityType

/**
 * Responsive Conflict Resolution Dialog.
 * Required by specs/Plan.md Task D and specs/PRD.md Section 91.
 * Complies strictly with Design.md (flat styling, Roboto, no gradients, no AI slop).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionDialog(
    conflict: SyncConflictRecord,
    onKeepLocal: (String, SyncEntityType) -> Unit,
    onAcceptRemote: (String, SyncEntityType) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FairShareTheme.shapes.dialog)
                .background(FairShareTheme.colors.surface)
                .border(1.dp, FairShareTheme.colors.negative, FairShareTheme.shapes.dialog)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FINANCIAL CONFLICT",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative,
                        fontWeight = FontWeight.Bold
                    )
                    FSStatusBadge(
                        text = conflict.entityType.name,
                        textColor = FairShareTheme.colors.accent,
                        backgroundColor = FairShareTheme.colors.accentSoft
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Concurrent Modification Detected",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This financial record was edited on another device while changes were pending on this device. To protect financial integrity, choose which version to preserve. Historical records are kept in the audit log.",
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Comparative Cards
                // 1. Local Draft
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LOCAL DRAFT (THIS DEVICE)",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent
                            )
                            Text(
                                text = "Rev ${conflict.localRevision}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = conflict.localSummary.ifBlank { "Local record" },
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Remote Canonical
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SERVER VERSION (CANONICAL)",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.positive
                            )
                            Text(
                                text = "Rev ${conflict.remoteRevision}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = conflict.remoteSummary.ifBlank { "Remote record" },
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Resolution Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FSButton(
                        text = "Keep Local Changes",
                        onClick = {
                            onKeepLocal(conflict.entityId, conflict.entityType)
                            onDismiss()
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FSButton(
                        text = "Accept Server Version",
                        onClick = {
                            onAcceptRemote(conflict.entityId, conflict.entityType)
                            onDismiss()
                        },
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Review Later",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
