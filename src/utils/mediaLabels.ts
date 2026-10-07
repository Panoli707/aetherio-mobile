/**
 * Etiquetas de un medio (película o episodio) a partir del dato crudo del
 * catálogo: duración y fecha de emisión.
 *
 * El reproductor necesita el mismo formato que la ficha para las tarjetas de
 * episodio. Fuente: minutos, "1h 30m" o segundos en la duración; "YYYY-MM-DD"
 * en la fecha.
 */

function numberValue(value: unknown) {
  const next = Number(value);
  return Number.isFinite(next) && next > 0 ? next : undefined;
}

/** Minutos de duración, o undefined si el catálogo no los trae. */
export function runtimeMinutes(value: unknown) {
  const numeric = numberValue(value);
  if (numeric) return numeric > 300 ? Math.round(numeric / 60) : Math.round(numeric);
  if (typeof value !== "string") return undefined;
  const hours = value.match(/(\d+)\s*h/i);
  const mins = value.match(/(\d+)\s*m/i);
  if (hours || mins) return (hours ? Number(hours[1]) * 60 : 0) + (mins ? Number(mins[1]) : 0);
  return undefined;
}

/** "24 min", "1 h 5 min", "1 h 30 min". Vacío si no hay duración conocida. */
export function formatRuntime(value: unknown) {
  const minutes = runtimeMinutes(value);
  if (!minutes) return "";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${rest} min`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

/** Fecha de emisión legible ("14 mar 2025"). Devuelve el dato si no la parses. */
export function formatAirDate(value?: string) {
  if (!value) return "";
  const trimmed = value.trim();
  if (!trimmed) return "";
  const dateOnly = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})/)?.[0];
  const date = dateOnly ? new Date(`${dateOnly}T00:00:00`) : new Date(trimmed);
  if (Number.isNaN(date.getTime())) return trimmed;
  return date.toLocaleDateString("es", { day: "numeric", month: "short", year: "numeric" });
}
