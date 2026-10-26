# PayMongo Phase 1 baseline

Captured on 26 October 2026 before the separate PayMongo Android edition was added.

## Source state

- Git commit: `3f429a4e0c4722663a7eefd3cc32bfb9afb3241b`
- The working tree already contained local project work. No tag or commit was created because it would incorrectly include unreviewed work.
- Existing fallback: RetroTV PhotoBooth `v1.0.0`, documented in `RELEASES.md`.

## PayMongo readiness boundary

- No PayMongo API key or webhook secret has been added to source, Gradle configuration, or an APK.
- No PayMongo API request, live payment, refund, webhook registration, or deployment is performed by this milestone.
- Dynamic QR Ph creation and verified payment notifications remain Phase 3 work, where the server holds the merchant secret and validates webhook signatures.
- The `paymongo` Android flavor is a staging shell only. It can be installed beside the standard edition but cannot activate a paid session.

## Required external checks before Phase 3

1. Confirm the merchant account can create a dynamic QR Ph Payment Intent in PayMongo test mode.
2. Register a dedicated staging HTTPS webhook endpoint and retain its signing secret in the backend secret store.
3. Simulate success, failure, and expiry with PayMongo's documented QR Ph test workflow.
4. Record the actual live QR Ph fee, settlement timing, refund process, and provider expiry behavior.

Do not scan or pay a test QR code. Use PayMongo's test simulation URL for the QR flow.
