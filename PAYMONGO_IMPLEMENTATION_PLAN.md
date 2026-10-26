# RetroTV PhotoBooth — PayMongo QR Ph implementation phase plan

Date: 25 October 2026  
Status: Phases 1–4 implemented in local source; staging deployment and physical-payment validation remain outstanding  
Initial target: Existing Android tablet and 80 mm thermal printer  
Product outcome: A separately installable PayMongo edition that automatically activates one booth session after server-verified payment.

## Implementation snapshot — 26 October 2026

- **Phase 1:** baseline and secret-handling boundary recorded locally.
- **Phase 2:** side-by-side `standard` and `paymongo` Android editions implemented.
- **Phase 3:** Supabase migration, backend payment function, signature-verified webhook path, idempotency records, and single-session grants implemented locally.
- **Phase 4:** the PayMongo Android edition now performs preflight checks, server-priced order creation, public-key QR Ph attachment, QR display/countdown, bounded server status checks, session claim, and hand-off to the existing capture/print flow.

No migration, Edge Function, webhook, API secret, price tier, or APK has been deployed by these source changes. Phase 5 is still required for restart, late-payment, and print-failure recovery.

## Delivery decisions

- Maintain one source project with standard and PayMongo Android product flavors.
- Preserve the standard application ID. Give the PayMongo flavor its own application ID, app name, and private app data.
- Use dynamic QR Ph through Payment Intents, with the QR displayed directly on the tablet.
- Start with the existing shared print-quantity pricing and thermal output.
- Use dedicated Supabase payment functions and records, with a separate staging environment. Continue using the existing Cloudflare photo/GIF delivery path.
- Treat payment, session use, printing, and digital delivery as separate states.
- Complete the acceptance checks in each phase before starting work that depends on it.

Creating this plan does not authorize charges, refunds, production deployment, or changes to PayMongo account settings. Those actions belong to the implementation and controlled pilot described below.

## Current project baseline

The following was checked in the local project:

| Area | Current implementation | Implication |
| --- | --- | --- |
| Android build | One application ID in app/build.gradle.kts; no product flavors declared | Separate-install support must be added |
| Operating modes | RENTAL, BUSINESS, and CODE_ACCESS in BoothConfiguration.kt | Preserve the existing standard routes |
| Business entry | PaymentScreen advances to BusinessPinScreen, then layout selection | PayMongo requires a verified-payment entry route |
| Payment UI | Locally uploaded QR images and a test-confirmation action | This UI does not verify a provider payment |
| Pricing | Quantity tiers for 1–10 prints, stored locally | Publish authorized prices to the backend before selling |
| Local sessions | Media, print quantity, layout, and code-access metadata | Add an independent pending-payment store before capture starts |
| Printing | Persisted PrintJob and PrintStatus models | Link paid orders to durable print jobs and recovery |
| Backend | Supabase code-access service and booth registry | Reuse infrastructure while separating payment authorization |
| Media delivery | Cloudflare Worker/R2 with booth-scoped delivery | Link delivery to the original session without coupling it to payment truth |
| Fallback | RELEASES.md records a v1.0.0 APK/source recovery point | Capture the actual current working baseline before implementation |

Primary integration files:

- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/eventphotobooth/app/ui/EventPhotoboothApp.kt
- app/src/main/java/com/eventphotobooth/app/data/BoothConfiguration.kt
- app/src/main/java/com/eventphotobooth/app/data/PhotoboothModels.kt
- app/src/main/java/com/eventphotobooth/app/data/LocalSessionStore.kt
- app/src/main/java/com/eventphotobooth/app/printer/LocalPrintQueue.kt
- supabase/functions/photo-access/index.ts
- cloudflare/src/index.js

Paths identify integration points, not a requirement to rewrite all these files. Keep new payment logic in dedicated modules and extract only the UI boundaries needed.

## Phase overview

| Phase | Purpose | Reviewable result | Required exit condition |
| --- | --- | --- | --- |
| 1 | Establish baseline and provider readiness | Recovery package and API contract notes | Current app can be restored; dynamic API path is proven in staging |
| 2 | Create the separate Android edition | Standard and PayMongo staging APKs | Both install together and keep separate settings |
| 3 | Build secure payment services | Orders, payment attempts, verified notifications, session grants | Only a valid matching payment can authorize one session |
| 4 | Connect the guest checkout | Quantity → QR → confirmation → layout → capture | One simulated payment completes the normal booth flow |
| 5 | Make payment and fulfillment recoverable | Restart recovery, late-payment handling, print recovery | Failure cases preserve the original order without duplicate use |
| 6 | Give the operator control | Payment readiness, order history, recovery and reconciliation | Every paid order has an explainable fulfillment/refund state |
| 7 | Verify the complete staging release | Test evidence and release candidate | Required backend and real-tablet checks pass |
| 8 | Run the controlled live pilot | Signed pilot APK, transaction reconciliation, release record | Pilot payments and booth output reconcile with no unexplained paid orders |

## Phase 1 — Protect the current build and verify PayMongo readiness

### Work

1. Record the current source revision, build toolchain, installed tablet version, and printer configuration.
2. Preserve a reinstallable working APK and a sanitized configuration export. Keep provisioning credentials in their existing secure owner-controlled location.
3. Verify the fallback can be installed and the normal camera/print flow can run. Do not assume the older recorded release includes all recent changes.
4. Confirm the merchant account has access to the intended dynamic QR Ph API in both required environments. Receiving money through a static QR or using withdrawals does not prove this API path.
5. Record the exact provider contract for:
   - Payment Intent creation, QR payment-method creation, attachment, and status retrieval.
   - QR image representation and provider expiry behavior.
   - Payment and expiry notification payloads, identifiers, signatures, and environment markers.
   - Supported request idempotency and safe recovery after a create-request timeout.
   - Cancellation/expiry options and what happens if a QR was already scanned.
   - Refund capabilities and the account's actual fees.
6. Use a minimal staging HTTPS receiver and PayMongo's documented simulation URL to prove notification delivery. Follow the QR-specific testing instructions in [S3]; do not scan/pay development QR codes.
7. Set the proposed kiosk payment window to 180 seconds only after verifying provider configuration and behavior. A local timer must never claim the provider request was cancelled.

### Deliverables

- Current baseline and restore instructions.
- Sanitized API examples and event fixtures.
- Confirmed environment/provisioning checklist, with unresolved provider limitations recorded.

### Acceptance checks

- A working current APK and settings recovery path exist.
- The intended account can create a dynamic QR in the development flow.
- Simulation produces the expected payment status and notification.
- Timeout, cancellation, expiry, and retry semantics are documented.
- No merchant secret is included in source, screenshots, general logs, or the APK.

## Phase 2 — Create separate standard and PayMongo builds

### Work

1. Add a product-flavor dimension with standard and paymongo variants.
2. Preserve com.eventphotobooth.app for standard. Proposed PayMongo ID: com.eventphotobooth.app.paymongo.
3. Use the display name RetroTV PhotoBooth PayMongo and a distinguishable launcher icon.
4. Review manifest providers, app links, exported components, backup rules, and generated file names for application-ID conflicts.
5. Separate staging/live backend configuration. Keep environment switching out of guest controls and prevent test events from authorizing live orders.
6. Introduce a small payment-client interface and a separate PayMongo route. The standard flavor retains its current operating modes.
7. Keep new payment state out of the large root composable. Use a dedicated payment controller/view model, UI, repository, and durable pending-order store.
8. Provision the new APK deliberately; do not assume it inherits settings or credentials from the standard APK.

### Deliverables

- Two installable staging/debug APKs.
- Flavor-aware configuration and documented build commands.
- A PayMongo checkout shell that cannot grant a paid session yet.

### Acceptance checks

- Both editions install side by side on the tablet.
- Changes to one edition's operator settings do not change the other edition.
- Standard Rental, Business/PIN, and Code Access flows still work.
- PayMongo paid entry cannot be unlocked through the existing test-confirmation action or ordinary Business PIN.
- The PayMongo staging edition clearly identifies its test environment.

## Phase 3 — Build secure order and payment services

### Work

1. Prepare a separate payment staging environment and dedicated payment functions. Review existing tables and policies before making schema changes; generate migrations with the supported Supabase tooling.
2. Authenticate each booth with a revocable, scoped device credential. A publishable Supabase key, booth ID alone, or a guest-supplied order ID is not proof of authorization.
3. Publish operator-approved print prices to the backend through an owner/operator-authorized path. The tablet sends a quantity/product selection, while the server determines the payable total.
4. Create an immutable order snapshot containing booth, environment, quantity, product, currency, integer-centavo amount, price version, and timestamps.
5. Store payment attempts separately so retries and provider IDs can be traced without losing the original order.
6. Create and attach the PayMongo resources server-side. Retain provider resource IDs and creation/retry keys before advancing the state.
7. Verify notifications using the current documented signature algorithm and raw request body. Validate the environment, provider references, amount, currency, and order ownership.
8. Persist verified event receipt durably before acknowledging success. Process events transactionally or through durable retryable work; do not depend on an in-memory task surviving an acknowledgement.
9. Deduplicate events and payment requests, including concurrent deliveries. Prevent older failed/expired events from downgrading a confirmed successful payment.
10. Add controlled provider-status reconciliation for delayed or missing notifications. Both notification processing and reconciliation must use the same validation and state transitions.
11. Atomically associate one session grant with a paid order. A repeated claim returns the same session ID; it never creates another entitlement.
12. Restrict database access so guests/devices cannot directly set paid, alter amounts, read other booths' orders, or issue session grants. Enable appropriate RLS and review privileged access.
13. Reject excessive quantities, disabled booths, revoked devices, stale prices, conflicting retry payloads, and unauthorized administrative operations.

### Proposed data responsibilities

| Record | Responsibility |
| --- | --- |
| Authorized pricing/configuration | Current server-approved product and quantity prices |
| Orders | Frozen purchase details and financial outcome |
| Payment attempts | Provider resources, attempt expiry, retry identity, and uncertainty |
| Verified webhook events | Deduplication and durable processing outcome |
| Session grants | One authorized, resumable booth session per order |
| Operator audit | Recovery, complimentary use, refund records, and privileged actions |

These are conceptual records; Phase 3 should choose the smallest practical schema after reviewing the existing database.

### Acceptance checks

- A valid simulated payment marks only its matching staging order paid.
- Incorrect amount, currency, environment, signature, or provider linkage grants nothing.
- Repeated/concurrent notifications and repeated claims produce one session grant.
- Booth A cannot view or claim Booth B's order.
- Client-supplied prices cannot reduce the charge.
- A provider timeout is classified as uncertain and reconciled; it does not trigger blind duplicate creation.
- Privileged keys remain in backend secrets. [S4, S5]

## Phase 4 — Implement the guest checkout

### Guest sequence

1. Start session.
2. Confirm camera permission, usable camera preview, known printer connection/permission, and backend readiness.
3. Choose print quantity and review the backend-confirmed total.
4. Create/reuse the order and display its dynamic QR.
5. Show the amount, quantity, order reference, instructions, and authoritative expiry countdown.
6. While confirming, check the booth backend using bounded polling/backoff; avoid direct tablet polling of PayMongo.
7. On server-confirmed paid status, claim and persist the authorized session.
8. Open layout selection, camera capture, customization, printing, and digital delivery for that session.

Printer readiness must distinguish detected USB connectivity from actual paper availability. Use operator setup/test-print confirmation where hardware cannot report paper status.

### UI states

- Checking availability.
- Preparing payment.
- Waiting for payment.
- Confirming payment.
- Payment received.
- Expired or cancelled attempt.
- Connection interrupted.
- Recovery needed.

### Work

- Freeze order quantity/amount once the QR is issued. Changes start a controlled replacement flow after checking the previous attempt.
- Prevent repeated taps from creating multiple active attempts.
- Remove the ordinary staff-PIN step after a verified PayMongo payment.
- Keep any staff-authorized complimentary session visibly separate and audited; it never marks a PayMongo order paid.
- Persist the pending order before capture, including the backend order/session association.
- Limit layout choices to those compatible with the purchased product.
- Display customer-friendly messages without provider secrets or full API responses.

### Acceptance checks

- A simulated payment completes the camera/thermal-print flow.
- Pending, failed, expired, and unknown payments cannot enter paid capture.
- An “I have paid” action, if included, only requests another status check.
- QR and payment text are usable in both tablet orientations.
- Recomposition, navigation, or repeated taps cannot create a second charge or grant.

## Phase 5 — Implement restart, late-payment, and fulfillment recovery

### State rules

Keep these concerns independent:

| Concern | Example states |
| --- | --- |
| Payment attempt | Creating, pending, uncertain, succeeded, failed, expired |
| Order lifecycle | Open, abandoned, paid, refund pending, partially refunded, refunded |
| Booth session | Unclaimed, claimed, capturing, ready to print, completed, attention needed |
| Print job | Queued, submitting, submitted, failed, outcome uncertain |
| Digital delivery | Local, pending upload, ready, failed |

Names may adapt to existing models. Their meanings must remain distinct. In particular, the existing PRINTED label must not imply physical paper delivery unless the printer actually confirms it.

### Work

1. On restart, reconcile pending orders and recover the original session ID.
2. Persist the claim/session association before camera capture. If a crash occurs before that local write, recover the same claim from the server.
3. Allow an already authorized and locally persisted active session to continue capturing/printing through temporary connection loss. New paid activation still requires backend verification.
4. Route late successful payments to the original order. An abandoned order can still receive financial reconciliation; it must not activate the next guest.
5. Require deliberate recovery/operator action where the original guest can no longer be identified. Do not auto-resume a previous guest's paid session to a new walk-up.
6. Reconcile existing attempts before creating replacements. Handle multiple successful attempts for one order as a duplicate-payment exception for refund review, with only one entitlement.
7. Link the paid order and session to one durable print job. Retry failed work from that job.
8. Record ambiguous USB submission separately. A lost connection after bytes were sent can leave the physical output uncertain; require operator review before retrying.
9. Persist progress for multi-copy jobs where feasible and show the remaining/uncertain copy count.
10. Keep the payment notification endpoint and reconciliation path running while paid orders remain unresolved.

### Acceptance checks

- Force-stop before payment, during confirmation, after claim, and before/after print submission.
- Every recovery retains the original order, session, quantity, and captured media.
- Payment state never regresses because of an older event.
- Duplicate payments or events do not create extra sessions.
- Printer failure preserves the paid order without asking the guest to pay again.
- Automatic recovery does not claim to guarantee exactly-once physical printing over USB.

## Phase 6 — Add operator payment controls and reconciliation

### Work

- Show payment-service connectivity, booth provisioning, environment, and last successful check.
- Provide a scoped order list with pending, paid, unfulfilled, completed, refund, and attention filters.
- Show amount, copy count, time, order reference, provider reference, and fulfillment outcome.
- Add controlled actions to recheck payment, resume a paid session, retry a print, and record an operator-assisted recovery.
- Record operator identity or available staff context, reason, time, and result for privileged actions.
- Support owner-controlled pricing publication; a change affects future orders and never changes an existing snapshot.
- Handle refunds through the PayMongo Dashboard for the first release. Record the provider reference and verify the refund result; “requested” is not “refunded.”
- Reconcile counts and amounts against PayMongo: gross paid, refunds, outstanding paid orders, fees where available, and completed/uncertain prints.
- Keep complimentary sessions, payments, fees, and wallet withdrawals separate in reporting. Use actual account rates rather than an assumed fee percentage.
- Add a control to pause new orders while retaining visibility and recovery for existing ones.

### Acceptance checks

- Every paid order can be traced to a session, unresolved recovery, or verified refund outcome.
- An operator can recover a paid session without generating another purchase.
- Invalid/revoked staff or device access cannot modify payment state.
- Daily totals reconcile with the corresponding provider transactions.

## Phase 7 — Verify the full staging release

Run meaningful tests during each implementation phase; this phase completes end-to-end verification.

### Required test matrix

| Scenario | Expected outcome |
| --- | --- |
| Successful simulated payment | One order, one grant, correct copy count |
| Repeated Start/Pay/claim requests | Stable request result; no extra grant |
| Forged notification | Rejected without changing payment state |
| Wrong booth or environment | Access/authorization rejected |
| Wrong amount/currency/provider reference | Exception recorded; no grant |
| Duplicate/out-of-order notifications | Correct final state, no duplicate use |
| Provider create timeout | Unknown result reconciled before replacement |
| QR timeout, cancellation, or saved QR paid later | Original attempt/order reconciled; next guest remains isolated |
| Backend/tablet network loss | Clear state and recovery; no false payment success |
| Tablet restart around session claim | Same session restored |
| Camera permission/device failure | Payment preflight or paid-session recovery |
| Paper/USB failure before submission | Durable retry on the existing job |
| USB loss after possible submission | Uncertain outcome shown for operator review |
| App restart during multi-copy print | Remaining/uncertain copies explained |
| Photo/GIF upload failure | Payment retained and delivery retried independently |
| Price update during checkout | Existing order keeps its frozen total |
| Duplicate successful payment attempts | One grant; duplicate payment flagged for refund review |
| Standard edition regression | Rental, Business/PIN, Code Access, capture, and printing still work |
| Side-by-side installation | Separate settings, identifiers, and correct media association |

### Deliverables

- Backend test results, including real concurrency/deduplication checks.
- Physical-tablet walkthrough results with real USB printing.
- Sanitized evidence for provider simulation and order reconciliation.
- Release candidate, version/signing details, known limitations, and recovery instructions.

### Acceptance checks

- All required security, payment, and recovery cases pass.
- No unresolved case can create an unauthorized paid session or silently lose a paid order.
- Both APKs build successfully and pass the relevant tablet regression checks.
- QR scanning is checked under actual booth lighting using an appropriate non-payment scanning check or controlled live pilot.
- No deployment or live-payment success is claimed from a compile-only result.

## Phase 8 — Controlled live pilot and handoff

### Work

1. Provision live backend secrets and a live payment-notification endpoint through authorized account access.
2. Verify the account's live QR Ph method, fees, and refund/reconciliation process.
3. Install the signed PayMongo pilot APK beside the standard edition.
4. Complete a small controlled live payment, then verify its exact order, session grant, output, provider record, and refund workflow where supported.
5. Pilot on the actual tablet/thermal printer. Proposed exit target: ten consecutive full sessions and at least two available participating payment apps.
6. Observe confirmation time, scanning problems, abandonments, paid-but-unfulfilled orders, print exceptions, and operator interventions.
7. Reconcile all pilot money and outcomes before making the build the default booth experience.
8. Record the APK checksum/version, source revision, backend/migration versions, configuration, and documented limitations.

### Acceptance checks

- All pilot payments match their original orders and booth sessions.
- No unexplained paid order, duplicate entitlement, or unexplained output remains.
- A paid session can be recovered after a controlled tablet restart.
- Printer recovery and the refund process have been demonstrated.
- The standard APK and baseline recovery package remain available.

### Rollback procedure

1. Pause new PayMongo orders server-side.
2. Retain notification receipt, payment reconciliation, and operator access.
3. Reconcile pending attempts and recover/refund existing paid obligations.
4. Record outstanding print/delivery work before switching the kiosk to the standard edition.
5. Restore the known-good APK/configuration if needed.
6. Keep backend schema changes additive so restoring the app does not require destructive database rollback.

## Scope for the first release

Included:

- Separate PayMongo Android edition.
- Dynamic QR Ph checkout for the existing quantity pricing.
- Server-verified payment, one resumable session, and thermal printing.
- Recovery, operator visibility, reconciliation, and recorded Dashboard-based refund handling.
- Existing photo/GIF delivery linked to the paid session.

Deferred:

- Automated wallet withdrawals and payouts.
- Cards, separate e-wallet redirect checkouts, and subscriptions.
- Revenue splitting between multiple merchants.
- A separate web administration product.
- Automated refund issuance from the tablet.
- Canon SELPHY 4R printing and its separate templates.
- Paid add-ons or extra purchases after a session has started.

For future 4R support, retain product/print-profile and price-version fields now. The backend must reject 4R purchases until that output path is implemented and validated.

## Suggested implementation modules

| Area | Proposed responsibility |
| --- | --- |
| payment/PaymentModels.kt | Order, payment, authorization, and recovery state |
| payment/PaymentClient.kt | Authenticated calls to the booth payment backend |
| payment/PaymentRepository.kt | Order creation, status reconciliation, and claim |
| payment/PaymentViewModel.kt | Guest checkout state and lifecycle |
| payment/PayMongoPaymentScreen.kt | QR, countdown, progress, and recovery messages |
| payment/PendingPaymentStore.kt | Durable pending order and authorized-session association |
| Operator payment UI | Order visibility and scoped recovery actions |
| Dedicated Supabase payment functions | Order/pricing validation, provider API calls, notification handling, grants, reconciliation |
| Additive database migrations | Payment records, constraints, authorization, audit |
| Existing session/print stores | Optional order/session links and durable fulfillment |

Confirm names against project conventions before creating files. Do not perform a broad UI or storage rewrite as a prerequisite.

## First implementation task

Implement Phase 1 and Phase 2 as the first reviewable milestone. Deliver the baseline recovery record, verified provider contract, separate standard and PayMongo staging APKs, and a checkout shell that cannot unlock a paid session. Run the relevant build and tablet checks. If account access or the build toolchain is unavailable, record the exact incomplete acceptance check; do not replace verification with an assumption.

## Documentation references

Provider and platform documentation checked on 25 October 2026. Recheck the relevant API contracts before implementation.

- [S1 — PayMongo dynamic QR Ph API](https://docs.paymongo.com/docs/payment-acceptance-qr-ph-api)
- [S2 — PayMongo QR Ph confirmation/events](https://docs.paymongo.com/docs/payment-acceptance-qr-ph)
- [S3 — PayMongo testing, including the QR-specific simulation instructions](https://docs.paymongo.com/docs/payment-acceptance-testing)
- [S4 — PayMongo API keys, webhook verification, idempotency, and security](https://docs.paymongo.com/docs/developer-tools-best-practices-1)
- [S5 — Supabase Edge Function secrets](https://supabase.com/docs/guides/functions/secrets)
- [S6 — PayMongo webhook endpoint registration](https://docs.paymongo.com/docs/creating-a-webhook-endpoint)
