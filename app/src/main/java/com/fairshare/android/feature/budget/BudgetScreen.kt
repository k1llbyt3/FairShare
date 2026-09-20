package com.fairshare.android.feature.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fairshare.android.core.design.FairShareTheme

@Composable
fun BudgetScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Trip Budget", style = FairShareTheme.typography.title, color = FairShareTheme.colors.textPrimary)
    }
}
