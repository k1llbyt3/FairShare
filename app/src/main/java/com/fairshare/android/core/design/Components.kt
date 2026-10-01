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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.window.Dialog

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
    val buttonModifier = modifier.defaultMinSize(minHeight = 48.dp)

    when (variant) {
        FSButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
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
                modifier = buttonModifier,
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
                modifier = buttonModifier,
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
fun FSConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        FSCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            backgroundColor = FairShareTheme.colors.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = FairShareTheme.typography.title,
                    color = if (isDestructive) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FSButton(
                        text = dismissText,
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    FSButton(
                        text = confirmText,
                        onClick = onConfirm,
                        variant = if (isDestructive) FSButtonVariant.Negative else FSButtonVariant.Primary
                    )
                }
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
        modifier = modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = FairShareTheme.shapes.card
            )
            .animateContentSize(),
        shape = FairShareTheme.shapes.card,
        color = backgroundColor
    ) {
        content()
    }
}

@Composable
fun FSStatusBadge(
    text: String,
    textColor: Color = FairShareTheme.colors.accent,
    backgroundColor: Color = FairShareTheme.colors.accentSoft,
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
