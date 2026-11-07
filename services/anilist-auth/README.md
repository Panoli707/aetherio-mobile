# AniList OAuth broker

This Worker exchanges AniList authorization codes without placing the AniList client secret in the Android APK. The temporary token is encrypted at rest in a Durable Object and can be claimed once using the app's PKCE verifier.

## Initial setup

The Worker is deployed at:

`https://aetherio-anilist-auth.adrianchaves341.workers.dev`

In the AniList developer settings for client `56163`, set the redirect URL to this exact address:

`https://aetherio-anilist-auth.adrianchaves341.workers.dev/callback`

Then, on the deployment machine, run this script from PowerShell. It prompts for the AniList client secret without echoing it and generates a random encryption key locally; neither value belongs in source control or the APK:

```powershell
.\services\anilist-auth\configure-secrets.ps1 -InitializeEncryptionKey
```

The account's Worker URL and redirect are public identifiers. The client secret and `TOKEN_KEY` are not. Do not send either secret in chat. If `TOKEN_KEY` is lost, pending OAuth sessions become unreadable; configure a replacement only during a planned reset.

Check readiness after configuring both secrets:

```powershell
Invoke-RestMethod https://aetherio-anilist-auth.adrianchaves341.workers.dev/health
```

The endpoint should report `ready: true`. Until then, authorization intentionally fails closed.

## Development

```powershell
node --test
npx.cmd --yes wrangler@4 deploy --dry-run
```

Deployment is separate from publishing the Android app. Do not publish a client build until the AniList callback is configured, both Worker secrets are installed, `/health` reports ready, and an end-to-end authorization has been verified.
