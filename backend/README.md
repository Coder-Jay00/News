# Backend Setup

## 1. Environment Variables
Copy `.env.example` to `.env` and fill in:
- `GEMINI_API_KEY`: Get from Google AI Studio.
- `SUPABASE_URL` & `KEY`: Get from Supabase Dashboard.

## 2. Local Run
```bash
pip install -r requirements.txt
python main.py
```

## 3. GitHub Actions (Production)
Go to your Repo Settings -> Secrets and Variables -> Actions.
Add the following secrets:
- `GEMINI_API_KEY`
- `SUPABASE_URL`
- `SUPABASE_KEY`
