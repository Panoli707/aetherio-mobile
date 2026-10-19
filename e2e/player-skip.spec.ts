import { expect, test, type Page } from "@playwright/test";

/**
 * Boton "Saltar intro / outro" de Big Picture.
 *
 * El boton real solo existe cuando MPV devuelve un segmento (`fetch_introdb_segments`,
 * comando nativo) y el arnes no puede simularlo. El diseno vive entero en la
 * clase `.bp-skip-button` mas el `style` del componente, asi que aqui se monta
 * un boton con esas mismas clases dentro del MISMO contexto de posicionamiento
 * (dentro del contenedor `absolute inset-x-0 bottom-0` que envuelve el cristal de
 * la barra) y se mide lo que la referencia fija: caja, radio, tinta y alineacion
 * del extremo derecho con la linea de tiempo.
 *
 * Valores de referencia medidos en artifacts/player-skip/ANALYSIS.md.
 */

async function stubPlayback(page: Page) {
  await page.addInitScript(() => {
    const w = window as unknown as Record<string, unknown>;
    let nextId = 1;
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
    w.__TAURI_INTERNALS__ = {
      transformCallback() { return nextId++; },
      async invoke(cmd: string) {
        if (cmd === "mpv_status") return { ...STATUS, timePos: STATUS.timePos + 1 };
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
      name: "La nobleza de las flores",
      addonName: "Fuente de prueba",
      url: "https://cdn.example/flores.mkv",
      behaviorHints: { headers: {} },
      technicalMetadata: { audioCodec: "eac3", audioChannels: 6, dynamicRange: "Dolby Atmos" },
    }));
    sessionStorage.setItem("aetherio-selected-media-meta", JSON.stringify({
      name: "La nobleza de las flores",
      resumeTime: 48,
    }));
  });
}

const URL_BP = "/big-picture/player?type=movie&id=tmdb%3A4951";

/** Inyecta un boton con las clases reales en el contenedor de la barra. */
async function mountProbeButton(page: Page, label = "Saltar intro") {
  await expect(page.locator("[data-player-controls-glass]")).toBeVisible({ timeout: 20_000 });
  await page.evaluate(text => {
    const glass = document.querySelector("[data-player-controls-glass]");
    const host = glass?.parentElement;
    if (!host) throw new Error("no se encontro el contenedor de la barra");
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.playerSkip = "true";
    button.dataset.probe = "true";
    button.className = "bp-skip-button absolute z-40 flex items-center justify-center whitespace-nowrap gsap-transition";
    button.style.right = "clamp(28px, 6.4vw, 132px)";
    button.style.bottom = "clamp(160px, 23vh, 280px)";
    button.textContent = text;
    host.appendChild(button);
  }, label);
  await page.waitForTimeout(250);
}

test("el boton de saltar cumple la referencia", async ({ page }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1920, height: 1080 });
  await stubPlayback(page);
  await page.goto(URL_BP);
  await page.waitForTimeout(2500);
  const size = page.viewportSize()!;
  await page.mouse.move(size.width / 2, size.height / 2);
  await page.waitForTimeout(700);
  await page.addStyleTag({ content: "*, *::before, *::after { visibility: visible !important; }" });
  await mountProbeButton(page);

  const probe = page.locator("[data-probe='true']");
  await expect(probe).toBeVisible();

  const measured = await probe.evaluate(node => {
    const style = getComputedStyle(node);
    const box = node.getBoundingClientRect();
    const glass = document.querySelector("[data-player-controls-glass]")!;
    const timeline = glass.querySelector("input[type='range']") as HTMLInputElement | null;
    const timelineBox = timeline?.getBoundingClientRect() ?? null;
    const icons = glass.querySelector("[data-bar-actions] button, .bp-bar-actions button");
    const iconsBox = icons?.getBoundingClientRect() ?? null;
    return {
      width: box.width,
      height: box.height,
      right: window.innerWidth - box.right,
      bottom: window.innerHeight - box.bottom,
      radius: Number.parseFloat(style.borderTopLeftRadius),
      fontSize: Number.parseFloat(style.fontSize),
      fontWeight: style.fontWeight,
      background: style.backgroundColor,
      color: style.color,
      borderTopWidth: style.borderTopWidth,
      paddingLeft: Number.parseFloat(style.paddingLeft),
      timelineRight: timelineBox ? window.innerWidth - timelineBox.right : null,
      iconsBottom: iconsBox ? window.innerHeight - iconsBox.top : null,
      glassTop: window.innerHeight - glass.getBoundingClientRect().top,
    };
  });

  // Proporcion de la referencia: 3.8-4.75 : 1. La etiqueta espanola es mas
  // larga que "Skip Intro", asi que se queda en el extremo bajo del rango.
  const ratio = measured.width / measured.height;
  expect(ratio).toBeGreaterThan(3.4);
  expect(ratio).toBeLessThan(6);

  // Alto y aire lateral proporcional al alto. La referencia de la TV da ~1.3 x;
  // aqui se cierra a 0.85 x porque la etiqueta espanola es mas larga y en
  // pantalla la pastilla debia pesar menos.
  expect(measured.paddingLeft / measured.height).toBeGreaterThan(0.7);
  expect(measured.paddingLeft / measured.height).toBeLessThan(1.2);

  // Radio: rectangulo muy redondeado, NO un circulo (seria height/2).
  expect(measured.radius / measured.height).toBeGreaterThan(0.18);
  expect(measured.radius / measured.height).toBeLessThan(0.45);

  // Cuerpo de texto ~0.42 x alto, peso fuerte, tinta sobre relleno claros.
  expect(measured.fontSize / measured.height).toBeGreaterThan(0.3);
  expect(measured.fontSize / measured.height).toBeLessThan(0.55);
  expect(Number(measured.fontWeight)).toBeGreaterThanOrEqual(700);
  expect(measured.background).toBe("rgb(246, 246, 247)");
  expect(measured.color).toBe("rgb(17, 17, 17)");
  expect(measured.borderTopWidth).toBe("0px");

  // Alineacion: el extremo derecho coincide con el de la linea de tiempo.
  if (measured.timelineRight !== null) {
    expect(Math.abs(measured.right - measured.timelineRight)).toBeLessThanOrEqual(2);
  }

  // Vertical: por encima del contenido de la barra (iconos), no encima.
  if (measured.iconsBottom !== null) {
    expect(measured.bottom).toBeGreaterThan(measured.iconsBottom);
  }

  await page.screenshot({ path: "artifacts/player-skip/measure/skip-1920x1080.png" });

  // El foco tiene que leerse: anillo por fuera de la pastilla.
  await probe.evaluate(node => (node as HTMLButtonElement).focus());
  await page.waitForTimeout(150);
  const focused = await probe.evaluate(node => {
    const style = getComputedStyle(node);
    return { shadow: style.boxShadow, transform: style.transform };
  });
  expect(focused.shadow).toContain("rgba(255, 255, 255, 0.6) 0px 0px 0px 3px");
  expect(focused.transform).not.toBe("none");
  await page.screenshot({ path: "artifacts/player-skip/measure/skip-focus.png" });
});

test("una etiqueta larga no rompe la forma", async ({ page }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 1920, height: 1080 });
  await stubPlayback(page);
  await page.goto(URL_BP);
  await page.waitForTimeout(2500);
  const size = page.viewportSize()!;
  await page.mouse.move(size.width / 2, size.height / 2);
  await page.waitForTimeout(600);
  await page.addStyleTag({ content: "*, *::before, *::after { visibility: visible !important; }" });
  await mountProbeButton(page, "Saltar resumen");

  const box = await page.locator("[data-probe='true']").evaluate(node => {
    const rect = node.getBoundingClientRect();
    const style = getComputedStyle(node);
    return {
      height: rect.height,
      width: rect.width,
      radius: Number.parseFloat(style.borderTopLeftRadius),
      overflows: rect.width > window.innerWidth,
    };
  });
  // La etiqueta mas larga no puede cambiar el alto ni el radio (todo sale de
  // --bp-skip-height), y la pastilla no se sale de la ventana.
  expect(box.height).toBeGreaterThan(50);
  expect(box.height).toBeLessThan(80);
  expect(box.radius / box.height).toBeLessThan(0.45);
  expect(box.overflows).toBe(false);
});