import google.generativeai as genai
import os
import json
from typing import Dict

class IntelligenceAgent:
    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key or "YOUR_" in api_key:
            print("ERROR: GEMINI_API_KEY is missing or contains a placeholder!")
        else:
            genai.configure(api_key=api_key)
            # Use pinned version to avoid 404 on v1beta alias lookup
            self.model = genai.GenerativeModel('gemini-1.5-flash-001')
            print("Intelligence Agent initialized (Model: gemini-1.5-flash-001)")

    def analyze_article(self, article: Dict) -> Dict:
        """
        Enriches an article with:
        1. Student-focused Summary (3 bullets)
        2. Trust Badge (Official, Technical, Strategic, Opinion)
        3. Icon (Lucide name)
        """
        if not hasattr(self, 'model'):
             # Fallback if no key
            article["summary"] = "AI Summary Unavailable (Missing Key)"
            article["trust_badge"] = "Unverified"
            article["icon"] = "file-text"
            return article

        prompt = f"""
        You are an elite intelligence analyst for tech students. Analyze this news item:
        Title: {article['title']}
        Source: {article['source']}
        Content Snippet: {article['summary'][:500]}

        Output ONLY valid JSON with this structure:
        {{
            "summary": "3 bullet points. concise. why it matters to a CS student.",
            "trust_badge": "One of: [Official], [Technical], [Strategic], [News]",
            "icon": "A single Lucide icon name (e.g., 'cpu', 'shield-alert', 'globe', 'zap') that fits best.",
            "trust_score": 85,  // Integer 0-100. 100=Official Docs, 80=Reputable News, 40=Rumor/Clickbait
            "trust_reason": "Brief explanation of the score."
        }}
        """
        
        try:
            response = self.model.generate_content(prompt)
            res_text = response.text.strip()
            
            # Clean Markdown if present
            if res_text.startswith("```json"):
                res_text = res_text[7:]
            if res_text.startswith("```"):
                res_text = res_text[3:]
            if res_text.endswith("```"):
                res_text = res_text[:-3]
            
            res_text = res_text.strip()
            
            # Find the first { and last } to extract JSON block
            start = res_text.find('{')
            end = res_text.rfind('}') + 1
            if start == -1 or end == 0:
                raise ValueError("No JSON block found in response")
            
            clean_json = res_text[start:end]
            data = json.loads(clean_json)
            
            article["ai_summary"] = data.get("summary", article["summary"])
            article["trust_badge"] = data.get("trust_badge", "News")
            article["icon"] = data.get("icon", "file-text")
            article["trust_score"] = data.get("trust_score", 50)
            article["trust_reason"] = data.get("trust_reason", "Standard news report.")
            
        except Exception as e:
            print(f"Error analyzing {article['title']}: {e}")
            article["ai_summary"] = "Analysis Failed"
            article["trust_badge"] = "News"
            article["icon"] = "alert-circle"
            article["trust_score"] = 50
            article["trust_reason"] = "Analysis failed."

        return article

    def synthesize_cluster(self, articles: list) -> Dict:
        """
        Feature #7: Multi-Source Synthesis.
        Takes 2+ articles on the same topic and creates a Master Report.
        """
        if not articles: return {}
        
        titles = " | ".join([a['title'] for a in articles])
        snippets = " ".join([a.get('summary', '')[:200] for a in articles])
        
        prompt = f"""
        SYNTHESIZE COMMAND:
        Merge these {len(articles)} conflicting/related reports into ONE Master Intelligence Brief.
        
        Sources: {titles}
        Context: {snippets}
        
        Output JSON:
        {{
            "title": "One definitive, non-clickbait title",
            "summary": "Comprehensive 4-bullet summary merging ALL facts.",
            "trust_score": 90, // Calculated average trust
            "trust_reason": "Synthesis of [Source A] and [Source B]",
            "trust_badge": "Strategic",
            "icon": "layers"
        }}
        """
        try:
            response = self.model.generate_content(prompt)
            res_text = response.text.strip()
            
            # Clean Markdown
            if res_text.startswith("```json"):
                res_text = res_text[7:]
            if res_text.startswith("```"):
                res_text = res_text[3:]
            if res_text.endswith("```"):
                res_text = res_text[:-3]
            res_text = res_text.strip()
            
            clean_json = res_text[res_text.find('{'):res_text.rfind('}')+1]
            return json.loads(clean_json)
        except:
            return None # Fallback to using individual articles

if __name__ == "__main__":
    # Test Run
    agent = IntelligenceAgent()
    sample = {
        "title": "NVIDIA announces new H200 GPU architecture",
        "source": "NVIDIA Blog",
        "summary": "Today we revealed the H200 with 141GB of HBM3e memory..."
    }
    print(json.dumps(agent.analyze_article(sample), indent=2))
