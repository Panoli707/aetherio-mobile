const LIFETIME = 10 * 60_000;
const statePattern = /^[a-f0-9]{96}$/;
const challengePattern = /^[a-f0-9]{64}$/;
const headers = {
  'Cache-Control': 'no-store', 'Pragma': 'no-cache', 'Referrer-Policy': 'no-referrer',
  'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY',
  'Content-Security-Policy': "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
};
const json = (body, status = 200) => Response.json(body, { status, headers });
const error = (status, reason = 'authorization_unavailable') => json({ error: 'authorization_unavailable', reason }, status);
const hex = bytes => Array.from(bytes, v => v.toString(16).padStart(2, '0')).join('');
export const challenge = async verifier => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));

async function readBody(request) {
  if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw new Error('body');
  if (Number(request.headers.get('Content-Length')) > 2048) throw new Error('body');
  const reader = request.body?.getReader();
  if (!reader) throw new Error('body');
  const chunks = []; let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > 2048) throw new Error('body');
      chunks.push(value);
    }
  } finally { await reader.cancel(); }
  const bytes = new Uint8Array(total); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return JSON.parse(new TextDecoder().decode(bytes));
}
function origin(env) {
  const url = new URL(env.PUBLIC_ORIGIN);
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw new Error('origin');
  return url.origin;
}
function ready(env) { return /^[0-9]{1,12}$/.test(env.CLIENT_ID) && !!env.CLIENT_SECRET && /^[a-f0-9]{64}$/.test(env.TOKEN_KEY); }
function back(state, ticket, denied = false) {
  const link = `aetherio://oauth/anilist?state=${state}&${denied ? 'error=access_denied' : `ticket=${ticket}`}`;
  // No token, authorization code, third-party script or automatic redirect is put in this page.
  return new Response(`<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Aetherio · AniList</title><h1>${denied ? 'Autorización cancelada' : 'Autorización completada'}</h1><p><a href="${link.replaceAll('&', '&amp;')}">Volver a Aetherio</a></p><p>El enlace caduca en diez minutos y solo sirve en el dispositivo que inició la conexión.</p></html>`,
    { headers: { ...headers, 'Content-Type': 'text/html; charset=utf-8' } });
}
function fragmentBridge() {
  const nonce = hex(crypto.getRandomValues(new Uint8Array(16)));
  const pageHeaders = { ...headers, 'Content-Type': 'text/html; charset=utf-8',
    'Content-Security-Policy': `default-src 'none'; script-src 'nonce-${nonce}'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'` };
  const script = `(()=>{try{const raw=location.hash.slice(1),p=new URLSearchParams(raw),a=[...p.keys()];if(!raw||new Set(a).size!==a.length)throw 0;const state=p.get('state');if(!/^[a-f0-9]{96}$/.test(state||''))throw 0;if(p.has('error')){if(p.get('error')!=='access_denied'||a.length!==2)throw 0}else if(!/^[!-~]{1,16384}$/.test(p.get('access_token')||'')||p.get('token_type')?.toLowerCase()!=='bearer'||!/^[0-9]{1,8}$/.test(p.get('expires_in')||'')||a.length!==4)throw 0;const link='aetherio://oauth/anilist#'+p.toString(),anchor=document.getElementById('open');anchor.href=link;location.replace(link)}catch{document.getElementById('message').textContent='No se pudo validar el retorno. Vuelve a Aetherio e inténtalo de nuevo.'}})();`;
  return new Response(`<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Aetherio · AniList</title><h1>Volviendo a Aetherio</h1><p id="message">Abriendo la aplicación…</p><a id="open" href="aetherio://oauth/anilist">Volver a Aetherio</a><script nonce="${nonce}">${script}</script></html>`, { headers: pageHeaders });
}
async function key(env) {
  const bytes = Uint8Array.from(env.TOKEN_KEY.match(/../g), v => parseInt(v, 16));
  return crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt']);
}
async function seal(env, data, state) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const bytes = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(state) },
    await key(env), new TextEncoder().encode(JSON.stringify(data)));
  return { iv: [...iv], bytes: [...new Uint8Array(bytes)] };
}
async function unseal(env, data, state) {
  const bytes = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: new Uint8Array(data.iv), additionalData: new TextEncoder().encode(state) },
    await key(env), new Uint8Array(data.bytes));
  return JSON.parse(new TextDecoder().decode(bytes));
}

export default {
  async fetch(request, env) {
    let path = '';
    try {
      const url = new URL(request.url);
      path = url.pathname;
      if (url.origin !== origin(env)) return error(400);
      if (url.pathname === '/health' && request.method === 'GET') return json({ ready: ready(env), clientId: env.CLIENT_ID,
        redirectUri: `${origin(env)}/callback` }, ready(env) ? 200 : 503);
      if (!ready(env)) return error(503, 'broker_not_ready');
      if (request.method === 'POST' && url.pathname === '/start' && !url.search) {
        if (request.headers.has('Origin')) return error(403, 'cross_site_request'); // Native app only, no cross-site browser posts.
        const body = await readBody(request);
        if (!statePattern.test(body.state)) return error(400);
        if (url.pathname === '/start') {
          if (!challengePattern.test(body.challenge)) return error(400);
          const ip = request.headers.get('CF-Connecting-IP');
          if (!ip || !(await env.START_LIMIT.limit({ key: ip })).success) return error(429, 'start_rate_limited');
        }
        return env.SESSIONS.get(env.SESSIONS.idFromName(body.state)).fetch(new Request(`https://session${url.pathname}`,
          { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
      }
      // OAuth implicit credentials exist only in the browser fragment; fragments are never sent in HTTP requests.
      if (url.pathname === '/callback' && request.method === 'GET') return fragmentBridge();
      return error(404);
    } catch {
      // Safe route-level diagnostics only; never return/log token, code, secret, URL or request payload.
      return error(path === '/callback' ? 502 : 400, path === '/callback' ? 'callback_route_failed' : 'request_rejected');
    }
  }
};

export class OAuthSession {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; }
  async fetch(request) {
    const path = new URL(request.url).pathname;
    return this.ctx.blockConcurrencyWhile(async () => {
      try { return await this.handle(request); }
      catch { return error(502, path === '/callback' ? 'callback_processing_failed' : 'session_processing_failed'); }
    });
  }
  async handle(request) {
    const path = new URL(request.url).pathname;
    const body = await request.json();
    if (!statePattern.test(body.state)) return error(400);
    let session = await this.ctx.storage.get('session');
    if (path === '/start') {
      if (session || !challengePattern.test(body.challenge)) return error(409);
      const expires = Date.now() + LIFETIME;
      session = { state: body.state, challenge: body.challenge, expires, phase: 'pending' };
      await this.ctx.storage.put('session', session);
      await this.ctx.storage.setAlarm(expires);
      const authorize = new URL('https://anilist.co/api/v2/oauth/authorize');
      // Implicit uses the callback registered in AniList's client settings.
      // Including redirect_uri reproduces unsupported_grant_type on the live service.
      authorize.search = new URLSearchParams({ client_id: this.env.CLIENT_ID, response_type: 'token',
        state: body.state }).toString();
      return json({ authorizationUrl: authorize.href });
    }
    if (!session || session.state !== body.state || session.expires <= Date.now()) return error(410);
      if (path === '/callback') {
        if (session.phase !== 'pending') return error(409);
        // Legacy session callback is no longer exposed by the public router; keep this branch fail-closed.
        session.phase = 'exchanging'; await this.ctx.storage.put('session', session);
      if (body.denied) { session.phase = 'denied'; await this.ctx.storage.put('session', session); return back(body.state, null, true); }
      if (typeof body.code !== 'string' || !body.code || body.code.length > 16384) return error(400);
      let signal;
      try { signal = AbortSignal.timeout(15000); }
      catch { return error(502, 'timeout_signal_unsupported'); }
      let response;
      try {
        response = await (this.env.fetcher || fetch)('https://anilist.co/api/v2/oauth/token', {
          // Inspect redirects without following them, so an OAuth code/secret never reaches another host.
          method: 'POST', redirect: 'manual', signal,
          headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
          body: JSON.stringify({ grant_type: 'authorization_code', client_id: this.env.CLIENT_ID,
            client_secret: this.env.CLIENT_SECRET, redirect_uri: `${origin(this.env)}/callback`, code: body.code })
        });
      } catch (cause) {
        const name = cause?.name;
        const code = cause?.cause?.code || cause?.code;
        const safeCodes = {
          ENOTFOUND: 'anilist_dns_error', EAI_AGAIN: 'anilist_dns_error',
          ETIMEDOUT: 'anilist_timeout', UND_ERR_CONNECT_TIMEOUT: 'anilist_timeout',
          ECONNREFUSED: 'anilist_connection_refused', ECONNRESET: 'anilist_connection_reset',
          CERT_HAS_EXPIRED: 'anilist_tls_error', UNABLE_TO_VERIFY_LEAF_SIGNATURE: 'anilist_tls_error',
          ERR_TLS_CERT_ALTNAME_INVALID: 'anilist_tls_error'
        };
        const reason = safeCodes[code] || (name === 'TimeoutError' ? 'anilist_timeout' : name === 'AbortError' ? 'anilist_aborted' :
          name === 'TypeError' ? 'anilist_fetch_type_error' : 'anilist_fetch_failed');
        return error(502, reason);
      }
      if (response.status >= 300 && response.status < 400) return error(502, `anilist_redirect_${response.status}`);
      if (!response.ok) return error(502, `anilist_http_${response.status}`);
      // Bound the upstream body before parsing credentials.
      let token;
      try {
        const reader = response.body.getReader(); const chunks = []; let size = 0;
        try { while (true) { const { value, done } = await reader.read(); if (done) break;
          size += value.length; if (size > 32768) throw new Error('large'); chunks.push(value); }
        } finally { await reader.cancel().catch(() => {}); }
        const all = new Uint8Array(size); let start = 0; for (const chunk of chunks) { all.set(chunk, start); start += chunk.length; }
        token = JSON.parse(new TextDecoder().decode(all));
      } catch { return error(502, 'anilist_token_invalid'); }
      if (typeof token.access_token !== 'string' || !/^[!-~]{1,16384}$/.test(token.access_token) ||
          String(token.token_type).toLowerCase() !== 'bearer' || !Number.isInteger(token.expires_in) ||
          token.expires_in < 1 || token.expires_in > 31536000) return error(502, 'anilist_token_invalid');
      session.token = await seal(this.env, { access_token: token.access_token, expiresAt: Date.now() + token.expires_in * 1000 }, body.state);
      session.ticket = hex(crypto.getRandomValues(new Uint8Array(32)));
      session.phase = 'ready'; await this.ctx.storage.put('session', session);
      return back(body.state, session.ticket);
    }
    if (path === '/claim') {
      if (session.phase !== 'ready' || body.ticket !== session.ticket || typeof body.verifier !== 'string' ||
          !statePattern.test(body.verifier) || await challenge(body.verifier) !== session.challenge) return error(403);
      const token = await unseal(this.env, session.token, body.state);
      // Retain only a spent marker until the alarm so start/claim/callback are all one-use.
      await this.ctx.storage.put('session', { state: session.state, expires: session.expires, phase: 'spent' });
      if (token.expiresAt <= Date.now()) return error(410);
      return json({ access_token: token.access_token, token_type: 'Bearer', expires_in: Math.floor((token.expiresAt - Date.now()) / 1000) });
    }
    return error(404);
  }
  async alarm() { await this.ctx.storage.deleteAll(); }
}
