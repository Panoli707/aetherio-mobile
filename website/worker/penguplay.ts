import { checkRateLimit, clientRateKey, parseRateRule } from "./rateLimit";

export interface PenguPlayEnv {
  PENGUPLAY_AUTH_TOKEN?: string;
  PENGUPLAY_RATE_LIMIT?: string;
}

const PREFIX = "/api/penguplay";
const UPSTREAM_ORIGIN = "https://pengu.uk";
const MAX_ID_LENGTH = 512;
const DEFAULT_RATE = { limit: 120, windowMs: 30_000 };

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET,OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "X-Content-Type-Options": "nosniff",
  };
}

function jsonError(message: string, status: number, retryAfterSeconds?: number): Response {
  const headers = new Headers({
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    ...corsHeaders(),
  });
  if (retryAfterSeconds !== undefined) headers.set("Retry-After", String(retryAfterSeconds));
  return new Response(JSON.stringify({ error: message }), { status, headers });
}

function isAllowedResourcePath(path: string): boolean {
  if (path === "/manifest.json") return true;

  const match = /^\/stream\/(movie|series)\/([^/]+)\.json$/.exec(path);
  if (!match) return false;

  let id: string;
  try {
    id = decodeURIComponent(match[2]);
  } catch {
    return false;
  }
  return id.length > 0
    && id.length <= MAX_ID_LENGTH
    && /^[A-Za-z0-9:._~-]+$/.test(id);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function withoutSports(manifest: unknown): Record<string, unknown> | null {
  if (!isRecord(manifest)) return null;

  const types = Array.isArray(manifest.types)
    ? manifest.types.filter(type => type !== "tv")
    : manifest.types;
  const idPrefixes = Array.isArray(manifest.idPrefixes)
    ? manifest.idPrefixes.filter(prefix => !(typeof prefix === "string" && prefix.startsWith("pp-live:")))
    : manifest.idPrefixes;
  const resources = Array.isArray(manifest.resources)
    ? manifest.resources.flatMap((resource): unknown[] => {
        if (typeof resource === "string") return resource === "catalog" || resource === "meta" ? [] : [resource];
        if (!isRecord(resource)) return [];
        const resourceTypes = Array.isArray(resource.types)
          ? resource.types.filter(type => type !== "tv")
          : undefined;
        if (resourceTypes && resourceTypes.length === 0) return [];
        const resourcePrefixes = Array.isArray(resource.idPrefixes)
          ? resource.idPrefixes.filter(prefix => !(typeof prefix === "string" && prefix.startsWith("pp-live:")))
          : undefined;
        return [{
          ...resource,
          ...(resourceTypes ? { types: resourceTypes } : {}),
          ...(resourcePrefixes ? { idPrefixes: resourcePrefixes } : {}),
        }];
      })
    : manifest.resources;
  const catalogs = Array.isArray(manifest.catalogs)
    ? manifest.catalogs.filter(catalog => !isRecord(catalog) || catalog.type !== "tv")
    : manifest.catalogs;
  const config = Array.isArray(manifest.config)
    ? manifest.config.filter(item => !isRecord(item) || item.key !== "source_live-sports")
    : manifest.config;
  const behaviorHints = isRecord(manifest.behaviorHints)
    ? Object.fromEntries(Object.entries(manifest.behaviorHints).filter(([key]) => key !== "epgProvider"))
    : manifest.behaviorHints;

  return { ...manifest, types, idPrefixes, resources, catalogs, config, behaviorHints };
}

/** Proxy de solo lectura: la credencial de PenguPlay solo se usa en el Worker. */
export async function handlePenguPlayRequest(
  request: Request,
  env: PenguPlayEnv,
): Promise<Response | null> {
  const url = new URL(request.url);
  if (url.pathname !== PREFIX && !url.pathname.startsWith(`${PREFIX}/`)) return null;

  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (request.method !== "GET") return jsonError("Método no permitido.", 405);

  const resourcePath = url.pathname.slice(PREFIX.length);
  if (!isAllowedResourcePath(resourcePath) || url.search) {
    return jsonError("Ruta PenguPlay no válida.", 400);
  }

  const token = env.PENGUPLAY_AUTH_TOKEN?.trim();
  if (!token) return jsonError("PenguPlay no está configurado en el servidor.", 503);

  const decision = checkRateLimit(
    clientRateKey(request, "penguplay"),
    parseRateRule(env.PENGUPLAY_RATE_LIMIT, DEFAULT_RATE),
  );
  if (!decision.allowed) {
    return jsonError("Demasiadas peticiones. Intenta de nuevo en unos segundos.", 429, decision.retryAfterSeconds);
  }

  const credentialPath = encodeURIComponent(JSON.stringify({ auth_token: token }));
  const upstreamUrl = `${UPSTREAM_ORIGIN}/${credentialPath}${resourcePath}`;
  let upstream: Response;
  try {
    upstream = await fetch(upstreamUrl, { headers: { Accept: "application/json" } });
  } catch {
    return jsonError("PenguPlay no está disponible.", 502);
  }

  if (!upstream.ok) {
    return jsonError(
      "PenguPlay no está disponible.",
      upstream.status >= 500 ? 502 : upstream.status,
    );
  }

  const headers = new Headers({
    "Cache-Control": "no-store",
    ...corsHeaders(),
  });
  const contentType = upstream.headers.get("Content-Type");
  if (contentType) headers.set("Content-Type", contentType);

  if (resourcePath === "/manifest.json") {
    let manifest: unknown;
    try {
      manifest = await upstream.json();
    } catch {
      return jsonError("El manifest de PenguPlay no es válido.", 502);
    }
    const productManifest = withoutSports(manifest);
    if (!productManifest) return jsonError("El manifest de PenguPlay no es válido.", 502);
    headers.set("Content-Type", "application/json; charset=utf-8");
    return new Response(JSON.stringify(productManifest), { status: upstream.status, headers });
  }

  // Pasa el body en streaming; nunca devuelve ni redirige al path upstream que
  // contiene la credencial.
  return new Response(upstream.body, { status: upstream.status, headers });
}
