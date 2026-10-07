import { beforeEach, describe, expect, it } from "vitest";
import {
  getLocalProfiles,
  hashPin,
  migratePinsToFourDigits,
  needsPinLengthMigration,
  PIN_LENGTH,
  verifyPin,
  writeLocalProfilesForTest,
} from "./localProfiles.ts";

const SALT = "aetherio-profile-v1";

function storage() {
  const map = new Map<string, string>();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
      getItem: (key: string) => map.get(key) ?? null,
      setItem: (key: string, value: string) => void map.set(key, value),
      removeItem: (key: string) => void map.delete(key),
      clear: () => map.clear(),
      key: (index: number) => [...map.keys()][index] ?? null,
      get length() { return map.size; },
    },
  });
  return map;
}

async function legacyPinHash(pin: string) {
  const data = new TextEncoder().encode(`${SALT}:${pin}`);
  const buffer = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(buffer)).map(b => b.toString(16).padStart(2, "0")).join("");
}

describe("migración de PINs a 4 dígitos", () => {
  beforeEach(() => {
    storage();
    // El entorno es node: localProfiles despacha eventos de window.
    Object.defineProperty(globalThis, "window", {
      configurable: true,
      value: { dispatchEvent: () => true, addEventListener: () => undefined, removeEventListener: () => undefined },
      writable: true,
    });
  });

  it("conserva el PIN si es de 4 dígitos", async () => {
    const pin = await hashPin("4821");
    writeLocalProfilesForTest([
      { id: "a", name: "A", pin, createdAt: 1, updatedAt: 1 },
    ]);
    const released = await migratePinsToFourDigits();
    expect(released).toEqual([]);
    expect(getLocalProfiles()[0].pin).toBe(pin);
    expect(await verifyPin("4821", getLocalProfiles()[0].pin!)).toBe(true);
  });

  it("quita el PIN legacy (5-8 dígitos) y deja el perfil accesible", async () => {
    const legacy = await legacyPinHash("12345678");
    writeLocalProfilesForTest([
      { id: "a", name: "Legacy", pin: legacy, createdAt: 1, updatedAt: 1 },
    ]);
    expect(needsPinLengthMigration()).toBe(true);

    const released = await migratePinsToFourDigits();
    expect(released).toEqual(["Legacy"]);
    expect(getLocalProfiles()[0].pin).toBeUndefined();
    // Sin PIN: el perfil entra sin pedir nada.
    expect(needsPinLengthMigration()).toBe(false);
  });

  it("solo toca los PINs legacy y respeta el resto del perfil", async () => {
    const good = await hashPin("0000");
    const legacy = await legacyPinHash("12");
    writeLocalProfilesForTest([
      { id: "a", name: "OK", pin: good, createdAt: 1, updatedAt: 1 },
      { id: "b", name: "Legacy", pin: legacy, avatarDataUrl: "data:image/png;base64,x", createdAt: 2, updatedAt: 2 },
      { id: "c", name: "Sin PIN", createdAt: 3, updatedAt: 3 },
    ]);
    const released = await migratePinsToFourDigits();
    expect(released).toEqual(["Legacy"]);
    const [a, b, c] = getLocalProfiles();
    expect(a.pin).toBe(good);
    expect(b.pin).toBeUndefined();
    expect(b.avatarDataUrl).toBe("data:image/png;base64,x");
    expect(c.pin).toBeUndefined();
  });

  it("corre una sola vez (idempotente)", async () => {
    writeLocalProfilesForTest([
      { id: "a", name: "Legacy", pin: await legacyPinHash("987654"), createdAt: 1, updatedAt: 1 },
    ]);
    await migratePinsToFourDigits();
    // Aunque se vuelva a llamar, no hay que rehacer los 10.000 hashes.
    const started = performance.now();
    const released = await migratePinsToFourDigits();
    expect(released).toEqual([]);
    expect(performance.now() - started).toBeLessThan(50);
  });

  it("no hace nada cuando nadie tiene PIN", async () => {
    writeLocalProfilesForTest([{ id: "a", name: "Sin PIN", createdAt: 1, updatedAt: 1 }]);
    const started = performance.now();
    const released = await migratePinsToFourDigits();
    expect(released).toEqual([]);
    expect(performance.now() - started).toBeLessThan(50);
  });

  it("el espacio de PIN son 4 dígitos", () => {
    expect(PIN_LENGTH).toBe(4);
  });
});