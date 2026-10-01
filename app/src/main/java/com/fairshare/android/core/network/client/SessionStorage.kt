package com.fairshare.android.core.network.client

import android.content.Context
import android.content.SharedPreferences

/**
 * Production-safe session storage.
 * Stores auth session tokens, user ID, and phone number securely.
 * Cleared on logout as required by specs/PRD.md FR-AUTH-04.
 */
class SessionStorage(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // In-memory fallback for test harnesses or isolated contexts
    private var memToken: String? = null
    private var memUserId: String? = null
    private var memPhone: String? = null

    var sessionToken: String?
        get() = prefs?.getString(KEY_TOKEN, null) ?: memToken
        set(value) {
            memToken = value
            prefs?.edit()?.let {
                if (value == null) it.remove(KEY_TOKEN) else it.putString(KEY_TOKEN, value)
                it.apply()
            }
        }

    var currentUserId: String?
        get() = prefs?.getString(KEY_USER_ID, null) ?: memUserId
        set(value) {
            memUserId = value
            prefs?.edit()?.let {
                if (value == null) it.remove(KEY_USER_ID) else it.putString(KEY_USER_ID, value)
                it.apply()
            }
        }

    var userPhone: String?
        get() = prefs?.getString(KEY_PHONE, null) ?: memPhone
        set(value) {
            memPhone = value
            prefs?.edit()?.let {
                if (value == null) it.remove(KEY_PHONE) else it.putString(KEY_PHONE, value)
                it.apply()
            }
        }

    val hasSession: Boolean
        get() = !sessionToken.isNullOrBlank() && !currentUserId.isNullOrBlank()

    fun saveSession(token: String, userId: String, phone: String) {
        this.sessionToken = token
        this.currentUserId = userId
        this.userPhone = phone
    }

    fun clear() {
        memToken = null
        memUserId = null
        memPhone = null
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        private const val PREF_NAME = "fairshare_auth_session"
        private const val KEY_TOKEN = "auth_session_token"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_PHONE = "auth_user_phone"
    }
}
