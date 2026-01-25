from push_notifier import send_update_notification

if __name__ == "__main__":
    print("Triggering App Update Notification...")
    # Sending notification for v1.2.6 (Stabilization Fix)
    success = send_update_notification(
        version="v1.2.6", 
        download_url="https://github.com/Coder-Jay00/News/releases/latest/download/Brief.apk"
    )
    if success:
        print("✅ Success: Update notification broadcast to all users.")
    else:
        print("❌ Failed: Check your firebase-service-account.json and credentials.")
