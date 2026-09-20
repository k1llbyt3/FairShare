package com.fairshare.android.core.network

/**
 * Network layer contract for backend synchronization.
 * Authoritative cloud state for shared multi-device groups.
 * As defined in specs/Plan.md Phase 4.
 */
interface NetworkContract {
    val isOnline: Boolean
        get() = false
}
