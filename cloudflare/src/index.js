export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));

    try {
      if (request.method === "POST" && url.pathname.match(/^\/v1\/sessions\/[^/]+\/assets$/)) {
        return cors(await uploadAssets(request, env, url));
      }
      if (request.method === "GET" && url.pathname.startsWith("/v1/download/")) {
        return cors(await downloadAsset(request, env, url.pathname.slice("/v1/download/".length)));
      }
      return cors(json({ error: "Not found" }, 404));
    } catch (error) {
      return cors(json({ error: error.message || "Delivery request failed" }, 500));
    }
  },

  async scheduled(_event, env, ctx) {
    ctx.waitUntil(deleteExpiredObjects(env));
  },
};

async function uploadAssets(request, env, url) {
  const sessionId = url.pathname.split("/")[3];
  const boothId = request.headers.get("X-Booth-Id") || "unassigned";
  requireUuid(sessionId);
  requireDeviceKey(request, env, boothId);

  const form = await request.formData();
  const photo = form.get("photo");
  const gif = form.get("gif");
  if (!(photo instanceof File) || !(gif instanceof File)) return json({ error: "photo and gif files are required" }, 400);

  const expiresAt = Date.now() + 60 * 60 * 1000;
  const prefix = `booths/${safeSegment(boothId)}/sessions/${sessionId}`;
  const objects = [
    ["photo.png", photo, "image/png"],
    ["animation.gif", gif, "image/gif"],
  ];
  const response = {};
  for (const [name, file, contentType] of objects) {
    const key = `${prefix}/${name}`;
    await env.MEDIA.put(key, file instanceof File ? file.stream() : file, { httpMetadata: { contentType }, customMetadata: { expiresAt: String(expiresAt) } });
    const token = randomToken();
    await env.MEDIA.put(`tokens/${token}`, JSON.stringify({ key, expiresAt }), { httpMetadata: { contentType: "application/json" }, customMetadata: { expiresAt: String(expiresAt) } });
    response[name === "photo.png" ? "photo" : "gif"] = { url: `${url.origin}/v1/download/${token}`, expiresAt };
  }
  return json({ sessionId, expiresAt, ...response });
}

async function downloadAsset(_request, env, token) {
  const tokenObject = await env.MEDIA.get(`tokens/${safeToken(token)}`);
  const payload = tokenObject ? await tokenObject.json() : null;
  if (!payload || payload.expiresAt < Date.now()) return json({ error: "Download link expired" }, 410);
  const object = await env.MEDIA.get(payload.key);
  if (!object) return json({ error: "File no longer available" }, 404);
  const headers = new Headers({ "Content-Type": object.httpMetadata?.contentType || "application/octet-stream", "Cache-Control": "private, no-store", "Content-Disposition": `inline; filename="${payload.key.split("/").pop()}"` });
  return new Response(object.body, { headers });
}

async function deleteExpiredObjects(env) {
  let cursor;
  do {
    const page = await env.MEDIA.list({ cursor, limit: 1000 });
    for (const object of page.objects) if (Number(object.customMetadata?.expiresAt || 0) <= Date.now()) await env.MEDIA.delete(object.key);
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
}

function requireDeviceKey(request, env, boothId) {
  const keys = JSON.parse(env.DEVICE_API_KEYS || "{}");
  const expected = keys[boothId];
  const actual = request.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!expected || !actual || actual !== expected) throw new Error("Unauthorized booth");
}

function requireUuid(value) { if (!/^[0-9a-f-]{36}$/i.test(value)) throw new Error("Invalid session id"); }
function safeSegment(value) { return value.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 64); }
function safeToken(value) { return /^[A-Za-z0-9_-]{64}$/.test(value) ? value : "invalid"; }
function randomToken() { const bytes = crypto.getRandomValues(new Uint8Array(32)); return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join(""); }
function json(value, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } }); }
function cors(response) { const headers = new Headers(response.headers); headers.set("Access-Control-Allow-Origin", "*"); headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Booth-Id"); headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS"); return new Response(response.body, { status: response.status, headers }); }
