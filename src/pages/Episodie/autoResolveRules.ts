import type { SourceSelectionMode } from "../../config/playbackPreferences";

export interface AutoResolveEntry {
  /** El boton "Continuar" del Detail (o la row de "sigo viendo"). */
  continueRequested: boolean;
  /** `autoplay=1`: el capitulo siguiente encadena solo. */
  autoplayRequested: boolean;
  sourceSelectionMode: SourceSelectionMode;
  /**
   * Senal de cache disponible. Cada llamante decide que significa "cache": el
   * loader usa "existe un enlace guardado" (puede que su source ya no este
   * entre las cargadas) y la resolucion usa "ese enlace sigue en la lista".
   */
  cacheSignal: boolean;
}

/**
 * ¿Esta entrada al panel de fuentes puede resolverse sola?
 *
 * "Continuar" NUNCA auto-resuelve: el panel sale siempre, con la fuente de la
 * entrada de progreso preseleccionada pero no impuesta. Elegir la stream es una
 * decision del usuario, y el minuto donde lo dejo no se pierde porque
 * `playStream` reanuda desde la entrada de progreso al elegir.
 *
 * El resto son las preferencias de reproduccion: el capitulo siguiente
 * encadena solo, "primera fuente" se resuelve sola, y "manual" solo se resuelve
 * si hay una señal de cache (reutilizar el ultimo enlace).
 */
export function shouldAutoResolveOnEntry(entry: AutoResolveEntry): boolean {
  if (entry.continueRequested) return false;
  if (entry.autoplayRequested) return true;
  if (entry.sourceSelectionMode === "first") return true;
  if (entry.sourceSelectionMode !== "manual") return false;
  return entry.cacheSignal;
}