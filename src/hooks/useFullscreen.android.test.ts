import { afterEach, describe, expect, it, vi } from "vitest";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useFullscreen } from "./useFullscreen";

vi.mock("react", () => ({
  useCallback: (callback: unknown) => callback,
  useEffect: (setup: () => unknown) => setup(),
}));
vi.mock("@tauri-apps/api/window", () => ({ getCurrentWindow: vi.fn() }));
vi.mock("../navigation/spatialNav.ts", () => ({ installSpatialNavigation: vi.fn() }));

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("fullscreen runtime guard", () => {
  it("does not register F11 or request a Tauri window on Android", () => {
    vi.stubEnv("TAURI_ENV_PLATFORM", "android");
    const addEventListener = vi.fn();
    vi.stubGlobal("window", { __TAURI_INTERNALS__: {}, addEventListener });
    useFullscreen();
    expect(addEventListener).not.toHaveBeenCalled();
    expect(getCurrentWindow).not.toHaveBeenCalled();
  });

  it("keeps desktop F11 maximizing the native window", async () => {
    vi.stubEnv("TAURI_ENV_PLATFORM", "windows");
    const addEventListener = vi.fn();
    const toggleMaximize = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("window", { __TAURI_INTERNALS__: {}, addEventListener });
    vi.mocked(getCurrentWindow).mockReturnValue({ toggleMaximize } as unknown as ReturnType<typeof getCurrentWindow>);
    useFullscreen();
    const handler = addEventListener.mock.calls[0][1];
    handler({ key: "F11", preventDefault: vi.fn() });
    await vi.waitFor(() => expect(toggleMaximize).toHaveBeenCalledOnce());
  });
});
