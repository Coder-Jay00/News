import os
import json
from dotenv import load_dotenv
from intelligence_agent import IntelligenceAgent

# Load Env
if os.path.exists("backend/.env"):
    load_dotenv("backend/.env")
else:
    load_dotenv()

def debug_run():
    print("--- DEBUGGING INTELLIGENCE AGENT ---")
    agent = IntelligenceAgent()
    
    article = {
        "title": "Quantum Computing Breakthrough: IBM Unveils 1000-Qubit Chip",
        "source": "TechCrunch",
        "summary": "IBM has announced a major milestone in quantum computing with the release of its new Condor chip, featuring over 1000 qubits. This development could pave the way for solving complex problems in drug discovery and materials science that are currently impossible for classical computers. However, error rates remain a significant challenge."
    }
    
    print("\nSending Article...")
    try:
        # Mock the generate_content to print raw text if possible, 
        # but since we can't easily mock without import issues, we'll just run it and catch the internal print if we modified the class.
        # Check if we can intercept the response.
        
        # Actually, let's just use the agent's method and see the result.
        # But to see the RAW text, I need to modify the agent momentarily OR just rely on print statements if I added them?
        # I didn't add print statements for raw text in the class yet.
        
        # Let's call the model DIRECTLY here to see what it generates.
        prompt = f"""
        You are an elite intelligence analyst for tech students. Analyze this news item:
        Title: {article['title']}
        Source: {article['source']}
        Content Snippet: {article['summary']}

        Output ONLY valid JSON with this structure:
        {{
            "summary": "3 bullet points. concise. why it matters to a CS student.",
            "trust_badge": "One of: [Official], [Technical], [Strategic], [News]",
            "icon": "Lucide icon name",
            "trust_score": 85,
            "trust_reason": "Brief explanation."
        }}
        """
        
        print("Prompt sent. Waiting for Gemini...")
        response = agent.model.generate_content(prompt)
        print("\n--- RAW RESPONSE FROM GEMINI ---")
        print(response.text)
        print("--------------------------------\n")
        
        # Now try the actual method
        print("Running agent.analyze_article()...")
        res = agent.analyze_article(article)
        print(json.dumps(res, indent=2))
        
    except Exception as e:
        print(f"CRASH: {e}")

if __name__ == "__main__":
    debug_run()
