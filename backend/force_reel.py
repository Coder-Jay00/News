import os
import datetime
import json
from dotenv import load_dotenv
from database_manager import DatabaseManager

if os.path.exists("backend/.env"):
    load_dotenv("backend/.env")
else:
    load_dotenv()

def force_reel():
    print("--- FORCING MORNING REEL GENERATION ---")
    db = DatabaseManager()
    
    # 1. Fetch Top 3 Articles (High Trust)
    # We select articles published in the last 24h preferably, but just top 3 latest High Score is good.
    res = db.client.table("articles").select("*").order("trust_score", desc=True).limit(5).execute()
    articles = res.data
    
    if not articles:
        print("CRITICAL: No articles in DB to make a reel!")
        return

    # Filter for quality
    top_3 = articles[:3]
    
    # 2. Construct Reel Content with NEW Title
    today_str_pretty = datetime.datetime.now().strftime('%b %d')
    title = f"Daily Intelligence Reel • {today_str_pretty}"
    
    reel_content = {
        "title": title,
        "summary": "Your daily high-signal update (Manual Sync).",
        "stories": top_3
    }
    
    print(f"Generated Content for {title}")
    
    # 3. Save to DB
    today_iso = datetime.datetime.now().strftime("%Y-%m-%d")
    data = {
        "date_str": today_iso,
        "content": reel_content
    }
    
    try:
        db.client.table("daily_briefings").upsert(data, on_conflict="date_str").execute()
        print(f"SUCCESS: Upserted Morning Reel for {today_iso}")
    except Exception as e:
        print(f"ERROR: {e}")

if __name__ == "__main__":
    force_reel()
