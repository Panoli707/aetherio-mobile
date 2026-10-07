import { beforeEach, describe, expect, it, vi } from "vitest";
import { handlePenguPlayRequest } from "./penguplay";
import { resetRateLimits } from "./rateLimit";

const ENV = { PENGUPLAY_AUTH_TOKEN: "unit-test-token" };

function req(path: string, init?: RequestInit): Request {
  return new Request(`https://trkll.aetherio.workers.dev${path}`, init);
}

function reqFromIp(path: string, ip: string): Request {
  return req(path, { headers: { "CF-Connecting-IP": ip } });
}

function okJson(body: unknown, headers: HeadersInit = {}): Response {
  const responseHeaders = new Headers({ "Content-Type": "application/json" });
  new Headers(headers).forEach((value, key) => responseHeaders.set(key, value));
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: responseHeaders,
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
  resetRateLimits();
});

describe("proxy PenguPlay: rutas y método", () => {
  it("ignora rutas ajenas", async () => {
    await expect(handlePenguPlayRequest(req("/api/tmdb/movie/1"), ENV)).resolves.toBeNull();
  });

  it("responde al preflight CORS", async () => {
    const response = await handlePenguPlayRequest(req("/api/penguplay/manifest.json", { method: "OPTIONS" }), {});
    expect(response?.status).toBe(204);
    expect(response?.headers.get("Access-Control-Allow-Origin")).toBe("*");
  });

  it("solo permite GET", async () => {
    const response = await handlePenguPlayRequest(req("/api/penguplay/manifest.json", { method: "POST" }), ENV);
    expect(response?.status).toBe(405);
  });

  it("503 si falta el secreto del Worker", async () => {
    const response = await handlePenguPlayRequest(req("/api/penguplay/manifest.json"), {});
    expect(response?.status).toBe(503);
  });

  it("rechaza rutas fuera de la superficie de lectura y query strings", async () => {
    await expect(handlePenguPlayRequest(req("/api/penguplay/admin"), ENV)).resolves.toMatchObject({ status: 400 });
    await expect(handlePenguPlayRequest(req("/api/penguplay/stream/movie/tt123.json?url=https://evil.test"), ENV))
      .resolves.toMatchObject({ status: 400 });
    await expect(handlePenguPlayRequest(req("/api/penguplay/stream/movie/tt123%2Fadmin.json"), ENV))
      .resolves.toMatchObject({ status: 400 });
    await expect(handlePenguPlayRequest(req("/api/penguplay/stream/tv/pp-live%3Afootball.json"), ENV))
      .resolves.toMatchObject({ status: 400 });
  });
});

describe("proxy PenguPlay: upstream", () => {
  it("inyecta la credencial en el host fijo, quita Sports del manifest y no reenvía headers sensibles", async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => okJson(
      {
        id: "com.penguplay",
        types: ["movie", "series", "tv"],
        idPrefixes: ["tt", "pp-live:"],
        resources: [
          { name: "stream", types: ["movie", "series"] },
          { name: "stream", types: ["tv"], idPrefixes: ["pp-live:"] },
          { name: "catalog", types: ["tv"] },
          { name: "subtitles", types: ["movie", "series"] },
        ],
        catalogs: [{ type: "tv", id: "pp-live-football" }],
        config: [{ key: "source_moviebox" }, { key: "source_live-sports" }],
        behaviorHints: { epgProvider: true },
      },
      { "Set-Cookie": "session=private", Location: "https://evil.test/redirect" },
    ));
    vi.stubGlobal("fetch", fetchMock);

    const response = await handlePenguPlayRequest(req("/api/penguplay/manifest.json"), ENV);
    const credentialPath = encodeURIComponent(JSON.stringify({ auth_token: ENV.PENGUPLAY_AUTH_TOKEN }));

    expect(response?.status).toBe(200);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`https://pengu.uk/${credentialPath}/manifest.json`);
    expect(response?.headers.get("Set-Cookie")).toBeNull();
    expect(response?.headers.get("Location")).toBeNull();
    expect(response?.headers.get("Cache-Control")).toBe("no-store");
    await expect(response?.json()).resolves.toEqual({
      id: "com.penguplay",
      types: ["movie", "series"],
      idPrefixes: ["tt"],
      resources: [
        { name: "stream", types: ["movie", "series"] },
        { name: "subtitles", types: ["movie", "series"] },
      ],
      catalogs: [],
      config: [{ key: "source_moviebox" }],
      behaviorHints: {},
    });
  });

  it("proxy el stream de series conservando el id codificado dentro de su segmento", async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => okJson({ streams: [{ url: "https://media.test/movie.m3u8" }] }));
    vi.stubGlobal("fetch", fetchMock);
    const response = await handlePenguPlayRequest(req("/api/penguplay/stream/series/tt123%3A1.json"), ENV);
    const credentialPath = encodeURIComponent(JSON.stringify({ auth_token: ENV.PENGUPLAY_AUTH_TOKEN }));

    expect(response?.status).toBe(200);
    expect(String(fetchMock.mock.calls[0]?.[0]))
      .toBe(`https://pengu.uk/${credentialPath}/stream/series/tt123%3A1.json`);
  });

  it("devuelve un error genérico ante fallos de red o del upstream", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("private upstream detail"); }));
    const response = await handlePenguPlayRequest(req("/api/penguplay/manifest.json"), ENV);

    expect(response?.status).toBe(502);
    await expect(response?.json()).resolves.toEqual({ error: "PenguPlay no está disponible." });
  });

  it("limita por IP las peticiones que llegan al upstream", async () => {
    const fetchMock = vi.fn(async () => okJson({ streams: [] }));
    vi.stubGlobal("fetch", fetchMock);
    const env = { ...ENV, PENGUPLAY_RATE_LIMIT: "2:30" };

    expect((await handlePenguPlayRequest(reqFromIp("/api/penguplay/manifest.json", "198.51.100.5"), env))?.status).toBe(200);
    expect((await handlePenguPlayRequest(reqFromIp("/api/penguplay/manifest.json", "198.51.100.5"), env))?.status).toBe(200);
    const blocked = await handlePenguPlayRequest(reqFromIp("/api/penguplay/manifest.json", "198.51.100.5"), env);

    expect(blocked?.status).toBe(429);
    expect(blocked?.headers.get("Retry-After")).toBe("30");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
