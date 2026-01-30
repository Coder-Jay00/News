"""
Firebase Cloud Messaging (FCM) Push Notification Sender

Sends push notifications to all Brief. app users when new articles are available.
Uses Firebase Admin SDK with service account authentication.
"""

import os
try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    FIREBASE_AVAILABLE = True
except ImportError:
    print("[FCM] WARNING: firebase_admin module not found. Notifications disabled.")
    FIREBASE_AVAILABLE = False

# Path to service account JSON (relative to this file)
SERVICE_ACCOUNT_PATH = os.path.join(os.path.dirname(__file__), "firebase-service-account.json")

# Initialize Firebase Admin SDK (only once)
_firebase_initialized = False

def init_firebase():
    """Initialize Firebase Admin SDK if not already done"""
    global _firebase_initialized
    if not FIREBASE_AVAILABLE:
        return False

    if not _firebase_initialized:
        try:
            cred = credentials.Certificate(SERVICE_ACCOUNT_PATH)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            print("[FCM] Firebase initialized successfully")
        except Exception as e:
            print(f"[FCM] Firebase init failed: {e}")
            return False
    return True


def send_news_notification(article_count: int, top_headline: str):
    """
    Send a push notification to the 'news' topic.
    All app users subscribed to 'news' will receive this.
    
    Args:
        article_count: Number of new articles
        top_headline: The most important headline to show
    """
    if not init_firebase():
        print("[FCM] Skipping notification - Firebase not initialized")
        return False
    
    try:
        # Build the notification message
        message = messaging.Message(
            notification=messaging.Notification(
                title=f"📰 {article_count} new stories",
                body=top_headline[:100]  # Limit body length
            ),
            data={
                "count": str(article_count),
                "headline": top_headline
            },
            # Send to all users subscribed to 'news' topic
            topic="news",
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="brief_news_channel",
                    priority="high",
                    default_sound=True,
                    default_vibrate_timings=True
                )
            )
        )
        
        # Send the message
        response = messaging.send(message)
        print(f"[FCM] Notification sent successfully: {response}")
        return True
        
    except Exception as e:
        print(f"[FCM] Failed to send notification: {e}")
        return False


def subscribe_token_to_news(fcm_token: str):
    """
    Subscribe a device token to the 'news' topic.
    Called when a new user installs the app.
    """
    if not init_firebase():
        return False
    
    try:
        response = messaging.subscribe_to_topic([fcm_token], "news")
        print(f"[FCM] Token subscribed to news topic: {response.success_count} success")
        return True
    except Exception as e:
        print(f"[FCM] Topic subscription failed: {e}")
        return False
def send_update_notification(version: str, download_url: str = "https://github.com/Coder-Jay00/News/releases/latest/download/Brief.apk"):
    """
    Send a push notification informing users of a new app version.
    """
    if not init_firebase():
        return False
    
    try:
        # We use a DATA payload only for updates to force the app to use our
        # custom notification handler which handles deep-linking to the URL.
        message = messaging.Message(
            data={
                "type": "update",
                "version": version,
                "url": download_url,
                "title": "🚀 New Update Available!",
                "body": f"Brief. {version} is now available. Click to download and stay up to date."
            },
            topic="news",
            android=messaging.AndroidConfig(
                priority="high"
            )
        )
        response = messaging.send(message)
        print(f"[FCM] Update notification sent: {response}")
        return True
    except Exception as e:
        print(f"[FCM] Failed to send update notification: {e}")
        return False

def send_targeted_notification(token: str, title: str, body: str):
    """
    Feature 10: Send a specific notification to a specific user (Watchlist Alert).
    """
    if not init_firebase(): return False
    
    try:
        message = messaging.Message(
            token=token, # Target specific device
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="brief_alerts_channel", # Separate channel for alerts
                    priority="high"
                )
            )
        )
        response = messaging.send(message)
        print(f"[FCM] Watchlist Alert sent to {token[:10]}...: {response}")
        return True
    except Exception as e:
        print(f"[FCM] Failed to send targeted notification: {e}")
        return False
