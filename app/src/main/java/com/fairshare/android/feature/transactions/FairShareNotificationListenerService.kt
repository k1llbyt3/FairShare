package com.fairshare.android.feature.transactions

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.domain.transactions.CandidateSource
import com.fairshare.android.core.domain.transactions.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FairShareNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val PREF_TRANSACTION_DETECTION_KEY = "fairshare_transaction_detection_enabled"

        fun isDetectionEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("fairshare_privacy_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_TRANSACTION_DETECTION_KEY, false)
        }

        fun setDetectionEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("fairshare_privacy_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_TRANSACTION_DETECTION_KEY, enabled).apply()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!isDetectionEnabled(applicationContext)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val rawCombined = "$title $text $bigText".trim()
        if (rawCombined.isBlank()) return

        val candidate = TransactionParser.parse(
            rawText = rawCombined,
            source = CandidateSource.NOTIFICATION,
            timestamp = sbn.postTime
        ) ?: return

        scope.launch {
            try {
                val container = FairShareAppContainer.getInstance(applicationContext)
                container.candidateRepository.saveCandidate(candidate)
            } catch (_: Exception) {
                // Ignore transient failures in listener
            }
        }
    }
}
