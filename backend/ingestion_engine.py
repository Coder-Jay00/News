import feedparser
import datetime
import json
import os
from typing import List, Dict

class IngestionEngine:
    def __init__(self):
        self.sources = {
            "tier2_rss": [
                # --- GENERAL NEWS (Balanced Freshness: 3h) ---
                {"url": "https://news.google.com/rss/search?q=world+news+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "World News"},
                {"url": "https://news.google.com/rss/search?q=business+finance+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "Business"},
                {"url": "https://news.google.com/rss/search?q=India+breaking+news+when:3h&hl=en-IN&gl=IN&ceid=IN:en", "category": "India News"},
                {"url": "https://news.google.com/rss/search?q=technology+innovation+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "Technology"},
                {"url": "https://news.google.com/rss/search?q=science+research+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "Science"},
                {"url": "https://news.google.com/rss/search?q=health+medical+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "Health"},
                
                # --- NICHE ---
                {"url": "https://news.google.com/rss/search?q=AI+LLM+OR+Nvidia+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "AI & Frontiers"},
                {"url": "https://news.google.com/rss/search?q=cybersecurity+breach+OR+hacker+when:3h&hl=en-US&gl=US&ceid=US:en", "category": "Cybersecurity"},
            ]
        }

    def fetch_rss_feed(self, url: str, category: str) -> List[Dict]:
        """Fetches and normalizes RSS feed data using robust requests."""
        from dateutil import parser 
        import requests # Using requests for better header control
        
        print(f"Fetching RSS: {url}...")
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=15)
            feed = feedparser.parse(response.content)
            articles = []
            
            for entry in feed.entries[:25]:
                raw_date = entry.get("published") or entry.get("updated") or str(datetime.datetime.now())
                try:
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
                    "source": entry.source.get("title", "Google News") if hasattr(entry, "source") else "Google News",
                    "category": category,
                    "tier": 2,
                    "trust_badge": "Unverified"
                })
            print(f"  Success: Found {len(articles)} entries.")
            return articles
        except Exception as e:
            print(f"  Error fetching {url}: {e}")
            return []

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
