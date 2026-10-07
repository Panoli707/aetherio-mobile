import clsx from "clsx";
import "./ToggleSwitch.css";

interface ToggleSwitchProps {
  checked: boolean;
  onChange: () => void;
  disabled?: boolean;
  size?: "md" | "lg";
  /** Nombre accesible; por defecto se deriva del contexto de la fila. */
  label?: string;
}

/**
 * Toggle unico de la app. La geometria (pista 2.35:1, knob 0.82:1, recorrido
 * completo) viene de la referencia de diseno; las marcas "|" y "o" de esa
 * referencia son guides de anotacion y quedan excluidas a proposito.
 */
export default function ToggleSwitch({
  checked,
  onChange,
  disabled = false,
  size = "md",
  label,
}: ToggleSwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={onChange}
      className={clsx("aetherio-toggle", size === "lg" && "aetherio-toggle--lg")}
    >
      <span className="aetherio-toggle__knob" />
    </button>
  );
}

/**
 * Version presentacional para cuando el control interactivo es el contenedor
 * (filas de Big Picture). Mantiene el mismo lenguaje visual.
 */
export function TogglePill({ checked }: { checked: boolean }) {
  return (
    <span className="aetherio-toggle-pill" data-checked={checked ? "true" : "false"} aria-hidden="true">
      <span className="aetherio-toggle__knob" />
    </span>
  );
}