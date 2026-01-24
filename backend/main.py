from ingestion_engine import IngestionEngine
from intelligence_agent import IntelligenceAgent
from database_manager import DatabaseManager
from push_notifier import send_news_notification
from dotenv import load_dotenv
import time
import json
import os

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
    
    # 2. Ingest Data (Tier 2 RSS for now)
    print("\n--- STEP 1: INGESTION ---")
    raw_articles = ingestion.run_tier2_ingestion()
    print(f"Total Raw Articles: {len(raw_articles)}")
    
    if not raw_articles:
        print("No articles found. Exiting.")
        return

    # 3. Intelligence Layer (Analyze & Summarize)
    print("\n--- STEP 2: INTELLIGENCE ANALYSIS ---")
    processed_articles = []
    fail_count = 0
    
    for article in raw_articles:
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
        time.sleep(1)

    # Fallback Mechanism
    if fail_count > len(raw_articles) * 0.5: # If >50% failed
        print("\n!!! GEMINI CRITICAL FAILURE DETECTED !!!")
        print("Falling back to Tier 1 (NewsData.io)...")
        tier1_articles = ingestion.fetch_newsdata()
        processed_articles.extend(tier1_articles) # Add Tier 1 (Pre-trusted)

    # 4. Upload to Database
    print(f"\n--- STEP 3: DATABASE UPLOAD ({len(processed_articles)} articles) ---")
    if processed_articles:
        db.upload_batch(processed_articles)
    else:
        print("SKIP: No processed articles to upload.")
    
    # 5. Send Push Notification (NEW!)
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
