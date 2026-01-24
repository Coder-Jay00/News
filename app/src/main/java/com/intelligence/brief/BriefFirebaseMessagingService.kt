package com.intelligence.brief

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service
 * Handles incoming push notifications from Firebase
 */
class BriefFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    /**
     * Called when a new FCM token is generated
     * This happens on first app launch and when token is refreshed
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // In production, you'd send this token to your backend
        // For now, we just log it
    }

    /**
     * Called when a push notification is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // 1. Prioritize Data Payload (for custom logic like Updates)
        if (data.isNotEmpty()) {
            val type = data["type"] ?: "news"
            val title = data["title"] ?: notification?.title ?: "New Update"
            val body = data["body"] ?: notification?.body ?: "Checking for latest brief..."
            val url = data["url"]

            if (type == "update" && url != null) {
                // Specially handle update notifications with the download URL
                NotificationHelper.showGenericNotification(this, title, body, url)
                return
            }
            
            // Default news handling
            val count = data["count"]?.toIntOrNull() ?: 0
            NotificationHelper.showNewArticlesNotification(this, count, body)
            return
        }

        // 2. Fallback to standard Notification Payload
        notification?.let { n ->
            val title = n.title ?: "Brief."
            val body = n.body ?: "New stories available"
            NotificationHelper.showGenericNotification(this, title, body)
        }
    }
}
