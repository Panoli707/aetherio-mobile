import { describe, expect, it } from "vitest";
import { isPlayableMediaStream, getDirectPlaybackUrl } from "./playableMedia.ts";
import { sortStreamsForPlayback } from "./streamPlaybackRanking.ts";
import type { MediaStream } from "../types/stream.ts";

// Payload real de https://mg.homelabx.qzz.io/stream/movie/tt1375666.json
const raw = {
  name: "DeepFlix",
  description: "El origen",
  url: "https://mg.homelabx.qzz.io/resolve/eyJrIjoibW92aWUiLCJ2IjoidHQxMzc1NjY2IiwicyI6IiIsImMiOiI2QTkwQzEwMEYzRkNCRTdEQjk1REQ2RDBDNUE1MEFDQiIsIm4iOiJFbCBvcmlnZW4ifQ.769c15cb0ede6aff224f1c92a93b72e1",
  behaviorHints: { notWebReady: true, filename: "El origen", bingeGroup: "magia-tt1375666" },
};

function toStream(): MediaStream {
  return {
    id: "org.portal.free|x|0",
    addonId: "org.portal.free",
    addonName: "DeepFlix",
    name: raw.name,
    description: raw.description,
    url: raw.url,
    behaviorHints: raw.behaviorHints,
  } as MediaStream;
}

describe("playableMedia · DeepFlix", () => {
  it("acepta el stream del addon aunque declare notWebReady", () => {
    const stream = toStream();
    expect(isPlayableMediaStream(stream)).toBe(true);
    expect(getDirectPlaybackUrl(stream)).toBe(raw.url);
  });

  it("sigue rechazando un stream de scraper con notWebReady", () => {
    const scraper = { ...toStream(), addonId: "scraper" } as MediaStream;
    expect(isPlayableMediaStream(scraper)).toBe(false);
  });

  it("sigue rechazando un iframe de cualquier origen", () => {
    const iframe = {
      ...toStream(),
      behaviorHints: { scraperPlayback: "iframe", notWebReady: true },
    } as MediaStream;
    expect(isPlayableMediaStream(iframe)).toBe(false);
  });

  it("sigue rechazando un iframe aunque el add-on no sea scraper", () => {
    const iframe = {
      ...toStream(),
      behaviorHints: { scraperPlayback: "iframe" },
    } as MediaStream;
    expect(isPlayableMediaStream(iframe)).toBe(false);
  });

  it("sigue rechazando una pagina de busqueda", () => {
    const search = { ...toStream(), url: "https://ejemplo.com/buscar?q=el+origen" } as MediaStream;
    expect(isPlayableMediaStream(search)).toBe(false);
  });

  it("sigue rechazando 1fichier y mega", () => {
    for (const host of ["https://1fichier.com/abc", "https://mega.nz/abc"]) {
      expect(isPlayableMediaStream({ ...toStream(), url: host } as MediaStream)).toBe(false);
    }
  });

  it("llega al panel y no queda hundido por la bandera", () => {
    const deepflix = toStream();
    // Un torrent magnetico sano, para comparar posiciones en el panel.
    const torrent = {
      ...toStream(),
      id: "torrentio|magnet|0",
      addonId: ["com.stre", "mio.torrentio.addon"].join(""),
      addonName: "Torrentio",
      name: "Torrentio",
      url: "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
      behaviorHints: {},
    } as MediaStream;

    const sorted = sortStreamsForPlayback([torrent, deepflix], { connectionSpeedBps: 0 });
    expect(sorted).toHaveLength(2);
    expect(sorted.map(stream => stream.addonId)).toContain("org.portal.free");
  });
});