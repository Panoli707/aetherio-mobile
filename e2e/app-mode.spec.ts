import { test, expect } from "@playwright/test";

/**
 * El modo (Big Picture / normal) se recuerda POR PERFIL.
 *
 * Escenario que motiva la regla: se entra al perfil 1, se deja en Big Picture,
 * se sale y se vuelve a entrar -> tiene que aparecer en Big Picture. Y el perfil
 * 2, que se usa con el ratón, entra en normal aunque el 1 esté en Big Picture:
 * el modo es de uso personal, no un ajuste de la app.
 */

const PREFIX = "aetherio-last-app-mode-v1";
const SPACE = "aetherio-profile-space-v2:local";
const PROFILES_KEY = `${SPACE}:profiles`;
const ACTIVE_KEY = `${SPACE}:active`;

type Perfil = { id: string; name: string; createdAt: number; updatedAt: number };

async function perfiles(page: import("@playwright/test").Page): Promise<Perfil[]> {
  return page.evaluate(k => JSON.parse(localStorage.getItem(k) ?? "[]") as Perfil[], PROFILES_KEY);
}

async function modo(page: import("@playwright/test").Page, profileId: string) {
  return page.evaluate(([p, id]) => localStorage.getItem(`${p}:${id}`), [PREFIX, profileId] as const);
}

async function activa(page: import("@playwright/test").Page, profileId: string) {
  await page.evaluate(([k, id]) => localStorage.setItem(k, id), [ACTIVE_KEY, profileId] as const);
}

/** Dos perfiles sembrados: el 1 en Big Picture y el 2 en modo normal. */
async function sembrar(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.waitForTimeout(400);
  await page.evaluate(
    ([profilesKey, activeKey, migratedKey, localModeKey, prefix]) => {
      localStorage.setItem(localModeKey, "1");
      localStorage.setItem(migratedKey, "1");
      localStorage.setItem(
        profilesKey,
        JSON.stringify([
          { id: "perfil-1", name: "Uno", createdAt: 1, updatedAt: 1 },
          { id: "perfil-2", name: "Dos", createdAt: 2, updatedAt: 2 },
        ]),
      );
      localStorage.setItem(activeKey, "perfil-1");
      localStorage.setItem(`${prefix}:perfil-1`, "big-picture");
      localStorage.setItem(`${prefix}:perfil-2`, "normal");
    },
    [PROFILES_KEY, ACTIVE_KEY, `${SPACE}:migrated`, "aetherio-local-mode-v1", PREFIX],
  );
}

test("el perfil 1 vuelve a entrar en Big Picture", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  await page.goto("/big-picture");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("big-picture");

  // Salir y reabrir la app: "/" es el arranque en frío, y con un perfil activo
  // es una ruta transitoria que NO debe consolidar nada por el camino.
  await page.goto("/");
  await page.waitForTimeout(2500);
  expect(await modo(page, "perfil-1"), "la raiz no debe pisar su memoria").toBe("big-picture");
  expect(new URL(page.url()).pathname, "y debe abrir en Big Picture").toBe("/big-picture");
});

test("el perfil 2 no hereda el modo del perfil 1", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  // Con el perfil 1 activo, la app abre en Big Picture.
  await page.goto("/");
  await page.waitForTimeout(2500);
  expect(new URL(page.url()).pathname).toBe("/big-picture");

  // Se entra al perfil 2 (uso de ratón): este tiene su propia memoria.
  await activa(page, "perfil-2");
  await page.goto("/");
  await page.waitForTimeout(2500);
  expect(new URL(page.url()).pathname, "el 2 abre en su modo, no en el del 1").toBe("/home");
  expect(await modo(page, "perfil-1"), "y el 1 sigue en Big Picture").toBe("big-picture");
  expect(await modo(page, "perfil-2")).toBe("normal");
});

test("cada ruta estable guarda en el espacio del perfil activo", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  await page.goto("/big-picture");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("big-picture");

  await page.goto("/big-picture/detail/movie/tmdb%3A4951");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("big-picture");

  await page.goto("/home");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("normal");

  await page.goto("/");
  await page.waitForTimeout(2500);
  expect(new URL(page.url()).pathname, "tras dejar normal, la raiz abre en Home").toBe("/home");
});

test("las transitorias no pisan la memoria del perfil", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  await page.goto("/big-picture");
  await page.waitForTimeout(900);

  await page.goto("/profiles");
  await page.waitForTimeout(2000);
  expect(await modo(page, "perfil-1")).toBe("big-picture");

  await page.goto("/quick-start/profile");
  await page.waitForTimeout(2000);
  expect(await modo(page, "perfil-1")).toBe("big-picture");
});

test("el cambio de modo dentro de la app se recuerda", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  await page.goto("/big-picture");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("big-picture");

  // El usuario pasa a normal dentro de la app.
  await page.goto("/home");
  await page.waitForTimeout(900);
  expect(await modo(page, "perfil-1")).toBe("normal");

  // Sale del todo y vuelve a entrar por la raíz.
  await page.goto("/");
  await page.waitForTimeout(2500);
  expect(new URL(page.url()).pathname).toBe("/home");
  expect(await modo(page, "perfil-1")).toBe("normal");
});

test("la actividad del mando en Big Picture no deja el cursor oculto en normal", async ({ page }) => {
  test.setTimeout(120_000);
  await sembrar(page);

  await page.goto("/big-picture");
  await page.waitForTimeout(900);

  await page.evaluate(() => {
    for (let i = 0; i < 5; i += 1) {
      window.dispatchEvent(new CustomEvent("aetherio-gamepad-activity"));
    }
  });
  const oculto = () => page.evaluate(() =>
    document.documentElement.classList.contains("aetherio-hide-cursor"));
  expect(await oculto()).toBe(true);

  await page.keyboard.press("F10");
  await page.waitForURL("**/home", { timeout: 15_000 });

  await expect.poll(oculto, {
    message: "el cursor debe volver al salir de Big Picture",
    timeout: 10_000,
  }).toBe(false);
});

test("sin perfiles sembrados la app arranca en la puerta de entrada", async ({ page }) => {
  test.setTimeout(120_000);
  await page.goto("/");
  await page.waitForTimeout(2500);

  // Sin perfil activo no hay modo que recordar, así que la raíz se queda donde
  // está mostrando la puerta: no debe inventarse un destino.
  expect((await perfiles(page)).length).toBe(0);
  expect(new URL(page.url()).pathname).toBe("/");
  expect(await page.locator("body").innerText()).not.toMatch(/cannot read|undefined is not/i);
});