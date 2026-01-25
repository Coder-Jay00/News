package com.intelligence.brief

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import java.util.TimeZone
import java.util.Locale

/**
 * Simple notification helper for Brief. news alerts
 */
object NotificationHelper {
    
    private const val CHANNEL_ID = "brief_news_channel"
    private const val CHANNEL_NAME = "News Alerts"
    private const val CHANNEL_DESC = "Get notified when new stories arrive"
    
    /**
     * Create notification channel (required for Android 8+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Check if we have notification permission (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required pre-Android 13
        }
    }
    
    /**
     * Show a generic notification with a custom title and optional URL
     */
    fun showGenericNotification(context: Context, title: String, body: String, url: String? = null) {
        if (!hasNotificationPermission(context)) return
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!url.isNullOrEmpty()) {
                putExtra("url", url)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            if (url != null) System.currentTimeMillis().toInt() else 0, 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Show a notification for new articles
     */
    fun showNewArticlesNotification(context: Context, articleCount: Int, topHeadline: String) {
        val title = if (articleCount > 0) "📰 $articleCount new stories" else "📰 Daily Brief Update"
        showGenericNotification(context, title, topHeadline)
    }
}
