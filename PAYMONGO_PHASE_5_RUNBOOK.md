# PayMongo Phase 5 — Recovery and Fulfillment

## Implemented behavior

- A server-verified session claim remains in the local pending-order record across an app restart.
- A claimed session is never resumed for a new walk-up guest. The PayMongo checkout displays a dedicated recovery screen and requires the operator to deliberately select **Resume Original Paid Session** while the original guest is present.
- The recovery action restores the existing order ID, session ID, quantity, and frozen amount. It does not create a charge or a second session grant.
- A QR expiry retains the original order locally. The checkout rechecks the original server order before it creates another QR, so a late payment is attached to its original order rather than the next guest.
- Print jobs persist their quantity, completed-copy count, submission attempts, and a separate physical-output outcome.
- USB submission is classified separately from confirmed paper delivery. A USB interruption after submission begins is **outcome uncertain** and is blocked from automatic retry until an operator checks the paper.
- Multi-copy jobs persist progress after each accepted USB submission. After restart, the job record shows the completed and remaining copies instead of blindly starting the whole print again.

## Operator recovery rules

| Situation | Safe action |
| --- | --- |
| App restarts before payment | Reopen checkout; it reconciles the original order before issuing a QR. |
| Guest paid after QR expired | Reopen the original pending order and check payment status; never start a new guest on that order. |
| App restarts after session claim | Confirm the original guest is present, then choose **Resume Original Paid Session**. |
| No printer before a submission starts | The job can be retried from the same paid session. |
| USB drops while a print is being submitted | Inspect the output; do not retry automatically. Record an operator decision before any retry. |
| One copy of a multi-copy job succeeds | Resume only the remaining copies. |

## Important limitation

Android USB printing cannot prove exactly-once physical paper delivery on these receipt printers. The app therefore never interprets a completed USB write as a hardware sensor-confirmed print. The records distinguish `SUBMITTED` from confirmed physical fulfillment, and an uncertain connection loss always needs operator review.

## Test matrix to run on the real tablet

1. Force-stop before payment, while QR is visible, after payment is confirmed, after session claim, and during each print copy.
2. Simulate a late payment after the QR countdown reaches zero; ensure it can only reconcile to the original order.
3. Unplug USB before a submission starts; verify the paid session is retained and the print can retry.
4. Unplug USB during a large strip submission; verify the job enters operator-attention state and cannot auto-retry.
5. Submit a three-copy job, stop after the first copy, restart the app, and confirm that only remaining copies are offered after review.

