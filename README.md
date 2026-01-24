# The Intelligence Brief 🕵️‍♂️

**Zero Cost. Zero Ads. Pure Signal.**

A "Zero Rupee" intelligence tool designed for tech students and professionals. It scrapes high-signal technical news (AI, Cyber, Geopolitics), summarizes it using Google Gemini (AI), and delivers it to a distraction-free Android application.

---

## 🏗 Architecture

The system follows a **"Headless"** architecture to stay within free-tier limits:

1.  **Backend (The "Agent")**:
    *   **Python Scripts**: Runs on **GitHub Actions** (CRON_SCHEDULE: Every 12 hours).
    *   **Ingestion**: Fetches news from Google News RSS (Tier 2) and NewsData.io (Tier 1 Fallback).
    *   **Intelligence**: Uses **Google Gemini 1.5 Flash** to summarize and rate articles ("Trust Badge").
    *   **Database**: Pushes cleaned data to **Supabase (PostgreSQL)**.

2.  **Mobile App (The "Terminal")**:
    *   **Native Android (Kotlin + Jetpack Compose)**.
    *   **Direct-to-DB**: Connects directly to Supabase `public` schema (Read-Only) securely.
    *   **Features**: Dark Mode, Offline Caching, Interest filtering (India/World/Tech).

---

## 🚀 Setup Guide

### 1. Prerequisites
*   **Python 3.10+**
*   **Android Studio Ladybug (or newer)**
*   **Supabase Account** (Free Project)
*   **Google Gemini API Key** (Free)

### 2. Backend Setup
1.  Navigate to `backend/`.
2.  Create virtual environment:
    ```powershell
    python -m venv venv
    .\venv\Scripts\Activate
    ```
3.  Install dependencies:
    ```bash
    pip install -r requirements.txt
    ```
4.  Configure `.env` file in `backend/`:
    ```ini
    SUPABASE_URL=https://your-project-id.supabase.co
    SUPABASE_KEY=eyJ... (Use SERVICE_ROLE key)
    GEMINI_API_KEY=AIza...
    NEWSDATA_API_KEY=pub_... (Optional)
    ```
5.  **Initialize Database**:
    *   Go to Supabase Dashboard -> SQL Editor.
    *   Run the contents of `backend/schema.sql`.

### 3. Mobile App Setup
1.  Open valid `Android Studio`.
2.  Open `app/src/main/java/com/intelligence/brief/DataRepository.kt`.
3.  Update the Supabase Config:
    ```kotlin
    supabaseUrl = "https://your-project-id.supabase.co"
    supabaseKey = "eyJ..." // (Use ANON / PUBLIC key)
    ```
4.  Sync Gradle and Run 🟢.

---

## 🤖 Manual Operation
To trigger an update manually (outside of GitHub Actions):

```powershell
# In terminal
.\venv\Scripts\Activate
python backend/main.py
```

---

## 🛡️ License
MIT License. Built for educational purposes.
