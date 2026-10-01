package com.fairshare.android.feature.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fairshare.android.MainActivity
import com.fairshare.android.core.domain.summary.DailySummaryFacts

object DailyBriefNotificationManager {

    private const val CHANNEL_ID = "fairshare_daily_brief"
    private const val CHANNEL_NAME = "Daily Brief & Budget Alerts"
    private const val CHANNEL_DESC = "Notifications for daily spending summaries and budget runway alerts"
    private const val PREFS_NAME = "fairshare_daily_brief_prefs"
    private const val PREF_ENABLED = "daily_brief_enabled"
    private const val PREF_REMINDER_HOUR = "daily_brief_reminder_hour"
    private const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun isDailyReminderEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ENABLED, true)
    }

    fun setDailyReminderEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    fun getDailyReminderHour(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_REMINDER_HOUR, 21) // 9:00 PM default
    }

    fun setDailyReminderHour(context: Context, hour: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_REMINDER_HOUR, hour).apply()
    }

    fun postDailyBriefNotification(
        context: Context,
        tripName: String,
        summary: DailySummaryFacts
    ) {
        if (!isDailyReminderEnabled(context)) return

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "DAILY_BRIEF")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val contentText = buildString {
            append("${summary.totalSpentToday.formatted()} spent today across ${summary.expenseCountToday} expense${if (summary.expenseCountToday == 1) "" else "s"}.")
            if (summary.plannedDailySpending != null) {
                append(" Daily target: ${summary.plannedDailySpending.formatted()}.")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FairShare: $tripName Daily Brief")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not granted; fail silently without crashing
        }
    }
}
