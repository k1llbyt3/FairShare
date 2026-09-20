package com.fairshare.android.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

enum class FSButtonVariant {
    Primary,
    Secondary,
    Negative
}

@Composable
fun FSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FSButtonVariant = FSButtonVariant.Primary,
    enabled: Boolean = true
) {
    when (variant) {
        FSButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = FairShareTheme.shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FairShareTheme.colors.accent,
                    contentColor = Color(0xFF101214),
                    disabledContainerColor = FairShareTheme.colors.disabled,
                    disabledContentColor = FairShareTheme.colors.textTertiary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = FairShareTheme.typography.section
                )
            }
        }
        FSButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = FairShareTheme.shapes.button,
                border = BorderStroke(1.dp, FairShareTheme.colors.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FairShareTheme.colors.surfaceElevated,
                    contentColor = FairShareTheme.colors.textPrimary,
                    disabledContainerColor = FairShareTheme.colors.surface,
                    disabledContentColor = FairShareTheme.colors.disabled
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = FairShareTheme.typography.body
                )
            }
        }
        FSButtonVariant.Negative -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = FairShareTheme.shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FairShareTheme.colors.negative,
                    contentColor = Color.White,
                    disabledContainerColor = FairShareTheme.colors.disabled,
                    disabledContentColor = FairShareTheme.colors.textTertiary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = FairShareTheme.typography.section
                )
            }
        }
    }
}

@Composable
fun FSCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = FairShareTheme.colors.surface,
    borderColor: Color = FairShareTheme.colors.border,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = borderColor,
            shape = FairShareTheme.shapes.card
        ),
        shape = FairShareTheme.shapes.card,
        color = backgroundColor
    ) {
        content()
    }
}

@Composable
fun FSStatusBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FairShareTheme.shapes.badge)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = FairShareTheme.typography.metadata,
            color = textColor
        )
    }
}
