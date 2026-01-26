import feedparser
import datetime
import json
import os
from typing import List, Dict

class IngestionEngine:
    def __init__(self):
            "friendly_sources": [
                # --- AGGREGATORS (The Backups) ---
                {"url": "https://www.bing.com/news/search?q=technology+AI&format=rss", "category": "Tech & AI", "source": "Bing News"},
                {"url": "https://news.yahoo.com/rss/tech", "category": "Tech", "source": "Yahoo News"},
                
                # --- DIRECT PUBLISHERS (High Trust) ---
                {"url": "https://techcrunch.com/feed/", "category": "Startups & VC", "source": "TechCrunch"},
                {"url": "https://www.theverge.com/rss/index.xml", "category": "Consumer Tech", "source": "The Verge"},
                {"url": "https://www.wired.com/feed/rss", "category": "Future & Science", "source": "Wired"},
                {"url": "http://feeds.arstechnica.com/arstechnica/index", "category": "IT & Policy", "source": "Ars Technica"},
                {"url": "https://feeds.feedburner.com/VentureBeat", "category": "AI & Enterprise", "source": "VentureBeat"},
                
                # --- GLOBAL/BUSINESS ---
                {"url": "http://feeds.bbci.co.uk/news/technology/rss.xml", "category": "Global Tech", "source": "BBC News"},
                {"url": "https://www.cnbc.com/id/19854910/device/rss/rss.html", "category": "Business Tech", "source": "CNBC"},
                {"url": "https://www.aljazeera.com/xml/rss/all.xml", "category": "Global News", "source": "Al Jazeera"},
            ]

    def fetch_rss_feed(self, url: str, category: str, source_name: str = "Unknown") -> List[Dict]:
        """Fetches and normalizes RSS feed data using robust requests."""
        from dateutil import parser 
        import requests # Using requests for better header control
        
        print(f"Fetching RSS: {url} ({source_name})...")
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=15)
            feed = feedparser.parse(response.content)
            articles = []
            
            for entry in feed.entries[:7]:
                raw_date = entry.get("published") or entry.get("updated") or str(datetime.datetime.now())
                try:
                    dt = parser.parse(raw_date)
                    if not dt.tzinfo:
                        dt = dt.replace(tzinfo=datetime.timezone.utc)
                    iso_date = dt.astimezone(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
                except:
                    iso_date = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')

                # Handle various summary fields
                summary_text = entry.get("summary") or entry.get("description") or ""

                articles.append({
                    "title": entry.title,
                    "link": entry.link,
                    "published": iso_date,
                    "summary": summary_text,
                    "source": source_name, # Use the explicitly passed source name
                    "category": category,
                    "tier": 2,
                    "trust_badge": "Verified" # Friendly sources are generally verified
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

    def run_friendly_ingestion(self) -> List[Dict]:
        """Runs ingestion across multiple friendly RSS sources."""
        import random
        all_articles = []
        
        # Shuffle sources to ensure category diversity in every run
        sources = self.sources["friendly_sources"].copy()
        
        # Pick 5 random sources to fetch per run (avoid timeout/overload)
        selected_sources = random.sample(sources, min(len(sources), 5)) 
        
        print(f"--- Fetching from {len(selected_sources)} Sources ---")
        
        for source in selected_sources:
            # Pass source name explicitly to helper
            all_articles.extend(self.fetch_rss_feed(source["url"], source["category"], source_name=source.get("source")))
        
        print(f"Collected {len(all_articles)} raw articles.")
        return all_articles

if __name__ == "__main__":
    engine = IngestionEngine()
    articles = engine.run_tier2_ingestion()
    
    # improved debug output
    print(json.dumps(articles[:3], indent=2))
