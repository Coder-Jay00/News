import os
import sys

# Ensure backend directory is in path
sys.path.append(os.path.join(os.getcwd(), 'backend'))

try:
    print("1. Testing Imports...")
    from ingestion_engine import IngestionEngine
    from database_manager import DatabaseManager
    print("✅ Imports Successful.")
except Exception as e:
    print(f"❌ Import Failed: {e}")
    sys.exit(1)

def test_ingestion():
    print("\n2. Testing Friendly Ingestion...")
    try:
        engine = IngestionEngine()
        articles = engine.run_friendly_ingestion()
        
        if len(articles) > 0:
            print(f"✅ Success: Fetched {len(articles)} articles.")
            print(f"Sample: {articles[0]['title']} ({articles[0]['source']})")
        else:
            print("⚠️ Warning: Fetched 0 articles. Check internet or source list.")
            
    except Exception as e:
        print(f"❌ Ingestion Failed: {e}")

if __name__ == "__main__":
    test_ingestion()
