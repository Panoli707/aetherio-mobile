import { useEffect } from "react";
import { GAMEPAD_ACTIVITY_EVENT } from "./useGamepad.ts";

/**
 * Única fuente de verdad del puntero de la app.
 *
 * El shell de Big Picture y el reproductor comparten el mismo <html>: si cada
 * uno alternase su propia clase por su cuenta, el que se montara o desmontara
 * último borraría la del otro y el cursor reaparecería a mitad de reproducción.
 * Por eso la clase se cuenta por "motivos": se esconde si alguno la pide y
 * solo vuelve cuando todos la sueltan.
 */

const HIDDEN_CURSOR_CLASS = "aetherio-hide-cursor";
const holders = new Map<string, number>();

function syncCursorClass() {
  if (typeof document === "undefined") return;
  let hidden = false;
  for (const count of holders.values()) {
    if (count > 0) {
      hidden = true;
      break;
    }
  }
  document.documentElement.classList.toggle(HIDDEN_CURSOR_CLASS, hidden);
}

/** Pide esconder el cursor (apilable por el mismo motivo). */
export function holdCursorHidden(reason: string): void {
  holders.set(reason, (holders.get(reason) ?? 0) + 1);
  syncCursorClass();
}

/** Devuelve el cursor al último motivo que lo escondía, o lo muestra. */
export function releaseCursorHidden(reason: string): void {
  const next = (holders.get(reason) ?? 0) - 1;
  if (next > 0) holders.set(reason, next);
  else holders.delete(reason);
  syncCursorClass();
}

export type CursorPolicy =
  /**
   * Modo TV (Big Picture): arranca escondido, el ratón lo devuelve y se
   * autoesconde; cualquier mando o tecla de navegación se lo lleva por delante.
   */
  | "gamepad"
  /**
   * Modo normal: arranca visible y se esconde solo tras `idleMs` sin puntero,
   * para no estorbar mientras se ve un medio.
   */
  | "idle";

export interface HiddenCursorOptions {
  /** Auto-ocultado tras estas ms sin puntero. 0 = no volver a esconder solo. */
  idleMs?: number;
  enabled?: boolean;
}

/**
 * Aplica la política de puntero mientras el componente esté montado.
 *
 * - El cursor solo aparece si hay ratón (movimiento o botón); ni el mando ni el
 *   teclado lo devuelven, y un mando en la mano lo esconde al instante.
 * - `GAMEPAD_ACTIVITY_EVENT` es la señal fiable: el hook del mando sintetiza
 *   flechas/Enter con `new KeyboardEvent` (isTrusted = false) y el reproductor
 *   de Big Picture las reclama con preventDefault(), así que ahí no llega
 *   ninguna tecla de teclado que sepa esconder el cursor.
 */
export function useHiddenCursor(
  policy: CursorPolicy | null,
  { idleMs = 0, enabled = true }: HiddenCursorOptions = {},
) {
  useEffect(() => {
    if (!enabled || !policy) return;
    const reason = `aetherio-cursor:${policy}`;
    let timer = 0;
    let holding = false;

    const clearTimer = () => {
      if (timer) window.clearTimeout(timer);
      timer = 0;
    };

    const hide = () => {
      clearTimer();
      if (holding) return;
      holding = true;
      holdCursorHidden(reason);
    };

    // El ratón devuelve el cursor y, si la política lo pide, se vuelve a
    // esconder solo pasado el rato (así no se queda clavado en pantalla).
    const show = () => {
      clearTimer();
      if (holding) {
        holding = false;
        releaseCursorHidden(reason);
      }
      if (idleMs > 0) timer = window.setTimeout(() => {
        timer = 0;
        hide();
      }, idleMs);
    };

    const onGamepad = () => hide();
    const onMouse = () => show();
    const onPointerDown = () => show();
    const onKey = (event: KeyboardEvent) => {
      // El mando sintetiza flechas/Enter (isTrusted = false): contarlas como
      // teclado real traería el cursor de vuelta con el primer stick.
      if (policy === "gamepad") {
        hide();
        return;
      }
      if (event.isTrusted) show();
    };

    window.addEventListener(GAMEPAD_ACTIVITY_EVENT, onGamepad);
    window.addEventListener("mousemove", onMouse, { passive: true });
    window.addEventListener("pointerdown", onPointerDown, { passive: true });
    window.addEventListener("keydown", onKey, true);

    // Big Picture es un modo TV: arranca sin cursor. El reproductor en modo
    // normal arranca con el ratón disponible y se esconde al rato.
    if (policy === "gamepad") hide();
    else show();

    return () => {
      clearTimer();
      window.removeEventListener(GAMEPAD_ACTIVITY_EVENT, onGamepad);
      window.removeEventListener("mousemove", onMouse);
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKey, true);
      if (holding) {
        holding = false;
        releaseCursorHidden(reason);
      }
    };
  }, [policy, idleMs, enabled]);
}