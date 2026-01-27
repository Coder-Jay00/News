-- 1. Create the table
create table articles (
  id uuid default gen_random_uuid() primary key,
  title text not null,
  summary text,
  source text,
  published timestamptz,
  category text,
  trust_badge text,
  icon text,
  link text unique not null,
  ai_summary text,
  tier integer,
  
  -- New Columns for Features 5 & 7 (Fact Check & Synthesis)
  trust_score integer,      -- 0-100 Score
  trust_reason text,        -- AI Explanation for score
  related_links text[]      -- Array of links if merged from multiple sources
);

-- Feature 9: The Morning Reel (Big Three)
create table daily_briefings (
  id uuid default gen_random_uuid() primary key,
  created_at timestamptz default now(),
  content jsonb,            -- Stores the list of 3 top stories
  date_str text unique      -- e.g. "2024-01-27"
);

-- Feature 10: Watchlist Keywords
create table user_watchlists (
  id uuid default gen_random_uuid() primary key,
  user_fcm_token text not null,
  keyword text not null,
  created_at timestamptz default now()
);

-- RLS policies for new tables (Open read for now)
alter table daily_briefings enable row level security;
create policy "Anon read briefings" on daily_briefings for select using (true);
create policy "Service write briefings" on daily_briefings for insert with check (true);

alter table user_watchlists enable row level security;
create policy "Anon insert watchlist" on user_watchlists for insert with check (true);  
create policy "Anon select own watchlist" on user_watchlists for select using (true);

-- 2. Enable Security (RLS)
alter table articles enable row level security;

-- 3. Allow Public (App) to READ
create policy "Anon can read all" on articles
for select using (true);

-- 4. Allow Admin (Backend) to WRITE
create policy "Service role can insert" on articles
for insert with check (true);

create policy "Service role can update" on articles
for update using (true);

create policy "Service role can delete" on articles
for delete using (true);
