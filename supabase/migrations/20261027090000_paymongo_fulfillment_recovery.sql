-- Phase 5: durable fulfillment and human-in-the-loop recovery records.
--
-- This is additive. Payment state, authorization, and physical fulfillment
-- stay independent so a failed printer can never invalidate a paid order.

create table if not exists public.photo_payment_fulfillment_jobs (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.photo_payment_orders(id) on delete restrict,
  session_grant_id uuid not null unique references public.photo_payment_session_grants(id) on delete restrict,
  booth_id text not null references public.photo_booths(booth_id) on delete restrict,
  requested_copies smallint not null check (requested_copies between 1 and 10),
  submitted_copies smallint not null default 0 check (submitted_copies between 0 and 10),
  status text not null default 'queued'
    check (status in ('queued', 'submitting', 'submitted', 'completed', 'failed_before_submission', 'outcome_uncertain', 'cancelled')),
  operator_attention_required boolean not null default false,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint photo_payment_fulfillment_jobs_copy_count_check check (submitted_copies <= requested_copies),
  constraint photo_payment_fulfillment_jobs_attention_check check (
    (status = 'outcome_uncertain' and operator_attention_required = true)
    or (status <> 'outcome_uncertain')
  )
);

create table if not exists public.photo_payment_fulfillment_events (
  id uuid primary key default gen_random_uuid(),
  fulfillment_job_id uuid not null references public.photo_payment_fulfillment_jobs(id) on delete cascade,
  event_type text not null check (event_type in (
    'queued', 'submission_started', 'copy_submitted', 'submission_failed',
    'outcome_uncertain', 'operator_reviewed', 'operator_retry_approved', 'completed'
  )),
  submitted_copies smallint not null default 0 check (submitted_copies between 0 and 10),
  actor_type text not null check (actor_type in ('booth', 'operator', 'system')),
  reason text,
  created_at timestamptz not null default now()
);

create index if not exists photo_payment_fulfillment_jobs_booth_status_idx
  on public.photo_payment_fulfillment_jobs (booth_id, status, created_at desc);

create index if not exists photo_payment_fulfillment_events_job_idx
  on public.photo_payment_fulfillment_events (fulfillment_job_id, created_at desc);

alter table public.photo_payment_fulfillment_jobs enable row level security;
alter table public.photo_payment_fulfillment_events enable row level security;

revoke all on public.photo_payment_fulfillment_jobs from anon, authenticated;
revoke all on public.photo_payment_fulfillment_events from anon, authenticated;

create policy "payment fulfillment jobs are backend-only"
  on public.photo_payment_fulfillment_jobs
  for all to anon, authenticated
  using (false)
  with check (false);

create policy "payment fulfillment events are backend-only"
  on public.photo_payment_fulfillment_events
  for all to anon, authenticated
  using (false)
  with check (false);

comment on table public.photo_payment_fulfillment_jobs is
  'One durable fulfillment job per paid order; physical USB output remains distinguishable from payment and session state.';
comment on table public.photo_payment_fulfillment_events is
  'Append-only payment fulfillment recovery ledger, including operator review of uncertain physical output.';

