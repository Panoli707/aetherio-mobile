import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  closeWindow,
  enterBigPictureWindow,
  exitBigPictureWindow,
  getRuntimeKind,
  installRuntimeDocumentClasses,
  isDesktopRuntime,
  isTauriRuntime,
  isWindowFullscreen,
  listenGamepadWake,
  listenOpenFiles,
  listenWindowFileDrops,
  maximizeWindow,
  minimizeWindow,
  setBigPictureActive,
  showAndFocusWindow,
  takePendingOpenFiles,
  toggleWindowFullscreen,
} from "./platform";

vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn() }));
vi.mock("@tauri-apps/api/window", () => ({ getCurrentWindow: vi.fn() }));
vi.mock("../navigation/spatialNav.ts", () => ({ installSpatialNavigation: vi.fn() }));

beforeEach(() => {
  vi.stubEnv("TAURI_ENV_PLATFORM", "");
  vi.stubGlobal("window", { __TAURI_INTERNALS__: {} });
  vi.stubGlobal("navigator", { userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("runtime detection", () => {
  it("does not classify an Android browser as a native app", () => {
    vi.stubGlobal("window", {});
    vi.stubGlobal("navigator", { userAgent: "Mozilla/5.0 (Linux; Android 14)" });
    vi.stubEnv("TAURI_ENV_PLATFORM", "android");

    expect(getRuntimeKind()).toBe("web");
    expect(isTauriRuntime()).toBe(false);
    expect(isDesktopRuntime()).toBe(false);
  });

  it("is safe outside a browser", () => {
    vi.stubGlobal("window", undefined);
    vi.stubGlobal("navigator", undefined);

    expect(getRuntimeKind()).toBe("web");
  });

  it("recognizes an Android WebView without a build target", () => {
    vi.stubGlobal("navigator", { userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 7)" });

    expect(getRuntimeKind()).toBe("android");
    expect(isTauriRuntime()).toBe(true);
    expect(isDesktopRuntime()).toBe(false);
  });

  it.each(["android", "androideabi"])("recognizes the %s build target with a desktop user agent", platform => {
    vi.stubEnv("TAURI_ENV_PLATFORM", platform);

    expect(getRuntimeKind()).toBe("android");
    expect(isTauriRuntime()).toBe(true);
  });

  it.each(["windows", "linux", "macos"])("preserves the %s desktop runtime", platform => {
    vi.stubEnv("TAURI_ENV_PLATFORM", platform);
    vi.stubGlobal("navigator", { userAgent: "Android" });

    expect(getRuntimeKind()).toBe("desktop");
    expect(isDesktopRuntime()).toBe(true);
  });

  it("preserves desktop detection without a build target", () => {
    expect(getRuntimeKind()).toBe("desktop");
    expect(isDesktopRuntime()).toBe(true);
  });

  it("supports the existing global Tauri marker", () => {
    vi.stubGlobal("window", { __TAURI__: {} });

    expect(isDesktopRuntime()).toBe(true);
  });

  it("sets the Android document marker and cleans up event listeners", () => {
    vi.stubEnv("TAURI_ENV_PLATFORM", "android");
    const root = { dataset: {} };
    const addWindowListener = vi.fn();
    const removeWindowListener = vi.fn();
    const addDocumentListener = vi.fn();
    const removeDocumentListener = vi.fn();
    vi.stubGlobal("window", {
      __TAURI_INTERNALS__: {},
      addEventListener: addWindowListener,
      removeEventListener: removeWindowListener,
    });
    vi.stubGlobal("document", {
      documentElement: root,
      addEventListener: addDocumentListener,
      removeEventListener: removeDocumentListener,
    });

    const cleanup = installRuntimeDocumentClasses();
    expect(root.dataset).toEqual({ aetherioRuntime: "android" });
    cleanup();
    expect(addWindowListener).toHaveBeenCalledTimes(2);
    expect(removeWindowListener).toHaveBeenCalledTimes(2);
    expect(addDocumentListener).toHaveBeenCalledTimes(1);
    expect(removeDocumentListener).toHaveBeenCalledTimes(1);
  });
});

describe("desktop integrations", () => {
  it.each(["android", "androideabi"])("does not invoke desktop APIs on %s", async platform => {
    vi.stubEnv("TAURI_ENV_PLATFORM", platform);
    vi.stubGlobal("document", {
      documentElement: { classList: { toggle: vi.fn() } },
    });

    await minimizeWindow();
    await maximizeWindow();
    await closeWindow();
    await showAndFocusWindow();
    await toggleWindowFullscreen();
    // Android must not wait for the desktop splash to mark startup ready.
    await enterBigPictureWindow();
    await exitBigPictureWindow();
    setBigPictureActive(true);
    expect(await isWindowFullscreen()).toBe(false);
    expect(await takePendingOpenFiles()).toEqual([]);
    (await listenWindowFileDrops(vi.fn()))();
    (await listenOpenFiles(vi.fn()))();
    (await listenGamepadWake(vi.fn()))();

    expect(invoke).not.toHaveBeenCalled();
    expect(listen).not.toHaveBeenCalled();
    expect(getCurrentWindow).not.toHaveBeenCalled();
  });

  it("keeps the native maximize command available on desktop", async () => {
    vi.mocked(invoke).mockResolvedValueOnce(undefined);

    await maximizeWindow();

    expect(invoke).toHaveBeenCalledWith("toggle_window_maximize", undefined);
  });

  it("keeps pending file handling available on desktop", async () => {
    vi.mocked(invoke).mockResolvedValueOnce(["C:\\Videos\\movie.mp4"]);

    expect(await takePendingOpenFiles()).toEqual(["C:\\Videos\\movie.mp4"]);
    expect(invoke).toHaveBeenCalledWith("take_pending_open_files", undefined);
  });

  it("preserves browser fullscreen when entering and leaving Big Picture", async () => {
    vi.stubGlobal("window", {});
    const requestFullscreen = vi.fn().mockResolvedValue(undefined);
    const exitFullscreen = vi.fn().mockResolvedValue(undefined);
    const browserDocument = {
      documentElement: { requestFullscreen },
      fullscreenElement: null as object | null,
      exitFullscreen,
    };
    vi.stubGlobal("document", browserDocument);

    await enterBigPictureWindow();
    expect(requestFullscreen).toHaveBeenCalledOnce();
    browserDocument.fullscreenElement = {};
    await exitBigPictureWindow();
    expect(exitFullscreen).toHaveBeenCalledOnce();
    expect(invoke).not.toHaveBeenCalled();
  });
});
