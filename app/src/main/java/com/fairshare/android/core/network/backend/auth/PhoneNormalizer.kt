package com.fairshare.android.core.network.backend.auth

/**
 * Phone number normalization and validation to ITU-T E.164 standard.
 * Required by specs/PRD.md FR-AUTH-01.
 */
object PhoneNormalizer {

    /**
     * Normalizes a phone number to E.164 international format (e.g. +919876543210).
     * @param rawPhone User-entered phone number (with or without +, spaces, hyphens).
     * @param defaultCountryCode Default ISO country dialing code (e.g. "+91" for India).
     * @return E.164 normalized phone number string starting with '+'.
     * @throws IllegalArgumentException If the phone number is invalid.
     */
    fun normalize(rawPhone: String, defaultCountryCode: String = "+91"): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Phone number cannot be empty")
        }

        // Clean non-digits except initial '+'
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.length < 7 || digitsOnly.length > 15) {
            throw IllegalArgumentException("Invalid phone number length: must be between 7 and 15 digits (E.164)")
        }

        val normalized = if (hasLeadingPlus) {
            "+$digitsOnly"
        } else {
            val cleanCode = defaultCountryCode.trim().removePrefix("+").filter { it.isDigit() }
            if (digitsOnly.startsWith(cleanCode) && digitsOnly.length > cleanCode.length + 6) {
                "+$digitsOnly"
            } else {
                "+$cleanCode$digitsOnly"
            }
        }

        // Validate complete E.164 length
        val totalDigits = normalized.removePrefix("+")
        if (totalDigits.length < 8 || totalDigits.length > 15) {
            throw IllegalArgumentException("Normalized phone number does not conform to E.164")
        }

        return normalized
    }

    /**
     * Checks whether a phone string conforms to E.164 format.
     */
    fun isValidE164(phone: String): Boolean {
        return try {
            val norm = normalize(phone)
            norm == phone
        } catch (_: Exception) {
            false
        }
    }
}
