# PayMongo Staging Backend Runbook

Phase 3 adds the server-side foundation for QR Ph checkout. It is deliberately not deployed by this source change. Phase 4 now supplies the separate Android checkout client, but it stays unable to accept a test payment until this backend setup is complete.

## What is included

- `supabase/functions/paymongo-payments`: server-only order creation, PayMongo Payment Intent creation/retrieval, signed webhook handling, and single-session grants.
- `supabase/migrations/20261026010000_paymongo_payment_foundation.sql`: backend-only payment tables with RLS enabled and no `anon` or `authenticated` access.
- `supabase/config.toml`: disables Supabase's gateway JWT check for this one function because PayMongo webhook calls do not carry a Supabase JWT. The function itself verifies the PayMongo signature before parsing the body, and kiosk calls must carry the existing `x-booth-key`.

The function does not accept a client-provided amount or currency. It snapshots the configured PHP price tier for the requested booth and print quantity. It also uses the database order UUID as PayMongo's idempotency key, so a retry cannot create a second Payment Intent.

## Before test deployment

1. Apply the migration to the staging Supabase project. Do not expose the payment tables through a public API role.
2. Create active `photo_payment_price_tiers` rows for every permitted `booth_id` and print quantity. Prices are in centavos (`amount_minor`), and are intentionally not seeded by the migration.
3. Set these function secrets in the staging environment. Never put the secret key or webhook secret in Android source, Gradle properties, or a tracked `.env` file.

   - `PAYMONGO_SECRET_KEY` — PayMongo test secret key
   - `PAYMONGO_PUBLIC_KEY` — PayMongo test public key; returned only to the staging kiosk for the Phase 4 client-side Payment Method flow
   - `PAYMONGO_WEBHOOK_SECRET` — secret for this exact PayMongo webhook endpoint
   - `PAYMONGO_ENVIRONMENT=test`
   - `PAYMONGO_API_BASE_URL=https://api.paymongo.com`
   - `PAYMONGO_WEBHOOK_TOLERANCE_SECONDS=300`
   - `PAYMONGO_SESSION_GRANT_TTL_SECONDS=600`
   - Existing `BOOTH_SHARED_KEY` — used only to authenticate kiosk-originated function calls

4. Deploy the migration and `paymongo-payments` function, then create a **test-mode** PayMongo webhook at:

   `https://<project-ref>.supabase.co/functions/v1/paymongo-payments`

   Subscribe to `payment.paid`, `payment.failed`, and `payment_intent.succeeded`.

5. Use PayMongo's signed test-event delivery before allowing a tablet to accept a test payment. A valid event must create one ledger row and must not create more than one session grant when replayed.

## Authorization behavior

| Caller | Required verification | Can issue a session? |
| --- | --- | --- |
| PayMongo webhook | Timestamped HMAC-SHA256 signature on the untouched request body, matching the configured test/live mode | Only after the function retrieves the Payment Intent and verifies its amount, PHP currency, and `succeeded` status |
| PayMongo kiosk build | Existing `x-booth-key`, active booth record, and a server-controlled price tier | Only by claiming an already verified, booth-owned order |
| Anyone else | Rejected | No |

`payment_status` is only a recovery path for a delayed webhook. It retrieves the Payment Intent on the server; a client-side QR result never unlocks a capture session by itself.

## Phase 4 client contract

The implemented PayMongo Android flow calls these actions, all with `boothId` and the kiosk key:

1. `create_order` with a new UUID `requestId` and `printQuantity`; it receives the server-selected amount, Payment Intent ID, client key, and non-secret PayMongo public key.
2. Create a `qrph` Payment Method and attach it to the Payment Intent on the device using PayMongo's public-key workflow. The Android app must never use the secret key.
3. Poll `payment_status` only while waiting for the authoritative webhook, then call `claim_session` after verified payment. A claimed order returns one stable session UUID; replays resume it rather than opening a second session.
4. Call `complete_session` after a successful capture/print workflow.

For the tablet build/provisioning sequence and physical test procedure, use `PAYMONGO_PHASE_4_STAGING_CHECKLIST.md`.

## Release gate

Do not switch `PAYMONGO_ENVIRONMENT` to `live` until test-mode payment, duplicate webhook delivery, invalid-signature rejection, delayed-webhook recovery, expired QR handling, and a full physical booth session have all passed. Use a separate live webhook endpoint and a separately rotated live secret.
