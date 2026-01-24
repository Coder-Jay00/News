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

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "New Stories"
            val body = notification.body ?: "Check out the latest news"
            
            Log.d(TAG, "Notification - Title: $title, Body: $body")
            
            // Show local notification
            NotificationHelper.showNewArticlesNotification(
                context = this,
                articleCount = 0, // Not used when we have a custom message
                topHeadline = body
            )
        }

        // Check if message contains data payload (for custom handling)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            
            val title = remoteMessage.data["title"] ?: "New Stories"
            val body = remoteMessage.data["body"] ?: "Fresh news available"
            val count = remoteMessage.data["count"]?.toIntOrNull() ?: 1
            
            NotificationHelper.showNewArticlesNotification(
                context = this,
                articleCount = count,
                topHeadline = body
            )
        }
    }
}
