# PayMongo Phase 4 Staging Checklist

Phase 4 is implemented in the separate `paymongoDebug` Android edition. It does not deploy infrastructure or enable live payments.

## What the tablet does

1. Requires camera permission, exactly one USB receipt printer with permission, a configured booth ID/key, and a healthy payment backend.
2. Sends only the selected print quantity and a UUID request ID to the backend. The backend returns the frozen PHP total and the Payment Intent client data.
3. Uses the PayMongo **public** key and client key to create/attach a `qrph` Payment Method with a 180-second expiry. No secret key is in the APK.
4. Displays the returned QR, asks the backend for bounded payment-status checks, then claims one server-verified session before opening layout selection.
5. Carries the claimed session UUID into the current capture/print flow and completes it with the backend only after a successful print.

The standard edition remains outside this flow. The PayMongo edition also blocks the old Rental, code-access, and Business-PIN entry paths.

## Staging setup

Complete the backend runbook first, then build the PayMongo edition with the **Supabase project base URL** (not the function URL):

```powershell
.\gradlew.bat :app:assemblePaymongoDebug -PpaymongoStagingBackendUrl=https://<project-ref>.supabase.co
```

On the tablet, open Owner Settings → Booth Provisioning and set the existing:

- Booth ID to the active `photo_booths.booth_id`.
- RetroRoll code key to the same secret value as `BOOTH_SHARED_KEY` in the staging Edge Function environment.
- Existing Cloudflare fields required by the provisioning screen, if they are not already configured.

The UI checks USB permission but cannot determine whether paper is loaded. Confirm paper, cover, power, and the correct printer manually before any test payment.

## Test sequence

1. Install the PayMongo staging APK beside the standard APK.
2. Confirm it identifies itself as `STAGING CHECKOUT` and refuses to continue if the camera, printer, booth key, or backend is unavailable.
3. Choose a print quantity. Confirm the displayed total matches the active backend price tier, not the locally configured price.
4. Generate a QR and use the PayMongo test process. Do not use live keys or a live payment.
5. Confirm payment via the server. The camera/layout flow must remain locked until `payment_status` reports a verified payment and `claim_session` succeeds.
6. Complete a full capture and successful thermal print. Confirm the paid order receives one session claim and then a completed session.
7. Repeat a status check, duplicate webhook delivery, and repeat tap. There must still be exactly one session grant.
8. Let a QR expire. Generate a new QR only for the same frozen order; do not create a replacement order or change its quantity/amount.

## Known staging boundary

- No migration, Edge Function, PayMongo webhook, secret, price tier, or APK has been deployed by this source change.
- A claimed session remaining after an app restart is intentionally shown as recovery-required. Phase 5 will add operator-safe restart and late-payment recovery rather than silently assigning that paid session to a new guest.
- Do not move `PAYMONGO_ENVIRONMENT` to `live` until all Phase 7 test cases pass on the actual tablet and printer.
