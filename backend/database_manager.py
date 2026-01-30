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
            detected_val = f"'{url}'" if url else "EMPTY STRING"
            raise ValueError(f"CRITICAL: SUPABASE_URL is {detected_val}! GitHub is not sending the secret. Check the name in GitHub Settings.")
            
        if not key or "YOUR_" in key:
            detected_val = f"'{key}'" if key else "EMPTY STRING"
            raise ValueError(f"CRITICAL: SUPABASE_KEY is {detected_val}! GitHub is not sending the secret. Check the name in GitHub Settings.")

        print(f"Connecting to Supabase: {url[:25]}...")
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

    def get_existing_links(self, links: List[str]) -> List[str]:
        """Checks which links from the list already exist in the DB."""
        if not self.client or not links:
            return []
        
        try:
            # Chunking to avoid URL length issues or query limits if list is huge
            # (Though RSS batches are usually small ~50-100)
            existing_links = []
            
            # Simple chunking logic (e.g., 50 at a time)
            chunk_size = 50
            for i in range(0, len(links), chunk_size):
                chunk = links[i:i + chunk_size]
                response = self.client.table("articles").select("link").in_("link", chunk).execute()
                for record in response.data:
                    existing_links.append(record['link'])
                    
            return existing_links
            
        except Exception as e:
            print(f"Error checking existing links: {e}")
            return []

    def save_morning_reel(self, content_json: Dict):
        """Feature 9: Save the Top 3 stories for the Morning Reel."""
        if not self.client: return
        try:
            # Update: Use timestamp to allow multiple updates per day (e.g. Morning, Noon, Pulse)
            today_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
            data = {
                "date_str": today_str,
                "content": content_json
            }
            # Upsert will now act like Insert because date_str is unique per minute
            self.client.table("daily_briefings").upsert(data, on_conflict="date_str").execute()
            print(f"✅ Saved Daily Pulse for {today_str}")
        except Exception as e:
            print(f"Failed to save Morning Reel: {e}")

    def get_all_watchlists(self) -> List[Dict]:
        """Feature 10: Fetch all user watchlists for processing."""
        if not self.client: return []
        try:
            # We want to fetch all rules: {user_fcm_token, keyword}
            response = self.client.table("user_watchlists").select("*").execute()
            return response.data
        except Exception as e:
            print(f"Failed to fetch watchlists: {e}")
            return []
