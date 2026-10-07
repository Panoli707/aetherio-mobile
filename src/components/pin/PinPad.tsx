import { useEffect, useRef } from "react";
import { Delete } from "lucide-react";
import { GAMEPAD_ACTION_EVENT, type GamepadActionDetail } from "../../hooks/useGamepad.ts";
import "./PinPad.css";

interface PinPadProps {
  value: string;
  onChange: (value: string) => void;
  /** Se dispara al completar los 4 dígitos (como la referencia: sin botón). */
  onComplete?: () => void;
  onCancel?: () => void;
  title?: string;
  subtitle?: string;
  /** El PIN de Aetherio son 4 dígitos: la fila de casillas nunca crece. */
  length?: number;
  error?: string;
}

/**
 * Campo de PIN numérico, 1:1 con la interfaz de passcode de la referencia:
 * título, instrucción, casillas y fila de dígitos con el borrador al final.
 *
 * No hay campo de texto ni botones: cada dígito es un <button> (el motor
 * espacial los recoge por geometría dentro del modal y el ratón los pulsa), el
 * teclado físico escribe sobre el mismo valor, y al completar los dígitos se
 * verifica solo. Cancelar es con B / Esc / atrás o clic fuera.
 */
export default function PinPad({
  value,
  onChange,
  onComplete,
  onCancel,
  title = "Introduce el PIN",
  subtitle = "Introduce tu PIN de cuatro dígitos.",
  length = 4,
  error = "",
}: PinPadProps) {
  const firstKeyRef = useRef<HTMLButtonElement>(null);
  const submittedRef = useRef(false);
  const prevLengthRef = useRef(value.length);
  // El valor vive tambien en un ref: el listener de teclado esta en `window` y
  // dos pulsaciones seguidas dentro del mismo tick de React leerian el closure
  // viejo y la segunda machacaria a la primera (se perdian digitos).
  const valueRef = useRef(value);
  valueRef.current = value;

  useEffect(() => {
    firstKeyRef.current?.focus({ preventScroll: true });
  }, []);

  // Tras un PIN incorrecto el valor se vacía y el nodo del primer dígito se
  // vuelve a crear: sin esto el foco se queda en el body y el mando no tiene
  // a dónde volver.
  useEffect(() => {
    if (!error) return;
    firstKeyRef.current?.focus({ preventScroll: true });
  }, [error]);

  // Verificación automática al completar (como la referencia). Solo al pasar de
  // `length - 1` a `length`, que es el gesto de escribir el último dígito: el pad
  // puede montarse ya relleno (QuickStart abre el del PIN que ya está puesto) y
  // en ese primer render no debe cerrarse solo.
  useEffect(() => {
    const previousLength = prevLengthRef.current;
    prevLengthRef.current = value.length;
    if (value.length !== length || previousLength !== length - 1) return;
    if (submittedRef.current) return;
    submittedRef.current = true;
    onComplete?.();
  }, [length, onComplete, value]);

  useEffect(() => {
    if (value.length < length) submittedRef.current = false;
  }, [length, value]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && /^(INPUT|TEXTAREA|SELECT)$/i.test(target.tagName)) return;
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      const current = valueRef.current;
      if (/^[0-9]$/.test(event.key)) {
        event.preventDefault();
        if (current.length >= length) return;
        const next = `${current}${event.key}`;
        valueRef.current = next;
        onChange(next);
        return;
      }
      if (event.key === "Backspace") {
        event.preventDefault();
        const next = current.slice(0, -1);
        valueRef.current = next;
        onChange(next);
        return;
      }
      if (event.key === "Enter") {
        if (current.length < length) return;
        event.preventDefault();
        onComplete?.();
        return;
      }
      if (event.key === "Escape" || event.key === "Esc") onCancel?.();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [length, onCancel, onChange, onComplete]);

  const push = (digit: string) => {
    const current = valueRef.current;
    if (current.length >= length) return;
    const next = `${current}${digit}`;
    valueRef.current = next;
    onChange(next);
  };
  const erase = () => {
    const next = valueRef.current.slice(0, -1);
    valueRef.current = next;
    onChange(next);
  };
  const eraseRef = useRef(erase);
  eraseRef.current = erase;

  /**
   * X del mando borra el último dígito, igual que el borrador de la fila.
   *
   * Fuera de un teclado en pantalla X solo borra si hay un contexto de texto
   * montado (useTextBackAction), que es el caso de Buscar pero no del pad: aquí
   * el que lo atiende es el propio pad. Se consume la acción para que ningún
   * otro contexto la reclame mientras el pad está en pantalla.
   */
  useEffect(() => {
    const onAction = (event: Event) => {
      const id = (event as CustomEvent<GamepadActionDetail>).detail?.id;
      if (id !== "x") return;
      event.preventDefault();
      eraseRef.current();
    };
    window.addEventListener(GAMEPAD_ACTION_EVENT, onAction);
    return () => window.removeEventListener(GAMEPAD_ACTION_EVENT, onAction);
  }, []);

  return (
    <div className={`pin-pad${error ? " pin-pad--error" : ""}`}>
      <div className="pin-pad__head">
        <h2 className="pin-pad__title">{title}</h2>
        <p className="pin-pad__subtitle">{subtitle}</p>
      </div>

      <div className="pin-pad__slots" aria-hidden="true">
        {Array.from({ length }).map((_, index) => (
          <span key={index} className={index < value.length ? "pin-pad__slot is-filled" : "pin-pad__slot"} />
        ))}
      </div>

      {error ? <p className="pin-pad__error">{error}</p> : null}

      <div className="pin-pad__keys" role="group" aria-label="Teclado numérico del PIN">
        {["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"].map((digit, index) => (
          <button
            key={digit}
            type="button"
            ref={index === 0 ? firstKeyRef : undefined}
            className="pin-pad__key"
            data-pin-key={digit}
            aria-label={`Escribir ${digit}`}
            onClick={() => push(digit)}
          >
            {digit}
          </button>
        ))}
        <button
          type="button"
          className="pin-pad__key pin-pad__key--del"
          data-pin-key="del"
          aria-label="Borrar último dígito"
          onClick={erase}
        >
          <Delete strokeWidth={2.2} />
        </button>
      </div>

      <p className="sr-only" aria-live="polite">
        {value.length ? `${value.length} de ${length} dígitos` : "Sin dígitos"}
      </p>
    </div>
  );
}