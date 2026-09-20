package com.fairshare.android.core.sync

/**
 * Synchronization Engine Contract.
 * Ensures offline actions survive and merge deterministically.
 * As defined in specs/Plan.md Phase 5.
 */
interface SyncContract {
    val pendingSyncCount: Int
        get() = 0
}
