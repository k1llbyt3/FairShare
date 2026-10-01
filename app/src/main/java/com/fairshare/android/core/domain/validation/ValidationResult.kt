package com.fairshare.android.core.domain.validation

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult() {
        val firstError: String
            get() = errors.firstOrNull() ?: "Validation failed"
    }

    val isValid: Boolean
        get() = this is Valid
}
