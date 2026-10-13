alter table public.photo_access_codes
    add column if not exists session_status text
        check (session_status is null or session_status in ('claimed', 'completed', 'failed', 'abandoned'));

alter table public.photo_access_codes
    add column if not exists r2_asset_references jsonb;

comment on column public.photo_access_codes.session_status is 'Metadata-only session state associated with the claimed voucher.';
comment on column public.photo_access_codes.r2_asset_references is 'Metadata-only R2 object keys; customer media remains in Cloudflare R2.';
