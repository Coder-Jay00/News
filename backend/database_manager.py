from supabase import create_client, Client
import os
import datetime
from typing import List, Dict
from dotenv import load_dotenv

class DatabaseManager:
    def __init__(self):
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_KEY")
        
        if not url or "YOUR_" in url:
            print("ERROR: SUPABASE_URL is missing or contains a placeholder!")
            self.client = None
        elif not key or "YOUR_" in key:
            print("ERROR: SUPABASE_KEY is missing or contains a placeholder!")
            self.client = None
        else:
            print(f"Connecting to Supabase at: {url[:25]}...")
            self.client: Client = create_client(url, key)

    def upload_batch(self, articles: List[Dict]):
        """Uploads a batch of processed articles to 'articles' table."""
        if not self.client:
            print("Upload Skipped: Supabase client not initialized.")
            return
        if not articles:
            print("Upload Skipped: No articles to upload.")
            return
        
        print(f"Uploading {len(articles)} articles to Supabase...")
        try:
            # Upsert based on 'link'
            self.client.table("articles").upsert(articles, on_conflict="link").execute()
            print("--- DATABASE UPLOAD SUCCESSFUL ---")
        except Exception as e:
            print(f"!!! DATABASE UPLOAD FAILED !!! Error: {e}")
            raise e # Raise to fail the GitHub Action so we see it

    def purge_old_data(self, hours=48):
        """Deletes articles older than X hours."""
        if not self.client:
            return
        
        limit_date = (datetime.datetime.now() - datetime.timedelta(hours=hours)).isoformat()
        print(f"Purging articles older than {hours} hours...")
        try:
            self.client.table("articles").delete().lt("published", limit_date).execute()
            print("Purge complete.")
        except Exception as e:
            print(f"Purge error: {e}")

    def get_latest_batch(self, limit=15) -> List[Dict]:
        """Fetches the latest articles for the app (Testing purpose)."""
        if not self.client:
            return []
        
        try:
            response = self.client.table("articles").select("*").order("published", desc=True).limit(limit).execute()
            return response.data
        except Exception as e:
            print(f"Fetch error: {e}")
            return []
