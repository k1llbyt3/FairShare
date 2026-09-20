package com.fairshare.android.core.security

/**
 * Security and privacy architecture contract.
 * Preserves privacy restrictions (no plaintext banking data, secure local biometric/keystore).
 * As defined in specs/Plan.md Section 3.8.
 */
interface SecurityContract {
    val isBiometricsAvailable: Boolean
        get() = false
}
