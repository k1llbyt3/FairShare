package com.fairshare.android.feature.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.network.backend.auth.PhoneNormalizer
import com.fairshare.android.core.network.backend.auth.VerificationTicket
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.AuthRepository
import kotlinx.coroutines.delay
import java.util.Locale

data class CountryDialInfo(
    val isoCode: String,
    val dialCode: String,
    val name: String,
    val flag: String,
    val minDigits: Int = 7,
    val maxDigits: Int = 15
)

val SUPPORTED_COUNTRIES = listOf(
    CountryDialInfo("IN", "+91", "India", "🇮🇳", minDigits = 10, maxDigits = 10),
    CountryDialInfo("US", "+1", "United States", "🇺🇸", minDigits = 10, maxDigits = 10),
    CountryDialInfo("GB", "+44", "United Kingdom", "🇬🇧", minDigits = 10, maxDigits = 10),
    CountryDialInfo("CA", "+1", "Canada", "🇨🇦", minDigits = 10, maxDigits = 10),
    CountryDialInfo("AU", "+61", "Australia", "🇦🇺", minDigits = 9, maxDigits = 9),
    CountryDialInfo("DE", "+49", "Germany", "🇩🇪", minDigits = 10, maxDigits = 11),
    CountryDialInfo("AE", "+971", "United Arab Emirates", "🇦🇪", minDigits = 9, maxDigits = 9),
    CountryDialInfo("SG", "+65", "Singapore", "🇸🇬", minDigits = 8, maxDigits = 8),
    CountryDialInfo("FR", "+33", "France", "🇫🇷", minDigits = 9, maxDigits = 9),
    CountryDialInfo("JP", "+81", "Japan", "🇯🇵", minDigits = 10, maxDigits = 10),
    CountryDialInfo("SA", "+966", "Saudi Arabia", "🇸🇦", minDigits = 9, maxDigits = 9),
    CountryDialInfo("QA", "+974", "Qatar", "🇶🇦", minDigits = 8, maxDigits = 8),
    CountryDialInfo("MY", "+60", "Malaysia", "🇲🇾", minDigits = 9, maxDigits = 10),
    CountryDialInfo("NP", "+977", "Nepal", "🇳🇵", minDigits = 10, maxDigits = 10),
    CountryDialInfo("LK", "+94", "Sri Lanka", "🇱🇰", minDigits = 9, maxDigits = 9),
    CountryDialInfo("NZ", "+64", "New Zealand", "🇳🇿", minDigits = 8, maxDigits = 10),
    CountryDialInfo("IE", "+353", "Ireland", "🇮🇪", minDigits = 9, maxDigits = 9),
    CountryDialInfo("NL", "+31", "Netherlands", "🇳🇱", minDigits = 9, maxDigits = 9),
    CountryDialInfo("CH", "+41", "Switzerland", "🇨🇭", minDigits = 9, maxDigits = 9)
)

enum class AuthStep {
    REGISTRATION_INPUT,
    CODE_INPUT,
    CURRENCY_PREFERENCE
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(AuthStep.REGISTRATION_INPUT) }

    // Auto-detect default country from device locale
    val defaultCountry = remember {
        val iso = Locale.getDefault().country
        SUPPORTED_COUNTRIES.find { it.isoCode.equals(iso, ignoreCase = true) }
            ?: SUPPORTED_COUNTRIES.first()
    }

    var selectedCountry by remember { mutableStateOf(defaultCountry) }
    var showCountryDialog by remember { mutableStateOf(false) }

    var phoneNumber by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("INR") }
    var devOtpReceived by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var countdownSeconds by remember { mutableIntStateOf(0) }

    // Resend countdown timer
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Header
            Text(
                text = "FAIRSHARE",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.accent,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            when (step) {
                AuthStep.REGISTRATION_INPUT -> {
                    Text(
                        text = "Sign in or Register",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zero-friction group expense splitting and fair settlements with offline resilience.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    // Full Name Input
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            errorMessage = null
                            displayName = it
                        },
                        label = { Text("Your Name", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. Rahul Sharma", color = FairShareTheme.colors.disabled) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
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

                    Spacer(modifier = Modifier.height(14.dp))

                    val isPhoneValid = remember(phoneNumber, selectedCountry) {
                        if (selectedCountry.dialCode == "+91") {
                            phoneNumber.length == 10
                        } else {
                            phoneNumber.length in selectedCountry.minDigits..selectedCountry.maxDigits
                        }
                    }

                    // Phone Input Form with Country Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Country Code Selector Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                            modifier = Modifier
                                .height(56.dp)
                                .clickable { showCountryDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Country",
                                    tint = FairShareTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Local Phone Number Input
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                errorMessage = null
                                // Normalize spaces, dashes, parentheses
                                val cleanDigits = input.filter { it.isDigit() }
                                val dialDigits = selectedCountry.dialCode.removePrefix("+")
                                // If user accidentally typed or pasted country code at the beginning, strip it
                                val sanitized = if (cleanDigits.startsWith(dialDigits) && cleanDigits.length > dialDigits.length + 4) {
                                    cleanDigits.removePrefix(dialDigits)
                                } else {
                                    cleanDigits
                                }.take(selectedCountry.maxDigits)
                                phoneNumber = sanitized
                            },
                            label = { 
                                val labelText = if (selectedCountry.dialCode == "+91") "Mobile Number (10 digits)" else "Mobile Number"
                                Text(labelText, color = FairShareTheme.colors.textSecondary) 
                            },
                            placeholder = { 
                                val hint = if (selectedCountry.dialCode == "+91") "9876543210" else "e.g. 9876543210"
                                Text(hint, color = FairShareTheme.colors.disabled) 
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (displayName.trim().length >= 2 && isPhoneValid && !isLoading) {
                                        sendCode(selectedCountry.dialCode, phoneNumber, authRepository, { ticket ->
                                            devOtpReceived = ticket.devOtp
                                            step = AuthStep.CODE_INPUT
                                            countdownSeconds = 60
                                        }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.accent,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary,
                                focusedContainerColor = FairShareTheme.colors.surface,
                                unfocusedContainerColor = FairShareTheme.colors.surface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMessage != null) {
                        ErrorBanner(message = errorMessage!!)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val canContinue = displayName.trim().length >= 2 && isPhoneValid && !isLoading
                    FSButton(
                        text = if (isLoading) "Sending code..." else "Continue",
                        onClick = {
                            errorMessage = null
                            sendCode(selectedCountry.dialCode, phoneNumber, authRepository, { ticket ->
                                devOtpReceived = ticket.devOtp
                                step = AuthStep.CODE_INPUT
                                countdownSeconds = 60
                            }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                        },
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AuthStep.CODE_INPUT -> {
                    Text(
                        text = "Verify Mobile Number",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We sent a 6-digit verification code to ${selectedCountry.dialCode} $phoneNumber",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(onClick = {
                        step = AuthStep.REGISTRATION_INPUT
                        verificationCode = ""
                        errorMessage = null
                    }) {
                        Text(
                            text = "Change phone number",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Isolated Development Mode OTP Helper Card (Disabled in production)
                    if (devOtpReceived != null) {
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.accentSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.accent.copy(alpha = 0.5f), FairShareTheme.shapes.card)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DEV OTP CODE",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = devOtpReceived!!,
                                        style = FairShareTheme.typography.title,
                                        color = FairShareTheme.colors.textPrimary,
                                        letterSpacing = 2.sp
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        verificationCode = devOtpReceived!!
                                    }
                                ) {
                                    Text(
                                        text = "Quick Fill",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = {
                            errorMessage = null
                            val digits = it.filter { char -> char.isDigit() }
                            if (digits.length <= 6) {
                                verificationCode = digits
                                if (verificationCode.length == 6 && !isLoading) {
                                    verifyAndProceed(selectedCountry.dialCode, phoneNumber, verificationCode, displayName, selectedCurrency, authRepository, { isNew ->
                                        if (isNew) step = AuthStep.CURRENCY_PREFERENCE else onAuthSuccess()
                                    }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                                }
                            }
                        },
                        label = { Text("6-Digit Code", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("123456", color = FairShareTheme.colors.disabled) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (verificationCode.length == 6 && !isLoading) {
                                    verifyAndProceed(selectedCountry.dialCode, phoneNumber, verificationCode, displayName, selectedCurrency, authRepository, { isNew ->
                                        if (isNew) step = AuthStep.CURRENCY_PREFERENCE else onAuthSuccess()
                                    }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                                }
                            }
                        ),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (countdownSeconds > 0) "Resend in ${countdownSeconds}s" else "Didn't receive code?",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        TextButton(
                            onClick = {
                                errorMessage = null
                                sendCode(selectedCountry.dialCode, phoneNumber, authRepository, { ticket ->
                                    devOtpReceived = ticket.devOtp
                                    countdownSeconds = 60
                                }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                            },
                            enabled = countdownSeconds == 0 && !isLoading
                        ) {
                            Text(
                                text = "Resend Code",
                                style = FairShareTheme.typography.metadata,
                                color = if (countdownSeconds == 0) FairShareTheme.colors.accent else FairShareTheme.colors.disabled,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        ErrorBanner(message = errorMessage!!)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    FSButton(
                        text = if (isLoading) "Verifying..." else "Verify & Sign In",
                        onClick = {
                            errorMessage = null
                            verifyAndProceed(selectedCountry.dialCode, phoneNumber, verificationCode, displayName, selectedCurrency, authRepository, { isNew ->
                                if (isNew) step = AuthStep.CURRENCY_PREFERENCE else onAuthSuccess()
                            }, { msg -> errorMessage = msg }, { l -> isLoading = l })
                        },
                        enabled = verificationCode.length == 6 && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AuthStep.CURRENCY_PREFERENCE -> {
                    Text(
                        text = "Welcome, ${displayName.trim()}!",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select your preferred currency for expense summaries and settlements.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "DEFAULT CURRENCY",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("INR" to "₹ INR", "USD" to "$ USD", "EUR" to "€ EUR", "GBP" to "£ GBP").forEach { (code, label) ->
                            val isSelected = selectedCurrency == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(FairShareTheme.shapes.button)
                                    .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                        shape = FairShareTheme.shapes.button
                                    )
                                    .clickable { selectedCurrency = code }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = FairShareTheme.typography.supporting,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (errorMessage != null) {
                        ErrorBanner(message = errorMessage!!)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    FSButton(
                        text = if (isLoading) "Saving..." else "Get Started",
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            val result = authRepository.updateProfile(displayName.ifBlank { "User" }, selectedCurrency)
                            isLoading = false
                            when (result) {
                                is NetworkResult.Success -> onAuthSuccess()
                                is NetworkResult.Error -> errorMessage = result.message
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Country Picker Dialog
    if (showCountryDialog) {
        CountryPickerDialog(
            countries = SUPPORTED_COUNTRIES,
            onSelect = { country ->
                selectedCountry = country
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }
}

@Composable
fun CountryPickerDialog(
    countries: List<CountryDialInfo>,
    onSelect: (CountryDialInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, countries) {
        if (searchQuery.isBlank()) countries
        else countries.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.dialCode.contains(searchQuery) ||
            it.isoCode.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Country",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = FairShareTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or code...", color = FairShareTheme.colors.disabled) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FairShareTheme.colors.textSecondary
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

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(filtered) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelect(country) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = country.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = country.name,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                            Text(
                                text = country.dialCode,
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = FairShareTheme.colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = FairShareTheme.shapes.card,
        color = FairShareTheme.colors.negativeSoft,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FairShareTheme.colors.negative.copy(alpha = 0.5f), FairShareTheme.shapes.card)
    ) {
        Text(
            text = message,
            style = FairShareTheme.typography.supporting,
            color = FairShareTheme.colors.negative,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun sendCode(
    countryCode: String,
    phoneNumber: String,
    repository: AuthRepository,
    onSuccess: (VerificationTicket) -> Unit,
    onError: (String) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    try {
        val cleanLocal = phoneNumber.filter { it.isDigit() }
        val cleanDial = countryCode.trim()
        val fullPhone = if (cleanLocal.startsWith(cleanDial.removePrefix("+"))) {
            "+$cleanLocal"
        } else {
            "$cleanDial$cleanLocal"
        }
        val normalized = PhoneNormalizer.normalize(fullPhone, cleanDial)
        onLoading(true)
        val result = repository.requestVerification(normalized)
        onLoading(false)
        when (result) {
            is NetworkResult.Success -> onSuccess(result.data)
            is NetworkResult.Error -> onError(result.message)
        }
    } catch (e: Exception) {
        onLoading(false)
        onError(e.message ?: "Invalid phone number format")
    }
}

private fun verifyAndProceed(
    countryCode: String,
    phoneNumber: String,
    code: String,
    displayName: String,
    currency: String,
    repository: AuthRepository,
    onProceed: (isNewUser: Boolean) -> Unit,
    onError: (String) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    try {
        val cleanLocal = phoneNumber.filter { it.isDigit() }
        val cleanDial = countryCode.trim()
        val fullPhone = if (cleanLocal.startsWith(cleanDial.removePrefix("+"))) {
            "+$cleanLocal"
        } else {
            "$cleanDial$cleanLocal"
        }
        val normalized = PhoneNormalizer.normalize(fullPhone, cleanDial)
        onLoading(true)
        val result = repository.verifyCode(normalized, code, displayName.ifBlank { null }, currency)
        onLoading(false)
        when (result) {
            is NetworkResult.Success -> {
                val isNew = result.data.displayName.startsWith("User ")
                onProceed(isNew)
            }
            is NetworkResult.Error -> onError(result.message)
        }
    } catch (e: Exception) {
        onLoading(false)
        onError(e.message ?: "Verification failed")
    }
}
