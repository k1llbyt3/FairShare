package com.fairshare.android.feature.evidence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.evidence.EvidenceAttachment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceViewerDialog(
    attachment: EvidenceAttachment,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(attachment.createdAt))
    val sizeKb = if (attachment.sizeBytes > 0) "${attachment.sizeBytes / 1024} KB" else "Evidence file"

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Receipt & Evidence",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(FairShareTheme.colors.surfaceElevated, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = attachment.fileName,
                            style = FairShareTheme.typography.body,
                            fontWeight = FontWeight.Bold,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${attachment.mimeType} • $sizeKb",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Attached: $dateStr",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Text(
                    text = "Local Path: Verified on-device evidence",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textTertiary
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onDelete != null) {
                        FSButton(
                            text = "Delete",
                            onClick = onDelete
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    FSButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
