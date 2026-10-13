import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-booth-key",
  "Content-Type": "application/json",
};
const TIME_ZONE = "Asia/Taipei";
const EVENT_ID = "retro-roll";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function todayInTaipei() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
}

function midnightAfter(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  // Asia/Taipei is UTC+8. The next local midnight is 16:00 UTC on the previous day.
  const next = new Date(Date.UTC(year, month - 1, day + 1, -8, 0, 0));
  return next.toISOString();
}

async function digest(value: string) {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function randomCode() {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(bytes[0] % 100000).padStart(5, "0");
}

async function validateBooth(client: ReturnType<typeof createClient>, boothId: string) {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(boothId)) throw new Error("Invalid booth ID");
  const { data, error } = await client.from("photo_booths")
    .select("booth_id")
    .eq("booth_id", boothId)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new Error("Booth is not registered or is disabled");
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const sharedKey = Deno.env.get("BOOTH_SHARED_KEY");
  if (!sharedKey || request.headers.get("x-booth-key") !== sharedKey) return json({ error: "Unauthorized booth" }, 401);

  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  try {
    const body = await request.json();
    const action = String(body.action ?? "");
    const boothId = String(body.boothId ?? "unknown").slice(0, 64);
    const secret = `${sharedKey}:retro-roll`;

    if (action === "health") {
      const { error } = await client.from("photo_code_batches").select("id").limit(1);
      if (error) throw error;
      return json({ ok: true, businessDate: todayInTaipei() });
    }

    if (action === "create_batch") {
      const businessDate = String(body.businessDate || todayInTaipei());
      const packageId = String(body.packageId || "").slice(0, 64);
      const packageLabel = String(body.packageLabel || packageId).slice(0, 80);
      const quantity = Math.max(1, Math.min(10000, Number(body.quantity || 1)));
      if (!packageId) return json({ error: "A package is required" }, 400);

      const { data: occupiedRows, error: occupiedError } = await client.from("photo_access_codes")
        .select("code_digest")
        .eq("event_id", EVENT_ID)
        .eq("business_date", businessDate);
      if (occupiedError) throw occupiedError;

      const codes: string[] = [];
      const codeDigests: string[] = [];
      const occupied = new Set((occupiedRows ?? []).map((row) => row.code_digest));
      const seen = new Set<string>();
      while (codes.length < quantity) {
        const code = randomCode();
        const codeDigest = await digest(`${EVENT_ID}:${businessDate}:${code}:${secret}`);
        if (!seen.has(code) && !occupied.has(codeDigest)) {
          seen.add(code);
          occupied.add(codeDigest);
          codes.push(code);
          codeDigests.push(codeDigest);
        }
      }
      const expiresAt = midnightAfter(businessDate);
      const { data: batch, error: batchError } = await client.from("photo_code_batches").insert({
        business_date: businessDate,
        event_id: EVENT_ID,
        package_id: packageId,
        package_label: packageLabel,
        quantity,
        expires_at: expiresAt,
      }).select("id").single();
      if (batchError) throw batchError;

      const rows = codes.map((code, index) => ({
        batch_id: batch.id,
        event_id: EVENT_ID,
        package_id: packageId,
        code_digest: codeDigests[index],
        code_hint: code.slice(-2),
        business_date: businessDate,
        expires_at: expiresAt,
      }));
      const { data: insertedCodes, error: codeError } = await client.from("photo_access_codes").insert(rows).select("id");
      if (codeError) throw codeError;
      return json({ batchId: batch.id, codeIds: (insertedCodes ?? []).map((row) => row.id), businessDate, packageId, packageLabel, expiresAt, codes });
    }

    if (action === "redeem") {
      await validateBooth(client, boothId);
      const code = String(body.code || "").replace(/\D/g, "").slice(0, 5).padStart(5, "0");
      if (code.length !== 5) return json({ error: "Enter the five-digit code" }, 400);
      const businessDate = todayInTaipei();
      const codeDigest = await digest(`${EVENT_ID}:${businessDate}:${code}:${secret}`);
      const { data: candidate, error: lookupError } = await client.from("photo_access_codes")
        .select("id, package_id, status, expires_at")
        .eq("code_digest", codeDigest).maybeSingle();
      if (lookupError) throw lookupError;
      if (!candidate) return json({ error: "That code is not valid today" }, 404);
      if (candidate.status !== "issued") return json({ error: `That code is already ${candidate.status}` }, 409);
      if (new Date(candidate.expires_at).getTime() <= Date.now()) {
        await client.from("photo_access_codes").update({ status: "expired", updated_at: new Date().toISOString() }).eq("id", candidate.id).eq("status", "issued");
        return json({ error: "That code has expired" }, 410);
      }
      const { data: claimed, error: claimError } = await client.from("photo_access_codes")
        .update({ status: "claimed", session_status: "claimed", claimed_at: new Date().toISOString(), booth_id: boothId, updated_at: new Date().toISOString() })
        .eq("id", candidate.id).eq("status", "issued").select("id, package_id, expires_at").maybeSingle();
      if (claimError) throw claimError;
      if (!claimed) return json({ error: "That code was just used" }, 409);
      return json({ accessCodeId: claimed.id, packageId: claimed.package_id, expiresAt: claimed.expires_at });
    }

    if (action === "complete") {
      await validateBooth(client, boothId);
      const accessCodeId = String(body.accessCodeId || "");
      const sessionId = String(body.sessionId || "").slice(0, 80);
      const { data, error } = await client.from("photo_access_codes")
        .update({
          status: "completed",
          session_status: "completed",
          completed_at: new Date().toISOString(),
          photo_session_id: sessionId,
          r2_asset_references: {
            photo: `booths/${boothId}/sessions/${sessionId}/photo.png`,
            gif: `booths/${boothId}/sessions/${sessionId}/animation.gif`,
          },
          updated_at: new Date().toISOString(),
        })
        .eq("id", accessCodeId).eq("status", "claimed").eq("booth_id", boothId).select("id, status").maybeSingle();
      if (error) throw error;
      if (!data) return json({ error: "The access session is no longer active" }, 409);
      return json({ ok: true, status: data.status });
    }

    if (action === "maintenance") {
      await validateBooth(client, boothId);
      const now = new Date().toISOString();
      const { error: expireError } = await client.from("photo_access_codes").update({ status: "expired", updated_at: now }).eq("status", "issued").lte("expires_at", now);
      if (expireError) throw expireError;
      const retention = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
      const { error: deleteError } = await client.from("photo_access_codes").delete().in("status", ["completed", "expired", "cancelled", "abandoned"]).lt("updated_at", retention);
      if (deleteError) throw deleteError;
      return json({ ok: true });
    }

    if (action === "totals") {
      const { data, error } = await client.from("photo_access_codes").select("status").eq("event_id", EVENT_ID).eq("business_date", todayInTaipei());
      if (error) throw error;
      const totals = { issued: 0, claimed: 0, completed: 0, expired: 0, cancelled: 0 };
      for (const row of data ?? []) if (row.status in totals) totals[row.status as keyof typeof totals] += 1;
      return json(totals);
    }

    if (action === "cancel") {
      const accessCodeId = String(body.accessCodeId || "");
      const { data, error } = await client.from("photo_access_codes")
        .update({ status: "cancelled", cancelled_at: new Date().toISOString(), updated_at: new Date().toISOString() })
        .eq("id", accessCodeId).eq("event_id", EVENT_ID).eq("status", "issued").select("id").maybeSingle();
      if (error) throw error;
      if (!data) return json({ error: "Only an unused code can be cancelled" }, 409);
      return json({ ok: true });
    }

    return json({ error: "Unknown action" }, 400);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Access code service failed";
    const status = message === "Invalid booth ID" || message === "Booth is not registered or is disabled" ? 403 : 500;
    return json({ error: message }, status);
  }
});
