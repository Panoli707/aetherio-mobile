import { expect, test, type Page } from "@playwright/test";

async function stubPlayback(page: Page) {
  await page.addInitScript(() => {
    const w = window as unknown as Record<string, unknown>;
    let nextId = 1;
    let statusTick = 0;
    const STATUS = {
      timePos: 806,
      duration: 3646,
      pause: false,
      fileLoaded: true,
      sid: 2,
      aid: 1,
      speed: 1,
      videoWidth: 1920,
      videoHeight: 1080,
      tracks: [
        { id: 2, kind: "sub", type: "sub", title: "Engles", lang: "eng", selected: true },
        { id: 1, type: "audio", title: "English - Dolby Atmos 5.1", lang: "eng" },
      ],
    };
    w.__calls = [];
    w.__TAURI_INTERNALS__ = {
      transformCallback() { return nextId++; },
      async invoke(cmd: string, args?: Record<string, unknown>) {
        (w.__calls as unknown[]).push({ cmd, args });
        if (cmd === "mpv_status") return { ...STATUS, timePos: STATUS.timePos + 1 + statusTick++ * 0.5 };
        if (cmd === "playback_capabilities") return { hardwareDecode: true, secondAudio: true };
        if (cmd.startsWith("plugin:event|")) return nextId++;
        return null;
      },
      convertFileSrc: (p: string) => p,
      metadata: { currentWindow: { label: "main" }, currentWebview: { label: "main" } },
      plugins: {},
    };
    w.__TAURI__ = { event: { listen: async () => () => undefined } };
    localStorage.setItem("aetherio-local-mode-v1", "1");
    sessionStorage.setItem("aetherio-selected-stream", JSON.stringify({
      name: "10 razones para odiarte",
      addonName: "Fuente de prueba",
      url: "https://cdn.example/10-things.mkv",
      behaviorHints: { headers: {} },
      technicalMetadata: { audioCodec: "eac3", audioChannels: 6, dynamicRange: "Dolby Atmos" },
    }));
    sessionStorage.setItem("aetherio-selected-media-meta", JSON.stringify({
      name: "10 razones para odiarte",
      resumeTime: 806,
    }));
  });
}

const URL_BP = "/big-picture/player?type=movie&id=tmdb%3A4951";

type Call = { cmd: string; args?: { name?: string; value?: unknown; command?: unknown[] } };

async function calls(page: Page): Promise<Call[]> {
  return page.evaluate(() => (window as unknown as { __calls: Call[] }).__calls);
}

async function openBar(page: Page) {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1920, height: 1080 });
  await stubPlayback(page);
  await page.goto(URL_BP);
  await page.waitForTimeout(2500);
  await page.mouse.move(960, 540);
  await page.waitForTimeout(700);
  await page.addStyleTag({ content: "*, *::before, *::after { visibility: visible !important; }" });
  await expect(page.locator("[data-player-controls-glass]")).toBeVisible({ timeout: 30_000 });
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
}

const controlsLayer = (page: Page) =>
  page.locator("[data-player-controls-glass]").locator("xpath=..");

test("flechas directas sin barra visible y sin abrirla", async ({ page }) => {
  await openBar(page);
  const root = page.locator("[data-player-direct-input]");
  await expect(root).toHaveAttribute("data-player-direct-input", "true");

  await page.mouse.move(960, 220);
  await page.waitForTimeout(3800);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(600);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "none");

  const before = (await calls(page)).length;
  await page.keyboard.press("ArrowRight");
  await page.waitForTimeout(250);
  await page.keyboard.press("ArrowUp");
  await page.waitForTimeout(250);

  const after = (await calls(page)).slice(before);
  const seek = after.find(call => call.cmd === "mpv_command" && call.args?.command?.[0] === "seek");
  expect(seek?.args?.command).toEqual(["seek", 10, "relative"]);
  const volume = after.filter(call => call.cmd === "mpv_set_property" && call.args?.name === "volume").at(-1);
  expect(volume?.args?.value).toBe(91);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "none");
});

test("Tab revela y enfoca; el foco mantiene la barra al salir con raton", async ({ page }) => {
  await openBar(page);
  await page.mouse.move(960, 220);
  await page.waitForTimeout(3800);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(600);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "none");

  await page.keyboard.press("Tab");
  await page.waitForTimeout(1500);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "auto");
  const focusedInBar = await page.evaluate(() =>
    Boolean(document.activeElement?.closest("[data-player-controls-glass]")));
  expect(focusedInBar).toBe(true);

  await page.mouse.move(20, 20);
  await page.waitForTimeout(900);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "auto");

  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(3800);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(600);
  await expect(controlsLayer(page)).toHaveCSS("pointer-events", "none");

  const volume = page.locator("[data-player-volume]");
  await expect(volume).toHaveAttribute("tabindex", "-1");
});

test("Space en boton enfocado activa una sola vez; menus cierran el bypass", async ({ page }) => {
  await openBar(page);
  await page.mouse.move(960, 540);
  await page.waitForTimeout(300);

  const play = page.locator("[data-player-playpause]");
  await expect(play).toBeVisible();
  await play.focus();
  await play.evaluate(node => {
    (node as HTMLElement & { __clicks?: number }).__clicks = 0;
    node.addEventListener("click", () => {
      const withCount = node as HTMLElement & { __clicks?: number };
      withCount.__clicks = (withCount.__clicks ?? 0) + 1;
    });
  });
  await page.keyboard.press("Space");
  await page.waitForTimeout(400);
  const clicks = await play.evaluate(node => (node as HTMLElement & { __clicks?: number }).__clicks ?? 0);
  expect(clicks).toBe(1);

  const pauseCalls = (await calls(page))
    .filter(call => call.cmd === "mpv_set_property" && call.args?.name === "pause");
  expect(pauseCalls.at(-1)?.args?.value).toBe(true);

  await page.evaluate(() => {
    (document.querySelector('[data-menu-id="subtitles"] button') as HTMLButtonElement | null)?.click();
  });
  await page.waitForTimeout(500);
  const root = page.locator("[data-player-direct-input]");
  await expect(root).toHaveAttribute("data-player-direct-input", "false");
  await page.keyboard.press("Escape");
  await page.waitForTimeout(500);
  await expect(root).toHaveAttribute("data-player-direct-input", "true");
});
