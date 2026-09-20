package com.fairshare.android.feature.receipt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fairshare.android.core.design.FairShareTheme

@Composable
fun ReceiptScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Receipt OCR", style = FairShareTheme.typography.title, color = FairShareTheme.colors.textPrimary)
    }
}
