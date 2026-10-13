create table public.photo_code_batches (
    id uuid primary key default gen_random_uuid(),
    business_date date not null,
    package_id text not null,
    package_label text not null,
    quantity integer not null check (quantity between 1 and 10000),
    expires_at timestamptz not null,
    created_by text not null default 'operator-app',
    created_at timestamptz not null default now()
);

create table public.photo_access_codes (
    id uuid primary key default gen_random_uuid(),
    batch_id uuid not null references public.photo_code_batches(id) on delete cascade,
    package_id text not null,
    code_digest text not null,
    code_hint text not null,
    status text not null default 'issued' check (status in ('issued', 'claimed', 'completed', 'expired', 'cancelled', 'abandoned')),
    business_date date not null,
    expires_at timestamptz not null,
    printed_at timestamptz not null default now(),
    claimed_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    booth_id text,
    photo_session_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (batch_id, code_digest)
);

create index photo_access_codes_lookup_idx on public.photo_access_codes (code_digest, status, expires_at);
create index photo_access_codes_expiry_idx on public.photo_access_codes (status, expires_at);
create index photo_access_codes_batch_idx on public.photo_access_codes (batch_id);

alter table public.photo_code_batches enable row level security;
alter table public.photo_access_codes enable row level security;

revoke all on public.photo_code_batches from anon, authenticated;
revoke all on public.photo_access_codes from anon, authenticated;

comment on table public.photo_code_batches is 'Operator-generated daily batches of paid-session access codes.';
comment on table public.photo_access_codes is 'Single-use five-digit access codes. Media remains in the existing Cloudflare R2 service.';
