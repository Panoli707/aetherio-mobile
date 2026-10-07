import { useEffect, useMemo, useRef } from "react";
import { Delete } from "lucide-react";
import { GAMEPAD_ACTION_EVENT, type GamepadActionDetail } from "../../hooks/useGamepad.ts";
import "./NameKeyboard.css";

export type KeyboardMode = "abc" | "ABC" | "123";

interface NameKeyboardProps {
  value: string;
  onChange: (value: string) => void;
  mode: KeyboardMode;
  onModeChange: (mode: KeyboardMode) => void;
  maxLength?: number;
}

const ROWS: Record<KeyboardMode, string[][]> = {
  abc: [["a", "b", "c", "d", "e", "f"], ["g", "h", "i", "j", "k", "l"], ["m", "n", "ñ", "o", "p", "q"], ["r", "s", "t", "u", "v", "w"], ["x", "y", "z"]],
  ABC: [["A", "B", "C", "D", "E", "F"], ["G", "H", "I", "J", "K", "L"], ["M", "N", "Ñ", "O", "P", "Q"], ["R", "S", "T", "U", "V", "W"], ["X", "Y", "Z"]],
  "123": [["1", "2", "3", "4", "5", "6"], ["7", "8", "9", "0", ".", "-"], ["@", "#", "&", "_", "/", ":"]],
};

const MODES: KeyboardMode[] = ["abc", "ABC", "123"];

/**
 * Teclado en pantalla para escribir el nombre del perfil (referencia 1 y 2):
 * pestañas abc/ABC/123, retícula de 6 columnas y fila ESPACIO / ⌫ / BORRAR.
 *
 * Es el mismo control que el teclado de Buscar pero con la composición de la
 * referencia: cada tecla es un <button> real (mando por geometría, ratón con
 * click) y el teclado físico escribe sobre el mismo valor.
 */
export default function NameKeyboard({
  value,
  onChange,
  mode,
  onModeChange,
  maxLength = 32,
}: NameKeyboardProps) {
  const firstKeyRef = useRef<HTMLButtonElement>(null);
  const rows = useMemo(() => ROWS[mode], [mode]);
  // Igual que en el pad de PIN: el listener de window no puede leer el closure
  // viejo o dos pulsaciones seguidas en el mismo tick se pisan.
  const valueRef = useRef(value);
  valueRef.current = value;

  useEffect(() => {
    firstKeyRef.current?.focus({ preventScroll: true });
  }, [mode]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && /^(INPUT|TEXTAREA|SELECT)$/i.test(target.tagName)) return;
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      const current = valueRef.current;
      if (event.key === "Backspace") {
        event.preventDefault();
        const next = current.slice(0, -1);
        valueRef.current = next;
        onChange(next);
        return;
      }
      if (event.key === " " || event.key === "Spacebar") {
        event.preventDefault();
        if (current.length >= maxLength || current === "" || current.endsWith(" ")) return;
        const next = `${current} `;
        valueRef.current = next;
        onChange(next);
        return;
      }
      if (event.key === "Escape" || event.key === "Esc") return;
      if (event.key.length === 1 && /[a-zA-Z0-9ñÑáéíóúüÁÉÍÓÜ@#&_/:.\- ]/.test(event.key)) {
        event.preventDefault();
        if (current.length >= maxLength) return;
        const next = `${current}${event.key}`;
        valueRef.current = next;
        onChange(next);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [maxLength, onChange]);

  /**
   * Las pestañas son un selector de modo, no una tecla: el foco del mando no las
   * recorre (el tablist va con data-spatial-ignore) y se cambian solo con LB / RB,
   * como en la referencia. El ratón y el Tab del teclado siguen entrampando igual.
   */
  useEffect(() => {
    const onAction = (event: Event) => {
      const id = (event as CustomEvent<GamepadActionDetail>).detail?.id;
      if (id !== "lb" && id !== "rb") return;
      event.preventDefault();
      const current = MODES.indexOf(mode);
      const delta = id === "rb" ? 1 : -1;
      onModeChange(MODES[(current + delta + MODES.length) % MODES.length]);
    };
    window.addEventListener(GAMEPAD_ACTION_EVENT, onAction);
    return () => window.removeEventListener(GAMEPAD_ACTION_EVENT, onAction);
  }, [mode, onModeChange]);

  const push = (key: string) => {    const current = valueRef.current;
    if (key === " " && (current.length >= maxLength || current === "" || current.endsWith(" "))) return;
    if (current.length >= maxLength) return;
    const next = `${current}${key}`;
    valueRef.current = next;
    onChange(next);
  };
  const erase = () => {
    const next = valueRef.current.slice(0, -1);
    valueRef.current = next;
    onChange(next);
  };
  const clear = () => {
    valueRef.current = "";
    onChange("");
  };

  return (
    <div className="qs-kb" role="group" aria-label="Teclado en pantalla">
      <div className="qs-kb__tabs" role="tablist" aria-label="Modo del teclado" data-spatial-ignore>
        {MODES.map(tab => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={mode === tab}
            className={mode === tab ? "qs-kb__tab is-active" : "qs-kb__tab"}
            data-qs-kb-tab={tab}
            onClick={() => onModeChange(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="qs-kb__grid">
        {rows.map((row, rowIndex) => (
          <div key={rowIndex} className="qs-kb__row">
            {row.map((key, keyIndex) => (
              <button
                key={`${rowIndex}-${key}`}
                type="button"
                ref={rowIndex === 0 && keyIndex === 0 ? firstKeyRef : undefined}
                className="qs-kb__key"
                data-qs-kb-key={key}
                aria-label={`Escribir ${key}`}
                onClick={() => push(key)}
              >
                {key}
              </button>
            ))}
          </div>
        ))}
      </div>

      <div className="qs-kb__actions">
        <button type="button" className="qs-kb__action" onClick={() => push(" ")}>ESPACIO</button>
        <button
          type="button"
          className="qs-kb__action qs-kb__action--icon"
          aria-label="Borrar último carácter"
          onClick={erase}
        >
          <Delete strokeWidth={2.4} />
        </button>
        <button type="button" className="qs-kb__action" onClick={clear}>BORRAR</button>
      </div>
    </div>
  );
}