create policy "photo code batches are backend-only"
    on public.photo_code_batches
    for all to anon, authenticated
    using (false)
    with check (false);

create policy "photo access codes are backend-only"
    on public.photo_access_codes
    for all to anon, authenticated
    using (false)
    with check (false);
