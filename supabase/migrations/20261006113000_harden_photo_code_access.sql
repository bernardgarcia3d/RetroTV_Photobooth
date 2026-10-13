alter table public.photo_code_batches
    add column if not exists event_id text not null default 'retro-roll';

alter table public.photo_access_codes
    add column if not exists event_id text not null default 'retro-roll';

create index if not exists photo_code_batches_event_date_idx
    on public.photo_code_batches (event_id, business_date, package_id);

create unique index if not exists photo_access_codes_event_date_digest_uidx
    on public.photo_access_codes (event_id, business_date, code_digest);

comment on column public.photo_code_batches.event_id is 'Logical event scope for shared booth code generation.';
comment on column public.photo_access_codes.event_id is 'Logical event scope for redemption and duplicate prevention.';
