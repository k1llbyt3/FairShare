package com.fairshare.android.core.database

/**
 * Local Room Database Architecture Contract.
 * Authoritative local cache for offline-first operation.
 * As defined in specs/Plan.md Phase 3.
 */
interface DatabaseContract {
    val version: Int
        get() = 1
}
