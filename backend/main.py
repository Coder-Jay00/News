from ingestion_engine import IngestionEngine
from intelligence_agent import IntelligenceAgent
from database_manager import DatabaseManager
from push_notifier import send_news_notification
from dotenv import load_dotenv
import time
import json
import os
import datetime

# 1. Load Environment Variables (Only for local dev)
if os.path.exists(".env"):
    load_dotenv()
    print("Local .env loaded.")
elif os.path.exists("backend/.env"):
    load_dotenv("backend/.env")
    print("Local backend/.env loaded.")

def main():
    print("=== STARTING DAILY BRIEF PIPELINE ===")
    
    # Debug Environment
    print(f"DEBUG: SUPABASE_URL exists: {os.getenv('SUPABASE_URL') is not None}")
    print(f"DEBUG: SUPABASE_KEY exists: {os.getenv('SUPABASE_KEY') is not None}")
    
    # 1. Initialize Components
    ingestion = IngestionEngine()
    intel = IntelligenceAgent()
    db = DatabaseManager()
    
    # 2. Ingest Data (Friendly RSS)
    print("\n--- STEP 1: INGESTION ---")
    raw_articles = ingestion.run_friendly_ingestion()
    print(f"Total Raw Articles: {len(raw_articles)}")
    
    # Shuffle articles to ensure the top of the feed has a healthy mix of categories
    import random
    random.shuffle(raw_articles)
    
    if not raw_articles:
        print("SKIP: No articles found in the last 24h window. Notification not sent.")
        return

    # 3. Intelligence Layer (Analyze & Summarize)
    print("\n--- STEP 2: INTELLIGENCE ANALYSIS ---")
    
    # Filter out articles that already exist in DB to save Gemini Quota
    all_links = [a['link'] for a in raw_articles]
    existing_links = set(db.get_existing_links(all_links))
    
    new_articles = [a for a in raw_articles if a['link'] not in existing_links]
    print(f"Filtering: {len(raw_articles)} raw -> {len(new_articles)} new articles (Skipped {len(existing_links)} existing)")
    
    if not new_articles:
        print("SKIP: No new articles to process.")
        return

    processed_articles = []
    fail_count = 0
    
    for article in new_articles:
        # Skip if title is too short or clearly junk (basic filter)
        if len(article['title']) < 15: 
            continue
            
        print(f"Analyzing: {article['title'][:50]}...")
        enriched_article = intel.analyze_article(article)
        
        # Check for Critical Failure (Gemini Down)
        if enriched_article.get("ai_summary") == "Analysis Failed":
            print("Gemini Failed. Marking for Fallback...")
            fail_count += 1
        
        processed_articles.append(enriched_article)
        # Gemini 1.5 Flash Free Tier: 15 RPM limit (~1 req every 4 seconds)
        time.sleep(4)

    # Fallback Mechanism
    if fail_count > len(new_articles) * 0.5: # If >50% failed
        print("\n!!! GEMINI CRITICAL FAILURE DETECTED !!!")
        print("Falling back to Tier 1 (NewsData.io)...")
        tier1_articles = ingestion.fetch_newsdata()
        processed_articles.extend(tier1_articles) # Add Tier 1 (Pre-trusted)

    # 4. Final Deduplication (Safeguard against Tier 1 overlaps)
    final_articles = []
    seen_links = set()
    for art in processed_articles:
        if art['link'] not in seen_links:
            final_articles.append(art)
            seen_links.add(art['link'])
    processed_articles = final_articles

    print(f"\n--- STEP 3: DATABASE UPLOAD ({len(processed_articles)} unique articles) ---")
    if processed_articles:
        db.upload_batch(processed_articles)
    else:
        print("SKIP: No processed articles to upload.")
    
    # Feature 9: The Morning Reel (Top 3)
    # -----------------------------------
    if processed_articles:
        print("\n--- FEATURE 9: GENERATING MORNING REEL ---")
        # Sort by Trust Score desc to get highest quality news
        sorted_arts = sorted(processed_articles, key=lambda x: x.get('trust_score', 0), reverse=True)
        top_3 = sorted_arts[:3]
        
        reel_content = {
            "title": f"Daily Intelligence Reel • {datetime.datetime.now().strftime('%b %d')}",
            "summary": "Your daily high-signal update.",
            "stories": top_3
        }
        db.save_morning_reel(reel_content)

    # Feature 10: Watchlist Alerts
    # -----------------------------------
    print("\n--- FEATURE 10: CHECKING WATCHLISTS ---")
    watchlists = db.get_all_watchlists() # List of {token, keyword}
    for watch_rule in watchlists:
        keyword = watch_rule.get('keyword', '').lower()
        token = watch_rule.get('user_fcm_token')
        
        for art in processed_articles:
            if keyword in art['title'].lower() or keyword in art.get('ai_summary', '').lower():
                print(f"Watchlist Hit! {keyword} found in {art['title']}")
                from push_notifier import send_targeted_notification
                send_targeted_notification(
                    token=token, 
                    title=f"🔔 Alert: {keyword.capitalize()} News",
                    body=f"Found in: {art['title']}"
                )
                break # Alert once per keyword per run to avoid spam

    # 5. Send Push Notification (General)
    print("\n--- STEP 4: PUSH NOTIFICATION ---")
    if processed_articles:
        top_headline = processed_articles[0].get('title', 'New stories available')
        send_news_notification(
            article_count=len(processed_articles),
            top_headline=top_headline
        )
    
    # 6. Cleanup
    print("\n--- STEP 5: CLEANUP ---")
    db.purge_old_data(hours=48)
    
    print("\n=== PIPELINE COMPLETE ===")

if __name__ == "__main__":
    main()
