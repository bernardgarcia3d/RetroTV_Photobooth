export type PaymongoWebhookEvent = {
  id: string;
  type: string;
  livemode: boolean;
  paymentId: string | null;
  paymentIntentId: string | null;
};

const encoder = new TextEncoder();

function hex(bytes: Uint8Array) {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function safeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return difference === 0;
}

export function parsePaymongoSignature(header: string | null, environment: "test" | "live") {
  if (!header) return null;
  const values = new Map<string, string>();
  for (const segment of header.split(",")) {
    const separator = segment.indexOf("=");
    if (separator < 1) continue;
    values.set(segment.slice(0, separator).trim(), segment.slice(separator + 1).trim());
  }
  const timestamp = values.get("t");
  const signature = values.get(environment === "live" ? "li" : "te");
  if (!timestamp || !/^\d{10}$/.test(timestamp) || !signature || !/^[a-f0-9]{64}$/i.test(signature)) return null;
  return { timestamp, signature: signature.toLowerCase() };
}

export async function hmacSha256Hex(secret: string, value: string) {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(value));
  return hex(new Uint8Array(signature));
}

export async function sha256Hex(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return hex(new Uint8Array(digest));
}

export async function verifyPaymongoWebhookSignature(
  rawBody: string,
  header: string | null,
  secret: string,
  environment: "test" | "live",
  toleranceSeconds: number,
  nowSeconds = Math.floor(Date.now() / 1000),
) {
  const parsed = parsePaymongoSignature(header, environment);
  if (!parsed) return false;
  const timestamp = Number(parsed.timestamp);
  if (!Number.isSafeInteger(timestamp) || Math.abs(nowSeconds - timestamp) > toleranceSeconds) return false;
  const expected = await hmacSha256Hex(secret, `${parsed.timestamp}.${rawBody}`);
  return safeEqual(expected, parsed.signature);
}

export function parsePaymongoWebhookEvent(payload: unknown): PaymongoWebhookEvent | null {
  if (!payload || typeof payload !== "object") return null;
  const root = payload as Record<string, unknown>;
  const data = root.data;
  if (!data || typeof data !== "object") return null;
  const event = data as Record<string, unknown>;
  const attributes = event.attributes;
  if (typeof event.id !== "string" || !attributes || typeof attributes !== "object") return null;
  const eventAttributes = attributes as Record<string, unknown>;
  if (typeof eventAttributes.type !== "string" || typeof eventAttributes.livemode !== "boolean") return null;
  const resource = eventAttributes.data;
  if (!resource || typeof resource !== "object") return null;
  const resourceObject = resource as Record<string, unknown>;
  const resourceAttributes = resourceObject.attributes && typeof resourceObject.attributes === "object"
    ? resourceObject.attributes as Record<string, unknown>
    : {};

  const payments = Array.isArray(resourceAttributes.payments) ? resourceAttributes.payments : [];
  const payment = payments.find((entry) => entry && typeof entry === "object" && (entry as Record<string, unknown>).type === "payment") as Record<string, unknown> | undefined;
  const paymentAttributes = payment?.attributes && typeof payment.attributes === "object"
    ? payment.attributes as Record<string, unknown>
    : {};
  const nestedIntent = resourceAttributes.payment_intent && typeof resourceAttributes.payment_intent === "object"
    ? resourceAttributes.payment_intent as Record<string, unknown>
    : null;

  const paymentIntentId = resourceObject.type === "payment_intent" && typeof resourceObject.id === "string"
    ? resourceObject.id
    : typeof resourceAttributes.payment_intent_id === "string"
    ? resourceAttributes.payment_intent_id
    : typeof paymentAttributes.payment_intent_id === "string"
    ? paymentAttributes.payment_intent_id
    : typeof nestedIntent?.id === "string"
    ? nestedIntent.id
    : null;

  return {
    id: event.id,
    type: eventAttributes.type,
    livemode: eventAttributes.livemode,
    paymentId: resourceObject.type === "payment" && typeof resourceObject.id === "string" ? resourceObject.id : null,
    paymentIntentId,
  };
}
