-- MoneyFen backup table. Run once in Supabase → SQL Editor.
create table if not exists public.moneyfen_backups (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.moneyfen_backups enable row level security;

drop policy if exists "own backup" on public.moneyfen_backups;
create policy "own backup" on public.moneyfen_backups
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
