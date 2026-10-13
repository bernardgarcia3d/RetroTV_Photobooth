create table if not exists public.photo_booths (
  id uuid primary key default gen_random_uuid(),
  booth_id text not null unique
    check (booth_id ~ '^[A-Za-z0-9_-]{1,64}$'),
  display_name text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.photo_booths enable row level security;

drop policy if exists "photo_booths_deny_anon" on public.photo_booths;
create policy "photo_booths_deny_anon"
  on public.photo_booths
  for all
  to anon
  using (false)
  with check (false);

drop policy if exists "photo_booths_deny_authenticated" on public.photo_booths;
create policy "photo_booths_deny_authenticated"
  on public.photo_booths
  for all
  to authenticated
  using (false)
  with check (false);

insert into public.photo_booths (booth_id, display_name)
values ('booth-002', 'Primary RetroRoll booth')
on conflict (booth_id) do nothing;

create index if not exists photo_booths_active_booth_id_idx
  on public.photo_booths (booth_id)
  where active = true;
