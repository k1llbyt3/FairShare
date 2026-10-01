package com.fairshare.android.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.AuthRepository

@Composable
fun ProfileScreen(
    user: BackendUser,
    authRepository: AuthRepository,
    onBackClick: () -> Unit,
    onLogoutSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember(user) { mutableStateOf(user.displayName) }
    var defaultCurrency by remember(user) { mutableStateOf(user.defaultCurrency) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val initials = user.displayName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "FS" }
        .uppercase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Account",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Profile header ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(FairShareTheme.colors.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.phoneNumber,
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(FairShareTheme.shapes.badge)
                        .background(FairShareTheme.colors.positiveSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VERIFIED",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.positive
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Edit profile section ─────────────────────────────────────────
            SettingsSectionHeader(title = "Profile")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        saveMessage = null
                        errorMessage = null
                    },
                    label = {
                        Text(
                            "Display Name",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedContainerColor = FairShareTheme.colors.surface,
                        unfocusedContainerColor = FairShareTheme.colors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Currency selector
                Text(
                    text = "Default Currency",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£").forEach { (code, symbol) ->
                        val isSelected = defaultCurrency == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FairShareTheme.shapes.button)
                                .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    FairShareTheme.shapes.button
                                )
                                .clickable { defaultCurrency = code }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = symbol,
                                    style = FairShareTheme.typography.section,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = code,
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textTertiary
                                )
                            }
                        }
                    }
                }

                if (saveMessage != null) {
                    Text(
                        text = saveMessage!!,
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.positive
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.negative
                    )
                }

                val hasChanges = displayName.isNotBlank() &&
                        (displayName != user.displayName || defaultCurrency != user.defaultCurrency)
                FSButton(
                    text = if (isSaving) "Saving..." else "Save Changes",
                    onClick = {
                        if (displayName.isNotBlank()) {
                            isSaving = true
                            val result = authRepository.updateProfile(displayName, defaultCurrency)
                            isSaving = false
                            when (result) {
                                is NetworkResult.Success -> {
                                    saveMessage = "Profile updated."
                                    errorMessage = null
                                }
                                is NetworkResult.Error -> {
                                    errorMessage = result.message
                                    saveMessage = null
                                }
                            }
                        }
                    },
                    enabled = hasChanges && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Account info section ─────────────────────────────────────────
            SettingsSectionHeader(title = "Account")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
            ) {
                SettingsRow(
                    label = "User ID",
                    value = user.id.take(12) + "…"
                )
                HorizontalDivider(
                    color = FairShareTheme.colors.border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                SettingsRow(
                    label = "Account Status",
                    value = "Active"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Session / Logout ─────────────────────────────────────────────
            SettingsSectionHeader(title = "Session")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign Out",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.negative
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FairShareTheme.colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Sign Out",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out? Your local data will be cleared.",
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authRepository.logout()
                    onLogoutSuccess()
                }) {
                    Text(
                        text = "Sign Out",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.negative
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
            },
            containerColor = FairShareTheme.colors.surfaceElevated,
            shape = FairShareTheme.shapes.dialog
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = FairShareTheme.typography.metadata,
        color = FairShareTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = FairShareTheme.typography.body,
            color = FairShareTheme.colors.textPrimary
        )
        Text(
            text = value,
            style = FairShareTheme.typography.supporting,
            color = FairShareTheme.colors.textSecondary
        )
    }
}
