# Shared photo/GIF delivery Worker

This Worker serves all booths from one private R2 bucket. Each booth uses a unique `X-Booth-Id` and device API key. Uploaded objects receive a one-hour expiry. Each photo and GIF receives a cryptographically random, booth-scoped opaque download token backed by a short-lived token object in R2. Tokens and media are removed by scheduled cleanup.

## First deployment

```powershell
npm install -g wrangler
wrangler login
wrangler r2 bucket create photobooth-media
wrangler secret put SIGNING_SECRET
wrangler secret put DEVICE_API_KEYS
wrangler deploy
```

`DEVICE_API_KEYS` is a JSON object, for example:

```json
{"booth-001":"replace-with-a-long-random-key","booth-002":"replace-with-another-key"}
```

Do not put the Cloudflare API token, R2 access key, or Worker secrets in the APK. The APK only receives a per-booth upload key. `SIGNING_SECRET` is no longer required by the opaque-token delivery path and may be removed after the deployment is confirmed stable.
