-- Run once in Supabase: SQL Editor -> New query -> paste -> Run.
create extension if not exists pgcrypto;

create table if not exists public.controls (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  name text not null check (char_length(name) between 2 and 40),
  description text not null default '' check (char_length(description) <= 300),
  author text not null default '' check (char_length(author) <= 32),
  tags text[] not null default '{}' check (cardinality(tags) <= 6),
  data text not null check (char_length(data) <= 420000),   -- base64 of the control file
  downloads integer not null default 0,
  hidden boolean not null default false                      -- set true in the dashboard to take a control down
);
create index if not exists controls_tags_idx on public.controls using gin (tags);
create index if not exists controls_created_idx on public.controls (created_at desc);

alter table public.controls enable row level security;

-- anyone (the launcher, with the anon key) may read visible rows and add new ones; nobody may edit or delete
drop policy if exists "read visible" on public.controls;
create policy "read visible" on public.controls for select using (hidden = false);
drop policy if exists "add new" on public.controls;
create policy "add new" on public.controls for insert with check (hidden = false and downloads = 0);

-- download counter (the only thing the app may change on an existing row)
create or replace function public.increment_downloads(cid uuid) returns void
language sql security definer set search_path = public as
$$ update public.controls set downloads = downloads + 1 where id = cid and hidden = false $$;
grant execute on function public.increment_downloads(uuid) to anon;
