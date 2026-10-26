-- Phase 3: server-owned PayMongo payment records.
--
-- These tables are deliberately backend-only. The kiosk never reads or writes
-- them through the Data API; the Edge Function uses the service role after it
-- has authenticated the kiosk request or verified a PayMongo webhook.

create table if not exists public.photo_payment_price_tiers (
  id uuid primary key default gen_random_uuid(),
  booth_id text not null references public.photo_booths(booth_id) on delete restrict,
  print_quantity smallint not null check (print_quantity between 1 and 10),
  amount_minor integer not null check (amount_minor between 100 and 2000000),
  currency char(3) not null default 'PHP' check (currency = 'PHP'),
  version integer not null default 1 check (version > 0),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (booth_id, print_quantity, version)
);

create unique index if not exists photo_payment_price_tiers_one_active_price_idx
  on public.photo_payment_price_tiers (booth_id, print_quantity)
  where active = true;

create table if not exists public.photo_payment_orders (
  id uuid primary key default gen_random_uuid(),
  booth_id text not null references public.photo_booths(booth_id) on delete restrict,
  client_request_id uuid not null,
  print_quantity smallint not null check (print_quantity between 1 and 10),
  amount_minor integer not null check (amount_minor between 100 and 2000000),
  currency char(3) not null default 'PHP' check (currency = 'PHP'),
  price_version integer not null check (price_version > 0),
  status text not null default 'created'
    check (status in ('created', 'payment_pending', 'paid', 'failed', 'expired', 'cancelled')),
  paymongo_payment_intent_id text unique,
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (booth_id, client_request_id)
);

create index if not exists photo_payment_orders_booth_status_idx
  on public.photo_payment_orders (booth_id, status, created_at desc);

create table if not exists public.photo_payment_attempts (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.photo_payment_orders(id) on delete cascade,
  attempt_number smallint not null check (attempt_number > 0),
  idempotency_key uuid not null unique,
  paymongo_payment_intent_id text unique,
  paymongo_client_key text,
  provider_status text,
  last_error_code text,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (order_id, attempt_number)
);

create index if not exists photo_payment_attempts_order_idx
  on public.photo_payment_attempts (order_id, attempt_number desc);

create table if not exists public.photo_payment_webhook_events (
  provider_event_id text primary key,
  event_type text not null,
  livemode boolean not null,
  provider_payment_id text,
  provider_payment_intent_id text,
  order_id uuid references public.photo_payment_orders(id) on delete set null,
  payload_sha256 char(64) not null,
  status text not null default 'received'
    check (status in ('received', 'processed', 'ignored', 'failed')),
  processing_error text,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  updated_at timestamptz not null default now()
);

create index if not exists photo_payment_webhook_events_order_idx
  on public.photo_payment_webhook_events (order_id, received_at desc);

create table if not exists public.photo_payment_session_grants (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.photo_payment_orders(id) on delete restrict,
  booth_id text not null references public.photo_booths(booth_id) on delete restrict,
  status text not null default 'available'
    check (status in ('available', 'claimed', 'completed', 'expired', 'cancelled')),
  session_id uuid unique,
  expires_at timestamptz not null,
  claimed_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists photo_payment_session_grants_booth_status_idx
  on public.photo_payment_session_grants (booth_id, status, expires_at);

alter table public.photo_payment_price_tiers enable row level security;
alter table public.photo_payment_orders enable row level security;
alter table public.photo_payment_attempts enable row level security;
alter table public.photo_payment_webhook_events enable row level security;
alter table public.photo_payment_session_grants enable row level security;

revoke all on public.photo_payment_price_tiers from anon, authenticated;
revoke all on public.photo_payment_orders from anon, authenticated;
revoke all on public.photo_payment_attempts from anon, authenticated;
revoke all on public.photo_payment_webhook_events from anon, authenticated;
revoke all on public.photo_payment_session_grants from anon, authenticated;

create policy "photo payment price tiers are backend-only"
  on public.photo_payment_price_tiers
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "photo payment orders are backend-only"
  on public.photo_payment_orders
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "photo payment attempts are backend-only"
  on public.photo_payment_attempts
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "photo payment webhooks are backend-only"
  on public.photo_payment_webhook_events
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "photo payment session grants are backend-only"
  on public.photo_payment_session_grants
  for all to anon, authenticated
  using (false)
  with check (false);

comment on table public.photo_payment_price_tiers is 'Server-controlled PHP price tiers for verified PayMongo checkout.';
comment on table public.photo_payment_orders is 'Idempotent kiosk payment orders. Amount and price version are snapshotted at creation.';
comment on table public.photo_payment_attempts is 'Provider request records. PayMongo idempotency keys prevent duplicate payment intents.';
comment on table public.photo_payment_webhook_events is 'Signature-verified PayMongo delivery ledger. Payload is represented only by its SHA-256 digest.';
comment on table public.photo_payment_session_grants is 'One capture-session authorization per successfully paid order.';
