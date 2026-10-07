import { useEffect, useRef } from "react";
import { GAMEPAD_ACTIVITY_EVENT } from "./useGamepad.ts";

/**
 * Con el mando en la mano el cursor sobra: al moverlo se esconde, y vuelve con
 * el ratón o con una tecla real.
 *
 * El hook del mando sintetiza flechas/Enter/Escape con `new KeyboardEvent`
 * (isTrusted = false). Si el teclado se escuchara sin mirar eso, el primer stick
 * de la sesión traeria el cursor de vuelta y la page se quedaria en modo mando
 * con el puntero delante.
 *
 * Es la misma regla que aplica ProfileSelection al alternar entre Big Picture y
 * modo normal; aquí solo se comparte el cursor, porque el flujo de creación no
 * cambia el modo de la app.
 */
export function useHideCursorOnGamepad(callbacks?: {
  /** Actividad del mando: el cursor se esconde. */
  onGamepad?: () => void;
  /** Ratón o teclado real: el cursor vuelve. */
  onNormalInput?: () => void;
}) {
  // Ref para no reiniciar los listeners en cada render del page.
  const handlersRef = useRef(callbacks);
  handlersRef.current = callbacks;

  useEffect(() => {
    const root = document.documentElement;

    const onGamepad = () => {
      root.classList.add("aetherio-bp-hide-cursor");
      handlersRef.current?.onGamepad?.();
    };
    const onNormalInput = () => {
      root.classList.remove("aetherio-bp-hide-cursor");
      handlersRef.current?.onNormalInput?.();
    };
    const onKey = (event: KeyboardEvent) => {
      if (!event.isTrusted) return;
      onNormalInput();
    };

    window.addEventListener(GAMEPAD_ACTIVITY_EVENT, onGamepad);
    window.addEventListener("mousemove", onNormalInput, { passive: true });
    window.addEventListener("keydown", onKey, true);
    return () => {
      window.removeEventListener(GAMEPAD_ACTIVITY_EVENT, onGamepad);
      window.removeEventListener("mousemove", onNormalInput);
      window.removeEventListener("keydown", onKey, true);
    };
  }, []);
}
