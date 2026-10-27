import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  parsePaymongoWebhookEvent,
  safeEqual,
  sha256Hex,
  verifyPaymongoWebhookSignature,
} from "./payment-utils.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-booth-key",
  "Content-Type": "application/json",
};
const maxRequestBytes = 64 * 1024;

type Environment = "test" | "live";
type PaymentOrder = {
  id: string;
  booth_id: string;
  client_request_id: string;
  print_quantity: number;
  amount_minor: number;
  currency: string;
  price_version: number;
  status: string;
  paymongo_payment_intent_id: string | null;
  paid_at: string | null;
};
type ProviderPaymentIntent = {
  id: string;
  amount: number;
  currency: string;
  status: string;
  clientKey: string | null;
};
type PaymentConfig = {
  environment: Environment;
  secretKey: string;
  publicKey: string;
  webhookSecret: string;
  apiBaseUrl: string;
  webhookToleranceSeconds: number;
  sessionGrantTtlSeconds: number;
};
type BoothCredential = {
  id: string;
  booth_id: string;
};

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

class ProviderError extends Error {}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function webhookJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function integerEnv(name: string, fallback: number, minimum: number, maximum: number) {
  const raw = Deno.env.get(name);
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new HttpError(500, `${name} is not configured safely`);
  }
  return value;
}

function paymentConfig(): PaymentConfig {
  const environment = Deno.env.get("PAYMONGO_ENVIRONMENT");
  if (environment !== "test" && environment !== "live") {
    throw new HttpError(500, "Payment service is not configured");
  }
  const secretKey = Deno.env.get("PAYMONGO_SECRET_KEY");
  const publicKey = Deno.env.get("PAYMONGO_PUBLIC_KEY");
  const webhookSecret = Deno.env.get("PAYMONGO_WEBHOOK_SECRET");
  if (!secretKey || !publicKey || !webhookSecret) {
    throw new HttpError(500, "Payment service is not configured");
  }
  const apiBaseUrl = Deno.env.get("PAYMONGO_API_BASE_URL") ?? "https://api.paymongo.com";
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(apiBaseUrl);
  } catch {
    throw new HttpError(500, "Payment service is not configured");
  }
  if (parsedUrl.protocol !== "https:" || parsedUrl.hostname !== "api.paymongo.com") {
    throw new HttpError(500, "Payment service is not configured");
  }
  return {
    environment,
    secretKey,
    publicKey,
    webhookSecret,
    apiBaseUrl: parsedUrl.origin,
    webhookToleranceSeconds: integerEnv("PAYMONGO_WEBHOOK_TOLERANCE_SECONDS", 300, 30, 3600),
    sessionGrantTtlSeconds: integerEnv("PAYMONGO_SESSION_GRANT_TTL_SECONDS", 600, 60, 3600),
  };
}

function paymentConfigured() {
  try {
    paymentConfig();
    return true;
  } catch {
    return false;
  }
}

function serviceClient() {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) throw new HttpError(500, "Payment service is not configured");
  return createClient(url, key);
}

function validBoothId(value: string) {
  return /^[A-Za-z0-9_-]{1,64}$/.test(value);
}

function validRequestId(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function validPaymentIntentId(value: string) {
  return /^pi_[A-Za-z0-9]+$/.test(value);
}

function requiredString(value: unknown, name: string) {
  if (typeof value !== "string" || !value.trim()) throw new HttpError(400, `${name} is required`);
  return value.trim();
}

function boundedPrintQuantity(value: unknown) {
  const quantity = Number(value);
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10) throw new HttpError(400, "Invalid print quantity");
  return quantity;
}

async function validateBooth(client: ReturnType<typeof createClient>, boothId: string) {
  if (!validBoothId(boothId)) throw new HttpError(403, "Invalid booth");
  const { data, error } = await client.from("photo_booths")
    .select("booth_id")
    .eq("booth_id", boothId)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(403, "Booth is not registered or is disabled");
}

/**
 * A dedicated credential is required for operator price administration. The
 * legacy shared key remains accepted for the existing checkout protocol while
 * already-provisioned kiosks are migrated, but it cannot edit prices.
 */
async function authenticateBooth(
  client: ReturnType<typeof createClient>,
  boothId: string,
  suppliedKey: string | null,
  requireDedicatedCredential = false,
): Promise<BoothCredential | null> {
  await validateBooth(client, boothId);
  if (!suppliedKey || suppliedKey.length < 16) throw new HttpError(401, "Unauthorized booth");
  const suppliedHash = await sha256Hex(suppliedKey);
  const { data, error } = await client.from("photo_payment_booth_credentials")
    .select("id, booth_id")
    .eq("booth_id", boothId)
    .eq("device_key_sha256", suppliedHash)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (data) return data as BoothCredential;

  if (!requireDedicatedCredential) {
    const legacyKey = Deno.env.get("BOOTH_SHARED_KEY");
    if (legacyKey && safeEqual(legacyKey, suppliedKey)) return null;
  }
  throw new HttpError(401, requireDedicatedCredential
    ? "This tablet has not been provisioned for operator pricing"
    : "Unauthorized booth");
}

async function activePriceTiers(client: ReturnType<typeof createClient>, boothId: string) {
  const { data, error } = await client.from("photo_payment_price_tiers")
    .select("print_quantity, amount_minor, currency, version")
    .eq("booth_id", boothId)
    .eq("active", true)
    .order("print_quantity", { ascending: true });
  if (error) throw error;
  return (data ?? []).map((tier) => ({
    printQuantity: tier.print_quantity,
    amountMinor: tier.amount_minor,
    currency: tier.currency,
    version: tier.version,
  }));
}

function priceTiersFromRequest(value: unknown) {
  if (!Array.isArray(value) || value.length < 1 || value.length > 10) {
    throw new HttpError(400, "Between one and ten price tiers are required");
  }
  const seen = new Set<number>();
  return value.map((item) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) throw new HttpError(400, "Invalid price tier");
    const record = item as Record<string, unknown>;
    const printQuantity = Number(record.printQuantity);
    const amountMinor = Number(record.amountMinor);
    if (!Number.isInteger(printQuantity) || printQuantity < 1 || printQuantity > 10 ||
      !Number.isInteger(amountMinor) || amountMinor < 100 || amountMinor > 2_000_000 || seen.has(printQuantity)) {
      throw new HttpError(400, "Invalid price tier");
    }
    seen.add(printQuantity);
    return { printQuantity, amountMinor };
  }).sort((left, right) => left.printQuantity - right.printQuantity);
}

async function paymongoRequest(config: PaymentConfig, path: string, init: RequestInit) {
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      Authorization: `Basic ${btoa(`${config.secretKey}:`)}`,
      ...(init.headers ?? {}),
    },
  });
  const text = await response.text();
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      if (response.ok) throw new ProviderError("Payment provider returned an invalid response");
    }
  }
  if (!response.ok) throw new ProviderError(`Payment provider request failed (${response.status})`);
  return parsed;
}

function providerPaymentIntent(payload: unknown): ProviderPaymentIntent {
  if (!payload || typeof payload !== "object") throw new ProviderError("Payment provider returned an invalid response");
  const data = (payload as Record<string, unknown>).data;
  if (!data || typeof data !== "object") throw new ProviderError("Payment provider returned an invalid response");
  const record = data as Record<string, unknown>;
  const attributes = record.attributes;
  if (
    typeof record.id !== "string" || !validPaymentIntentId(record.id) || !attributes || typeof attributes !== "object"
  ) throw new ProviderError("Payment provider returned an invalid response");
  const values = attributes as Record<string, unknown>;
  if (typeof values.amount !== "number" || typeof values.currency !== "string" || typeof values.status !== "string") {
    throw new ProviderError("Payment provider returned an invalid response");
  }
  return {
    id: record.id,
    amount: values.amount,
    currency: values.currency,
    status: values.status,
    clientKey: typeof values.client_key === "string" ? values.client_key : null,
  };
}

async function createProviderPaymentIntent(config: PaymentConfig, order: PaymentOrder) {
  const response = await paymongoRequest(config, "/v1/payment_intents", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": order.id,
    },
    body: JSON.stringify({
      data: {
        attributes: {
          amount: order.amount_minor,
          currency: order.currency,
          capture_type: "automatic",
          payment_method_allowed: ["qrph"],
          description: `RetroRoll photo booth ${order.booth_id} order ${order.id}`,
          metadata: {
            photo_booth_order_id: order.id,
            booth_id: order.booth_id,
          },
        },
      },
    }),
  });
  const intent = providerPaymentIntent(response);
  if (intent.amount !== order.amount_minor || intent.currency !== order.currency || !intent.clientKey) {
    throw new ProviderError("Payment provider returned an unexpected payment intent");
  }
  return intent;
}

async function retrieveProviderPaymentIntent(config: PaymentConfig, paymentIntentId: string) {
  if (!validPaymentIntentId(paymentIntentId)) throw new ProviderError("Payment provider returned an invalid payment intent ID");
  return providerPaymentIntent(await paymongoRequest(config, `/v1/payment_intents/${encodeURIComponent(paymentIntentId)}`, { method: "GET" }));
}

async function activePriceTier(client: ReturnType<typeof createClient>, boothId: string, printQuantity: number) {
  const { data, error } = await client.from("photo_payment_price_tiers")
    .select("amount_minor, currency, version")
    .eq("booth_id", boothId)
    .eq("print_quantity", printQuantity)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(409, "No active price is configured for this print quantity");
  return data as { amount_minor: number; currency: string; version: number };
}

async function findOrder(client: ReturnType<typeof createClient>, orderId: string, boothId: string) {
  const { data, error } = await client.from("photo_payment_orders")
    .select("id, booth_id, client_request_id, print_quantity, amount_minor, currency, price_version, status, paymongo_payment_intent_id, paid_at")
    .eq("id", orderId)
    .eq("booth_id", boothId)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(404, "Payment order not found");
  return data as PaymentOrder;
}

function checkoutResponse(order: PaymentOrder, publicKey: string, attempt: { paymongo_payment_intent_id: string | null; paymongo_client_key: string | null } | null) {
  return {
    orderId: order.id,
    amountMinor: order.amount_minor,
    currency: order.currency,
    printQuantity: order.print_quantity,
    status: order.status,
    paymentIntentId: attempt?.paymongo_payment_intent_id ?? order.paymongo_payment_intent_id,
    clientKey: attempt?.paymongo_client_key ?? null,
    publicKey,
  };
}

async function latestAttempt(client: ReturnType<typeof createClient>, orderId: string) {
  const { data, error } = await client.from("photo_payment_attempts")
    .select("id, paymongo_payment_intent_id, paymongo_client_key, provider_status")
    .eq("order_id", orderId)
    .order("attempt_number", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return data as { id: string; paymongo_payment_intent_id: string | null; paymongo_client_key: string | null; provider_status: string | null } | null;
}

async function resumeCheckout(client: ReturnType<typeof createClient>, config: PaymentConfig, order: PaymentOrder) {
  let attempt = await latestAttempt(client, order.id);
  if (attempt?.paymongo_payment_intent_id && attempt.paymongo_client_key) {
    if (order.paymongo_payment_intent_id !== attempt.paymongo_payment_intent_id) {
      const now = new Date().toISOString();
      const { error } = await client.from("photo_payment_orders").update({
        paymongo_payment_intent_id: attempt.paymongo_payment_intent_id,
        status: "payment_pending",
        updated_at: now,
      }).eq("id", order.id).neq("status", "paid");
      if (error) throw error;
      order = { ...order, paymongo_payment_intent_id: attempt.paymongo_payment_intent_id, status: order.status === "paid" ? "paid" : "payment_pending" };
    }
    return checkoutResponse(order, config.publicKey, attempt);
  }

  if (!attempt) {
    const { data, error } = await client.from("photo_payment_attempts").insert({
      order_id: order.id,
      attempt_number: 1,
      idempotency_key: order.id,
      provider_status: "creating",
    }).select("id, paymongo_payment_intent_id, paymongo_client_key, provider_status").single();
    if (error && error.code !== "23505") throw error;
    attempt = data as typeof attempt;
    if (!attempt) attempt = await latestAttempt(client, order.id);
  }
  if (!attempt) throw new Error("Payment attempt could not be created");

  try {
    const intent = await createProviderPaymentIntent(config, order);
    const now = new Date().toISOString();
    const { error: attemptError } = await client.from("photo_payment_attempts").update({
      paymongo_payment_intent_id: intent.id,
      paymongo_client_key: intent.clientKey,
      provider_status: intent.status,
      last_error_code: null,
      updated_at: now,
    }).eq("id", attempt.id);
    if (attemptError) throw attemptError;
    const { error: orderError } = await client.from("photo_payment_orders").update({
      paymongo_payment_intent_id: intent.id,
      status: intent.status === "succeeded" ? "paid" : "payment_pending",
      updated_at: now,
    }).eq("id", order.id).neq("status", "paid");
    if (orderError) throw orderError;
    const updatedOrder = { ...order, paymongo_payment_intent_id: intent.id, status: intent.status === "succeeded" ? "paid" : "payment_pending" };
    if (intent.status === "succeeded") await markPaymentSucceeded(client, config, intent);
    return checkoutResponse(updatedOrder, config.publicKey, { ...attempt, paymongo_payment_intent_id: intent.id, paymongo_client_key: intent.clientKey });
  } catch (error) {
    await client.from("photo_payment_attempts").update({
      provider_status: "create_failed",
      last_error_code: error instanceof ProviderError ? "provider_create_failed" : "internal_create_failed",
      updated_at: new Date().toISOString(),
    }).eq("id", attempt.id);
    throw error;
  }
}

async function createOrder(client: ReturnType<typeof createClient>, config: PaymentConfig, boothId: string, printQuantity: number, requestId: string) {
  const { data: existing, error: existingError } = await client.from("photo_payment_orders")
    .select("id, booth_id, client_request_id, print_quantity, amount_minor, currency, price_version, status, paymongo_payment_intent_id, paid_at")
    .eq("booth_id", boothId)
    .eq("client_request_id", requestId)
    .maybeSingle();
  if (existingError) throw existingError;
  if (existing) return resumeCheckout(client, config, existing as PaymentOrder);

  const price = await activePriceTier(client, boothId, printQuantity);
  const { data, error } = await client.from("photo_payment_orders").insert({
    booth_id: boothId,
    client_request_id: requestId,
    print_quantity: printQuantity,
    amount_minor: price.amount_minor,
    currency: price.currency,
    price_version: price.version,
  }).select("id, booth_id, client_request_id, print_quantity, amount_minor, currency, price_version, status, paymongo_payment_intent_id, paid_at").single();
  if (error && error.code !== "23505") throw error;
  if (data) return resumeCheckout(client, config, data as PaymentOrder);

  const { data: racedOrder, error: racedError } = await client.from("photo_payment_orders")
    .select("id, booth_id, client_request_id, print_quantity, amount_minor, currency, price_version, status, paymongo_payment_intent_id, paid_at")
    .eq("booth_id", boothId)
    .eq("client_request_id", requestId)
    .single();
  if (racedError || !racedOrder) throw racedError ?? new Error("Payment order could not be recovered");
  return resumeCheckout(client, config, racedOrder as PaymentOrder);
}

async function createSessionGrant(client: ReturnType<typeof createClient>, order: PaymentOrder, ttlSeconds: number) {
  const expiresAt = new Date(Date.now() + ttlSeconds * 1000).toISOString();
  const { error } = await client.from("photo_payment_session_grants").insert({
    order_id: order.id,
    booth_id: order.booth_id,
    expires_at: expiresAt,
  });
  if (error && error.code !== "23505") throw error;
}

async function markPaymentSucceeded(client: ReturnType<typeof createClient>, config: PaymentConfig, intent: ProviderPaymentIntent) {
  const { data, error } = await client.from("photo_payment_orders")
    .select("id, booth_id, client_request_id, print_quantity, amount_minor, currency, price_version, status, paymongo_payment_intent_id, paid_at")
    .eq("paymongo_payment_intent_id", intent.id)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;
  const order = data as PaymentOrder;
  if (intent.amount !== order.amount_minor || intent.currency !== order.currency || intent.status !== "succeeded") {
    throw new ProviderError("Payment intent did not match its order");
  }
  const now = new Date().toISOString();
  const { error: orderError } = await client.from("photo_payment_orders").update({
    status: "paid",
    paid_at: now,
    updated_at: now,
  }).eq("id", order.id).neq("status", "paid");
  if (orderError) throw orderError;
  const { error: attemptError } = await client.from("photo_payment_attempts").update({
    provider_status: intent.status,
    last_error_code: null,
    updated_at: now,
  }).eq("paymongo_payment_intent_id", intent.id);
  if (attemptError) throw attemptError;
  await createSessionGrant(client, order, config.sessionGrantTtlSeconds);
  return { ...order, status: "paid", paid_at: order.paid_at ?? now };
}

async function markPaymentNotSucceeded(client: ReturnType<typeof createClient>, intent: ProviderPaymentIntent, status: "failed" | "expired") {
  const now = new Date().toISOString();
  const { data, error } = await client.from("photo_payment_orders").update({ status, updated_at: now })
    .eq("paymongo_payment_intent_id", intent.id)
    .neq("status", "paid")
    .select("id")
    .maybeSingle();
  if (error) throw error;
  await client.from("photo_payment_attempts").update({
    provider_status: intent.status,
    last_error_code: status === "expired" ? "qrph_expired" : "payment_failed",
    updated_at: now,
  }).eq("paymongo_payment_intent_id", intent.id);
  return data as { id: string } | null;
}

async function claimSession(client: ReturnType<typeof createClient>, boothId: string, orderId: string) {
  const { data, error } = await client.from("photo_payment_session_grants")
    .select("id, booth_id, status, session_id, expires_at")
    .eq("order_id", orderId)
    .eq("booth_id", boothId)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(409, "Payment has not been verified");
  const grant = data as { id: string; booth_id: string; status: string; session_id: string | null; expires_at: string };
  if (grant.status === "claimed" && grant.session_id) return { sessionId: grant.session_id, resumed: true };
  if (grant.status !== "available") throw new HttpError(409, "This paid session is no longer available");
  if (new Date(grant.expires_at).getTime() <= Date.now()) {
    await client.from("photo_payment_session_grants").update({ status: "expired", updated_at: new Date().toISOString() })
      .eq("id", grant.id).eq("status", "available");
    throw new HttpError(410, "The paid session grant has expired");
  }
  const sessionId = crypto.randomUUID();
  const now = new Date().toISOString();
  const { data: claimed, error: claimError } = await client.from("photo_payment_session_grants").update({
    status: "claimed",
    session_id: sessionId,
    claimed_at: now,
    updated_at: now,
  }).eq("id", grant.id).eq("status", "available").select("session_id").maybeSingle();
  if (claimError) throw claimError;
  if (claimed?.session_id) return { sessionId: claimed.session_id, resumed: false };

  const { data: retry, error: retryError } = await client.from("photo_payment_session_grants")
    .select("status, session_id").eq("id", grant.id).maybeSingle();
  if (retryError) throw retryError;
  if (retry?.status === "claimed" && retry.session_id) return { sessionId: retry.session_id, resumed: true };
  throw new HttpError(409, "This paid session has already been claimed");
}

async function completeSession(client: ReturnType<typeof createClient>, boothId: string, orderId: string, sessionId: string) {
  const { data, error } = await client.from("photo_payment_session_grants").update({
    status: "completed",
    completed_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  }).eq("order_id", orderId).eq("booth_id", boothId).eq("session_id", sessionId).eq("status", "claimed").select("id").maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(409, "The paid session is no longer active");
  return { ok: true };
}

async function handleWebhook(request: Request, client: ReturnType<typeof createClient>) {
  if (request.method !== "POST") return webhookJson({ error: "Method not allowed" }, 405);
  const config = paymentConfig();
  const declaredLength = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declaredLength) && declaredLength > maxRequestBytes) return webhookJson({ error: "Payload too large" }, 413);
  const rawBody = await request.text();
  if (new TextEncoder().encode(rawBody).byteLength > maxRequestBytes) return webhookJson({ error: "Payload too large" }, 413);
  const verified = await verifyPaymongoWebhookSignature(
    rawBody,
    request.headers.get("paymongo-signature"),
    config.webhookSecret,
    config.environment,
    config.webhookToleranceSeconds,
  );
  if (!verified) return webhookJson({ error: "Invalid webhook signature" }, 401);

  let payload: unknown;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return webhookJson({ error: "Invalid webhook payload" }, 400);
  }
  const event = parsePaymongoWebhookEvent(payload);
  if (!event) return webhookJson({ error: "Unsupported webhook payload" }, 400);
  if (event.livemode !== (config.environment === "live")) return webhookJson({ error: "Webhook mode does not match configuration" }, 400);

  const payloadSha = await sha256Hex(rawBody);
  const { data: previous, error: previousError } = await client.from("photo_payment_webhook_events")
    .select("status, order_id, payload_sha256").eq("provider_event_id", event.id).maybeSingle();
  if (previousError) throw previousError;
  if (!previous) {
    const { error: insertError } = await client.from("photo_payment_webhook_events").insert({
      provider_event_id: event.id,
      event_type: event.type,
      livemode: event.livemode,
      provider_payment_id: event.paymentId,
      provider_payment_intent_id: event.paymentIntentId,
      payload_sha256: payloadSha,
    });
    if (insertError && insertError.code !== "23505") throw insertError;
  } else if (previous.payload_sha256 !== payloadSha) {
    return webhookJson({ error: "Webhook event payload mismatch" }, 409);
  } else if (previous.status === "processed" || previous.status === "ignored") {
    return webhookJson({ received: true, duplicate: true });
  }

  try {
    let orderId: string | null = null;
    if (event.paymentIntentId) {
      const intent = await retrieveProviderPaymentIntent(config, event.paymentIntentId);
      if (event.type === "payment.paid" || event.type === "payment_intent.succeeded") {
        const order = await markPaymentSucceeded(client, config, intent);
        orderId = order?.id ?? null;
      } else if (event.type === "payment.failed") {
        orderId = intent.status === "succeeded"
          ? (await markPaymentSucceeded(client, config, intent))?.id ?? null
          : (await markPaymentNotSucceeded(client, intent, "failed"))?.id ?? null;
      }
    }
    const status = orderId || event.type === "payment.paid" || event.type === "payment_intent.succeeded" || event.type === "payment.failed"
      ? "processed"
      : "ignored";
    const { error: updateError } = await client.from("photo_payment_webhook_events").update({
      status,
      order_id: orderId,
      processing_error: null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("provider_event_id", event.id);
    if (updateError) throw updateError;
    return webhookJson({ received: true });
  } catch (error) {
    await client.from("photo_payment_webhook_events").update({
      status: "failed",
      processing_error: error instanceof ProviderError ? "provider_verification_failed" : "processing_failed",
      updated_at: new Date().toISOString(),
    }).eq("provider_event_id", event.id);
    throw error;
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const client = serviceClient();
  if (request.headers.has("paymongo-signature")) {
    try {
      return await handleWebhook(request, client);
    } catch {
      return webhookJson({ error: "Webhook processing failed" }, 500);
    }
  }
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const declaredLength = Number(request.headers.get("content-length") ?? "0");
    if (Number.isFinite(declaredLength) && declaredLength > maxRequestBytes) throw new HttpError(413, "Request is too large");
    const rawBody = await request.text();
    if (new TextEncoder().encode(rawBody).byteLength > maxRequestBytes) throw new HttpError(413, "Request is too large");
    let body: Record<string, unknown>;
    try {
      const parsed = JSON.parse(rawBody) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("not an object");
      body = parsed as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Invalid request body");
    }
    const action = requiredString(body.action, "Action");
    const boothId = requiredString(body.boothId, "Booth ID");
    const credential = await authenticateBooth(client, boothId, request.headers.get("x-booth-key"), action === "replace_prices");
    if (action === "health") return json({ ok: true, paymentConfigured: paymentConfigured() });

    if (action === "list_prices") {
      return json({ boothId, tiers: await activePriceTiers(client, boothId) });
    }
    if (action === "replace_prices") {
      if (!credential) throw new HttpError(401, "This tablet has not been provisioned for operator pricing");
      const tiers = priceTiersFromRequest(body.tiers);
      const { data, error } = await client.rpc("replace_photo_payment_price_tiers", {
        p_booth_id: boothId,
        p_credential_id: credential.id,
        p_tiers: tiers.map((tier) => ({ print_quantity: tier.printQuantity, amount_minor: tier.amountMinor })),
      });
      if (error) throw error;
      const result = Array.isArray(data) ? data[0] : null;
      if (!result) throw new HttpError(500, "Price update failed");
      return json({
        boothId,
        priceVersion: result.price_version,
        tiers: await activePriceTiers(client, boothId),
      });
    }

    const config = paymentConfig();

    if (action === "create_order") {
      const requestId = requiredString(body.requestId, "Request ID");
      if (!validRequestId(requestId)) throw new HttpError(400, "Request ID must be a UUID");
      return json(await createOrder(client, config, boothId, boundedPrintQuantity(body.printQuantity), requestId));
    }
    if (action === "payment_status") {
      const order = await findOrder(client, requiredString(body.orderId, "Order ID"), boothId);
      if (!order.paymongo_payment_intent_id) throw new HttpError(409, "Payment checkout has not been created");
      const intent = await retrieveProviderPaymentIntent(config, order.paymongo_payment_intent_id);
      const settled = intent.status === "succeeded" ? await markPaymentSucceeded(client, config, intent) : null;
      return json({
        orderId: order.id,
        paymentStatus: intent.status,
        verifiedPaid: Boolean(settled),
      });
    }
    if (action === "claim_session") {
      const orderId = requiredString(body.orderId, "Order ID");
      await findOrder(client, orderId, boothId);
      return json(await claimSession(client, boothId, orderId));
    }
    if (action === "complete_session") {
      const orderId = requiredString(body.orderId, "Order ID");
      const sessionId = requiredString(body.sessionId, "Session ID");
      if (!validRequestId(sessionId)) throw new HttpError(400, "Session ID must be a UUID");
      return json(await completeSession(client, boothId, orderId, sessionId));
    }
    return json({ error: "Unknown action" }, 400);
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    if (error instanceof ProviderError) return json({ error: "The payment provider is temporarily unavailable. Please try again." }, 503);
    return json({ error: "Payment service failed" }, 500);
  }
});

