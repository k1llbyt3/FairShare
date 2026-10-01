package com.fairshare.android.core.network

import com.fairshare.android.core.network.client.FairShareApiClient

/**
 * Network layer contract for backend communication and synchronization.
 * Authoritative cloud state for shared multi-device groups.
 * As defined in specs/Plan.md Phase 4 and specs/PRD.md Section 68.
 */
interface NetworkContract {
    val isOnline: Boolean
        get() = true

    val apiClient: FairShareApiClient?
        get() = null
}
