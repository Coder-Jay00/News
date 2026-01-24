from push_notifier import send_update_notification

if __name__ == "__main__":
    print("Triggering App Update Notification...")
    # Sending notification for v1.0.1 pointing to the web mirror
    success = send_update_notification(
        version="v1.0.1", 
        download_url="https://brief-iota.vercel.app/Brief.apk"
    )
    if success:
        print("✅ Success: Update notification broadcast to all users.")
    else:
        print("❌ Failed: Check your firebase-service-account.json and credentials.")
