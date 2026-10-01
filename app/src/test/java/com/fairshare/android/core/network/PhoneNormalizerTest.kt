package com.fairshare.android.core.network

import com.fairshare.android.core.network.backend.auth.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun normalizeStandardIndianNumber_prependsCountryCode() {
        val normalized = PhoneNormalizer.normalize("9876543210")
        assertEquals("+919876543210", normalized)
    }

    @Test
    fun normalizeFormattedNumberWithSpacesAndDashes_cleansCorrectly() {
        val normalized = PhoneNormalizer.normalize("+91 98765-43210")
        assertEquals("+919876543210", normalized)
    }

    @Test
    fun normalizeInternationalUsNumber_preservesPlusCode() {
        val normalized = PhoneNormalizer.normalize("+1 (415) 555-2671")
        assertEquals("+14155552671", normalized)
    }

    @Test
    fun normalizeNumberWithParentheses_cleansCorrectly() {
        val normalized = PhoneNormalizer.normalize("(987) 654-3210")
        assertEquals("+919876543210", normalized)
    }

    @Test
    fun isValidE164_identifiesValidAndInvalidFormats() {
        assertTrue(PhoneNormalizer.isValidE164("+919876543210"))
        assertTrue(PhoneNormalizer.isValidE164("+14155552671"))
        assertFalse(PhoneNormalizer.isValidE164("9876543210")) // missing leading +
        assertFalse(PhoneNormalizer.isValidE164("invalid_phone"))
        assertFalse(PhoneNormalizer.isValidE164("+12")) // too short
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalizeBlankPhone_throwsException() {
        PhoneNormalizer.normalize("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalizeTooShortPhone_throwsException() {
        PhoneNormalizer.normalize("12345")
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalizeTooLongPhone_throwsException() {
        PhoneNormalizer.normalize("12345678901234567890")
    }
}
