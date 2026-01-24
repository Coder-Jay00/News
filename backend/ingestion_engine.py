import feedparser
import datetime
import json
import os
from typing import List, Dict

class IngestionEngine:
    def __init__(self):
        self.sources = {
            "tier2_rss": [
                # --- GENERAL NEWS (Top Stories) ---
                {"url": "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "category": "World News"},
                {"url": "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TVdZU0FtVnlHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en", "category": "Business"},
                {"url": "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en", "category": "India News"},
                {"url": "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp6Y1WtU0FtVnlHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en", "category": "Technology"},
                {"url": "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtVnlHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en", "category": "Science"},
                {"url": "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNR3QwTlRFU0FtVnlHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en", "category": "Health"},
                
                # --- NICHE ---
                # Tech & AI
                {"url": "https://news.google.com/rss/search?q=artificial+intelligence+research+OR+LLM+architectures&hl=en-US&gl=US&ceid=US:en", "category": "AI & Frontiers"},
                {"url": "https://news.google.com/rss/search?q=cybersecurity+breach+OR+zero+day&hl=en-US&gl=US&ceid=US:en", "category": "Cybersecurity"},
            ]
        }

    def fetch_rss_feed(self, url: str, category: str) -> List[Dict]:
        """Fetches and normalizes RSS feed data."""
        from dateutil import parser # Robust date parsing
        
        print(f"Fetching RSS: {url}...")
        feed = feedparser.parse(url)
        articles = []
        
        for entry in feed.entries[:10]: # Limit to top 10 per feed to avoid noise
            # Normalize published date to UTC
            raw_date = entry.get("published") or entry.get("updated") or str(datetime.datetime.now())
            try:
                # Force UTC normalization
                dt = parser.parse(raw_date)
                if not dt.tzinfo:
                    dt = dt.replace(tzinfo=datetime.timezone.utc)
                iso_date = dt.astimezone(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
            except:
                iso_date = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')

            articles.append({
                "title": entry.title,
                "link": entry.link,
                "published": iso_date,
                "summary": entry.get("summary", ""),
                "source": entry.source.get("title", "Unknown") if hasattr(entry, "source") else "Google News",
                "category": category,
                "tier": 2, # Tier 2 = Broad RSS
                "trust_badge": "Unverified" # Default until Gemini checks it
            })
        return articles

    def fetch_newsdata(self) -> List[Dict]:
        """Tier 1: High Quality API (NewsData.io)."""
        api_key = os.getenv("NEWSDATA_API_KEY")
        if not api_key or "your_" in api_key:
            print("Skipping NewsData.io (No API Key)")
            return []

        url = f"https://newsdata.io/api/1/news?apikey={api_key}&q=cybersecurity OR artificial intelligence&language=en"
        print(f"Fetching NewsData.io...")
        
        try:
            import requests # Lazy import
            response = requests.get(url)
            data = response.json()
            articles = []
            
            for entry in data.get('results', []):
                articles.append({
                    "title": entry.get('title'),
                    "link": entry.get('link'),
                    "published": entry.get('pubDate', str(datetime.datetime.now())),
                    "summary": entry.get('description', ""),
                    "source": entry.get('source_id', "NewsData"),
                    "category": "Tech", # Simplified mapping
                    "tier": 1,
                    "trust_badge": "Official", # Assume high trust for Tier 1
                    "icon": "shield"
                })
            return articles
        except Exception as e:
            print(f"NewsData Fetch Error: {e}")
            return []

    def run_tier2_ingestion(self) -> List[Dict]:
        """Runs all Tier 2 RSS fetches."""
        all_articles = []
        for source in self.sources["tier2_rss"]:
            all_articles.extend(self.fetch_rss_feed(source["url"], source["category"]))
        
        print(f"Collected {len(all_articles)} raw Tier 2 articles.")
        return all_articles

if __name__ == "__main__":
    engine = IngestionEngine()
    articles = engine.run_tier2_ingestion()
    
    # improved debug output
    print(json.dumps(articles[:3], indent=2))
