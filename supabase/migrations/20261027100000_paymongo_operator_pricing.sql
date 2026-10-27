-- Phase 6: per-booth credentials and audited, server-owned operator pricing.
--
-- A kiosk can change only the prices for the booth whose credential it holds.
-- Price changes are atomic and append a new version, preserving the price that
-- was used for every historic payment order.

create table if not exists public.photo_payment_booth_credentials (
  id uuid primary key default gen_random_uuid(),
  booth_id text not null unique references public.photo_booths(booth_id) on delete cascade,
  device_key_sha256 char(64) not null unique check (device_key_sha256 ~ '^[a-f0-9]{64}$'),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.photo_payment_price_change_audit (
  id uuid primary key default gen_random_uuid(),
  booth_id text not null references public.photo_booths(booth_id) on delete restrict,
  credential_id uuid references public.photo_payment_booth_credentials(id) on delete set null,
  previous_tiers jsonb not null,
  replacement_tiers jsonb not null,
  price_version integer not null check (price_version > 0),
  changed_at timestamptz not null default now()
);

create index if not exists photo_payment_price_change_audit_booth_idx
  on public.photo_payment_price_change_audit (booth_id, changed_at desc);

alter table public.photo_payment_booth_credentials enable row level security;
alter table public.photo_payment_price_change_audit enable row level security;

revoke all on public.photo_payment_booth_credentials from anon, authenticated;
revoke all on public.photo_payment_price_change_audit from anon, authenticated;

create policy "payment booth credentials are backend-only"
  on public.photo_payment_booth_credentials
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "payment price audit is backend-only"
  on public.photo_payment_price_change_audit
  for all to anon, authenticated
  using (false)
  with check (false);

create or replace function public.replace_photo_payment_price_tiers(
  p_booth_id text,
  p_credential_id uuid,
  p_tiers jsonb
)
returns table (price_version integer, tiers jsonb)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_previous jsonb;
  v_replacement jsonb;
  v_version integer;
  v_count integer;
begin
  if p_booth_id !~ '^[A-Za-z0-9_-]{1,64}$' then
    raise exception 'invalid booth id';
  end if;
  if jsonb_typeof(p_tiers) <> 'array' then
    raise exception 'price tiers must be an array';
  end if;

  select count(*) into v_count from jsonb_array_elements(p_tiers);
  if v_count < 1 or v_count > 10 then
    raise exception 'between one and ten price tiers are required';
  end if;
  if exists (
    select 1
    from jsonb_to_recordset(p_tiers) as item(print_quantity integer, amount_minor integer)
    where item.print_quantity not between 1 and 10
       or item.amount_minor not between 100 and 2000000
  ) then
    raise exception 'invalid price tier';
  end if;
  if (select count(distinct item.print_quantity) from jsonb_to_recordset(p_tiers) as item(print_quantity integer, amount_minor integer)) <> v_count then
    raise exception 'duplicate print quantities are not allowed';
  end if;

  select coalesce(jsonb_agg(jsonb_build_object('printQuantity', print_quantity, 'amountMinor', amount_minor) order by print_quantity), '[]'::jsonb)
    into v_previous
    from public.photo_payment_price_tiers
    where booth_id = p_booth_id and active;

  select coalesce(max(version), 0) + 1 into v_version
    from public.photo_payment_price_tiers
    where booth_id = p_booth_id;

  select jsonb_agg(jsonb_build_object('printQuantity', item.print_quantity, 'amountMinor', item.amount_minor) order by item.print_quantity)
    into v_replacement
    from jsonb_to_recordset(p_tiers) as item(print_quantity integer, amount_minor integer);

  update public.photo_payment_price_tiers
    set active = false, updated_at = now()
    where booth_id = p_booth_id and active;

  insert into public.photo_payment_price_tiers (booth_id, print_quantity, amount_minor, currency, version, active)
  select p_booth_id, item.print_quantity, item.amount_minor, 'PHP', v_version, true
  from jsonb_to_recordset(p_tiers) as item(print_quantity integer, amount_minor integer);

  insert into public.photo_payment_price_change_audit (booth_id, credential_id, previous_tiers, replacement_tiers, price_version)
  values (p_booth_id, p_credential_id, v_previous, v_replacement, v_version);

  return query select v_version, v_replacement;
end;
$$;

revoke all on function public.replace_photo_payment_price_tiers(text, uuid, jsonb) from public, anon, authenticated;
grant execute on function public.replace_photo_payment_price_tiers(text, uuid, jsonb) to service_role;

comment on table public.photo_payment_booth_credentials is 'Hashed, per-booth kiosk credentials. They scope payment and price administration to one booth.';
comment on table public.photo_payment_price_change_audit is 'Immutable audit trail for server-owned operator price changes.';
comment on function public.replace_photo_payment_price_tiers(text, uuid, jsonb) is 'Service-role-only atomic price version replacement used by the PayMongo Edge Function.';

