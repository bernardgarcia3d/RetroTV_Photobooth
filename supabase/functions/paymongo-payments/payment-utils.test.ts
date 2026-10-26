import {
  parsePaymongoSignature,
  parsePaymongoWebhookEvent,
  safeEqual,
  verifyPaymongoWebhookSignature,
} from "./payment-utils.ts";

Deno.test("verifies a timestamped test-mode PayMongo signature", async () => {
  const payload = '{"data":{"id":"evt_1"}}';
  const secret = "whsec_test";
  const header = "t=1700000000,te=8a2a40196c31774bd12d2506d377e1fc78f28cdcd2e79a43e43f2ef9bf9b9bd4,li=";
  const verified = await verifyPaymongoWebhookSignature(payload, header, secret, "test", 300, 1700000000);
  const rejectedForLive = await verifyPaymongoWebhookSignature(payload, header, secret, "live", 300, 1700000000);
  if (!verified || rejectedForLive) throw new Error("signature verification did not select the correct environment signature");
});

Deno.test("rejects a replayed or malformed signature", async () => {
  const payload = "{}";
  const header = "t=1700000000,te=0000000000000000000000000000000000000000000000000000000000000000,li=";
  const replayed = await verifyPaymongoWebhookSignature(payload, header, "whsec_test", "test", 300, 1700000401);
  if (replayed || parsePaymongoSignature("t=not-a-time,te=123,li=", "test") || safeEqual("abc", "ab")) {
    throw new Error("invalid signatures must not be accepted");
  }
});

Deno.test("extracts the payment intent from payment and intent events", () => {
  const payment = parsePaymongoWebhookEvent({
    data: { id: "evt_paid", attributes: { type: "payment.paid", livemode: false, data: { id: "pay_1", type: "payment", attributes: { payment_intent_id: "pi_1" } } } },
  });
  const intent = parsePaymongoWebhookEvent({
    data: { id: "evt_intent", attributes: { type: "payment_intent.succeeded", livemode: false, data: { id: "pi_2", type: "payment_intent", attributes: {} } } },
  });
  if (payment?.paymentIntentId !== "pi_1" || intent?.paymentIntentId !== "pi_2") throw new Error("payment intent extraction failed");
});
