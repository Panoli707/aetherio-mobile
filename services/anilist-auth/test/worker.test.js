import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { OAuthSession, challenge } from '../src/worker.js';

const state = 'a'.repeat(96), verifier = 'b'.repeat(96);
function fixture() {
  const data = new Map(); let alarm; let calls = 0;
  let tail = Promise.resolve();
  const ctx = { storage: { get: async k => structuredClone(data.get(k)), put: async (k,v) => data.set(k,structuredClone(v)),
    setAlarm: async value => { alarm = value; }, deleteAll: async () => data.clear() },
    blockConcurrencyWhile: fn => { const next = tail.then(fn); tail = next.catch(() => {}); return next; } };
  const env = { CLIENT_ID: '56163', CLIENT_SECRET: 'test-only-secret', TOKEN_KEY: 'c'.repeat(64),
    PUBLIC_ORIGIN: 'https://auth.example', START_LIMIT: { limit: async () => ({ success: true }) },
    fetcher: async (url, options) => {
      calls++;
      assert.equal(url, 'https://anilist.co/api/v2/oauth/token');
       assert.equal(options.redirect, 'manual');
      const sent = JSON.parse(options.body);
      assert.equal(sent.grant_type, 'authorization_code'); assert.equal(sent.client_secret, env.CLIENT_SECRET);
      assert.equal(sent.redirect_uri, 'https://auth.example/callback');
      return Response.json({ access_token: 'test-only-access', token_type: 'Bearer', expires_in: 3600 });
    } };
  const session = new OAuthSession(ctx,env);
  env.SESSIONS = { idFromName: value => value, get: () => session };
  const send = (path, body) => session.fetch(new Request(`https://session${path}`, { method:'POST', body:JSON.stringify({ state,...body }) }));
  return { env, session, data, send, calls: () => calls, alarm: () => alarm };
}
test('code flow keeps secrets server-side, encrypts temporary token, and claims only once with verifier', async () => {
  const f = fixture();
  const started = await f.send('/start', { challenge: await challenge(verifier) });
  assert.equal(started.status,200);
  const url = new URL((await started.json()).authorizationUrl);
  assert.equal(url.searchParams.get('response_type'),'token');
  assert.equal(url.searchParams.get('state'),state);
   assert.equal(url.searchParams.has('redirect_uri'),false);
   assert.deepEqual([...url.searchParams.keys()].sort(), ['client_id','response_type','state']);
  assert.ok(!url.href.includes('secret'));
  assert.ok(f.alarm() > Date.now());
  const callback = await f.send('/callback',{code:'test-code'});
  assert.equal(callback.status,200);
  const page = await callback.text();
  assert.ok(!page.includes('test-only-access')); assert.ok(!page.includes('test-code'));
  assert.ok(!JSON.stringify([...f.data]).includes('test-only-access'));
  const ticket = f.data.get('session').ticket;
  assert.equal((await f.send('/claim',{ ticket,verifier:'d'.repeat(96) })).status,403);
  assert.equal((await f.send('/claim',{ ticket:'wrong',verifier })).status,403);
  const claim = await f.send('/claim',{ticket,verifier});
  assert.equal(claim.headers.get('Cache-Control'),'no-store');
  assert.equal((await claim.json()).access_token,'test-only-access');
  assert.equal(f.data.get('session').token,undefined);
  assert.equal((await f.send('/claim',{ticket,verifier})).status,403);
  assert.equal((await f.send('/callback',{code:'test-code'})).status,409);
  assert.equal((await f.send('/start',{challenge:await challenge(verifier)})).status,409);
  assert.equal(f.calls(),1);
  await f.session.alarm(); assert.equal(f.data.size,0);
});
test('expired, unsolicited, denied and failed callbacks do not yield a token',async () => {
  const f = fixture();
  assert.equal((await f.send('/callback',{code:'code'})).status,410);
  await f.send('/start',{challenge:await challenge(verifier)});
  const denied = await f.send('/callback',{denied:true});
  assert.match(await denied.text(),/error=access_denied/); assert.equal(f.calls(),0);
  assert.equal((await f.send('/claim',{verifier})).status,403);
  const g = fixture(); await g.send('/start',{challenge:await challenge(verifier)});
  g.env.fetcher = async () => Response.json({error:'upstream-sensitive'}, {status:400});
  const rejected = await g.send('/callback',{code:'private-test-code'});
  assert.equal(rejected.status,502);
  assert.deepEqual(await rejected.json(), {error:'authorization_unavailable',reason:'anilist_http_400'});
  assert.equal(g.data.get('session').phase,'exchanging');
  assert.equal((await g.send('/callback',{code:'code'})).status,409);
  const session = g.data.get('session'); session.expires=Date.now()-1; g.data.set('session',session);
  assert.equal((await g.send('/claim',{verifier})).status,410);
});
test('callback diagnostics expose only a safe upstream category', async () => {
  const f = fixture(); await f.send('/start',{challenge:await challenge(verifier)});
  f.env.fetcher = async () => { throw new Error('private-secret private-code'); };
  const response = await f.send('/callback',{code:'private-code'});
  assert.equal(response.status,502);
  const body = await response.text();
  assert.deepEqual(JSON.parse(body), {error:'authorization_unavailable',reason:'anilist_fetch_failed'});
  assert.ok(!body.includes('private-secret') && !body.includes('private-code'));
  const g = fixture(); await g.send('/start',{challenge:await challenge(verifier)});
  g.env.fetcher = async () => { throw new TypeError('private-network-detail'); };
  const typed = await g.send('/callback',{code:'private-code'});
  assert.deepEqual(await typed.json(), {error:'authorization_unavailable',reason:'anilist_fetch_type_error'});
  const h = fixture(); await h.send('/start',{challenge:await challenge(verifier)});
  h.env.fetcher = async () => { throw Object.assign(new TypeError('private-network-detail'), {cause:{code:'ENOTFOUND'}}); };
  const dns = await h.send('/callback',{code:'private-code'});
  assert.deepEqual(await dns.json(), {error:'authorization_unavailable',reason:'anilist_dns_error'});
  const redirected = fixture(); await redirected.send('/start',{challenge:await challenge(verifier)});
  redirected.env.fetcher = async () => new Response(null,{status:302,headers:{Location:'https://untrusted.example/private'}});
  const blocked = await redirected.send('/callback',{code:'private-code'});
  const blockedBody = await blocked.text();
  assert.equal(blocked.status,502);
  assert.deepEqual(JSON.parse(blockedBody),{error:'authorization_unavailable',reason:'anilist_redirect_302'});
  assert.ok(!blockedBody.includes('untrusted.example') && !blockedBody.includes('private-code'));
});
test('concurrent callbacks and claims are serialized and cannot exchange or disclose twice', async () => {
  const f = fixture(); await f.send('/start', { challenge: await challenge(verifier) });
  const callbacks = await Promise.all([f.send('/callback', {code:'code'}), f.send('/callback',{code:'code'})]);
  assert.deepEqual(callbacks.map(r=>r.status).sort(), [200,409]); assert.equal(f.calls(),1);
  const ticket = f.data.get('session').ticket;
  const claims = await Promise.all([f.send('/claim',{ticket,verifier}), f.send('/claim',{ticket,verifier})]);
  assert.deepEqual(claims.map(r=>r.status).sort(),[200,403]);
});
test('malformed credentials and redirects from AniList never reach the app or storage', async () => {
  for (const token of [
    {access_token:'sensitive-token',token_type:'Basic',expires_in:3600},
    {access_token:'sensitive-token',token_type:'Bearer',expires_in:0},
    {access_token:'a'.repeat(33000),token_type:'Bearer',expires_in:3600},
    {access_token:'bad\ntoken',token_type:'Bearer',expires_in:3600},
  ]) {
    const f=fixture(); await f.send('/start',{challenge:await challenge(verifier)});
    f.env.fetcher=async()=>Response.json(token);
    const response=await f.send('/callback',{code:'sensitive-code'});
    assert.equal(response.status,502);
    const body=await response.text();
    assert.deepEqual(JSON.parse(body),{error:'authorization_unavailable',reason:'anilist_token_invalid'});
    assert.ok(!body.includes('sensitive'));
    assert.ok(!JSON.stringify([...f.data]).includes('sensitive'));
  }
});
test('public router refuses cross-site requests, foreign origins, duplicate parameters, oversized bodies and rate-limited starts',async () => {
  const f=fixture();
  const send=(path,body,extra={})=>worker.fetch(new Request(`https://auth.example${path}`,{method:'POST',headers:{'Content-Type':'application/json','CF-Connecting-IP':'192.0.2.1',...extra},body:JSON.stringify(body)}),f.env);
  const body={state,challenge:await challenge(verifier)};
  const crossSite = await send('/start',body,{Origin:'https://evil.example'});
  assert.equal(crossSite.status,403);
  assert.deepEqual(await crossSite.json(),{error:'authorization_unavailable',reason:'cross_site_request'});
  assert.equal((await send('/start',{...body,padding:'a'.repeat(2100)})).status,400);
  f.env.START_LIMIT.limit=async()=>({success:false});
  const rateLimited = await send('/start',body);
  assert.equal(rateLimited.status,429);
  assert.deepEqual(await rateLimited.json(),{error:'authorization_unavailable',reason:'start_rate_limited'});
  assert.equal((await worker.fetch(new Request(`https://auth.example/callback?state=${state}&state=${state}&code=x`),f.env)).status,200);
  assert.equal((await worker.fetch(new Request('https://foreign.example/health'),f.env)).status,400);
  assert.equal((await worker.fetch(new Request('https://auth.example/health'),f.env)).status,200);
  delete f.env.CLIENT_SECRET;
  assert.equal((await worker.fetch(new Request('https://auth.example/health'),f.env)).status,503);
});
test('implicit callback bridge only reads credentials from the fragment and redirects to the native app', async () => {
  const f = fixture();
  const response = await worker.fetch(new Request('https://auth.example/callback', { method:'GET' }), f.env);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('Content-Security-Policy'), /script-src 'nonce-[a-f0-9]{32}'/);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  const html = await response.text();
  assert.match(html, /location\.hash/);
  assert.match(html, /aetherio:\/\/oauth\/anilist#/);
  assert.doesNotMatch(html, /fetch\(|XMLHttpRequest|anilist\.co/);
  assert.equal(f.calls(), 0);
});
