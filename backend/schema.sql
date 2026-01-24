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
  tier integer
);

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
