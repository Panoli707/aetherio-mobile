import type { CSSProperties } from "react";

// Paneles claros de la app (picker de arte, panel lateral del reproductor,
// diálogos de Big Picture y de fiesta...). Los menús contextuales usan
// PLAYER_GLASS_SURFACE, más abajo.
//
// El vidrio se replica en inline porque los paneles se posicionan con estilos
// calculados y deben poder superponerse al canvas sin depender de una clase
// atada a un lado del árbol.
//
// --lg-blur-xl (blur 60 + saturate 200) en vez de --lg-filter-xl: la
// refracción url(#frosted-xl) de .liquid-glass* apenas se lee a tamaño de
// menú/panel y quedaba como "sin material". blur() recorta solo al radio.
export const CONTEXT_GLASS_STYLE: CSSProperties = {
  border: "1px solid rgba(225,230,238,0.09)",
  background: "rgba(255,255,255,0.12)",
  backdropFilter: "var(--lg-blur-xl)",
  WebkitBackdropFilter: "var(--lg-blur-xl)",
  boxShadow: "0 3px 14px rgba(0,0,0,0.38)",
  willChange: "transform, opacity, backdrop-filter",
};

// §14 — con contraste alto el vidrio se oscurece para proteger el texto.
// reduced-transparency NO se honra aquí: el resto de la app (.liquid-glass*)
// tampoco lo honra, y con los efectos de transparencia de Windows apagados
// este ramo pintaba sólidos casi negros junto a una UI de vidrio — el "menú
// negro sin blur" reportado.
export function getContextGlassStyle(): CSSProperties {
  if (typeof window !== "undefined" && window.matchMedia?.("(prefers-contrast: more)").matches) {
    return {
      border: "1px solid rgba(255,255,255,0.18)",
      background: "rgba(40,40,44,0.98)",
      backdropFilter: "blur(12px) saturate(140%)",
      WebkitBackdropFilter: "blur(12px) saturate(140%)",
      boxShadow: "0 3px 14px rgba(0,0,0,0.5)",
      willChange: "transform, opacity, backdrop-filter",
    };
  }
  return CONTEXT_GLASS_STYLE;
}

// Vidrio oscuro de los menús (la piel de la referencia en Big Picture): el
// material del menú de subtítulos, que desde aquí comparten TODOS los menús
// contextuales — cards, detalle, reproductor — en Big Picture y en modo
// normal. Velo oscuro sobre el frost; borde y sombras son los de la
// referencia.
//
// Dentro del reproductor el desenfoque lo pone el shader de MPV (el panel
// entra en su geometría vía data-player-floating-panel-glass) y quien lo usa
// ahí apaga el backdrop-filter: CSS no filtra el video nativo de forma fiable
// en WebView2. Fuera del reproductor el vidrio lo hace --lg-blur-xl, el mismo
// frost fuerte que CONTEXT_GLASS_STYLE.
export const PLAYER_GLASS_SURFACE: CSSProperties = {
  border: "1px solid rgba(225,230,238,0.09)",
  background: "rgba(20,21,24,0.42)",
  backdropFilter: "var(--lg-blur-xl)",
  WebkitBackdropFilter: "var(--lg-blur-xl)",
  boxShadow: "0 28px 70px rgba(0,0,0,0.55), 0 2px 10px rgba(0,0,0,0.4)",
  willChange: "transform, opacity, backdrop-filter",
};

// §14 — con contraste alto el vidrio se oscurece igual que su hermana clara;
// reduced-transparency no se honra (ver nota en getContextGlassStyle).
export function getPlayerGlassSurface(): CSSProperties {
  if (typeof window !== "undefined" && window.matchMedia?.("(prefers-contrast: more)").matches) {
    return {
      border: "1px solid rgba(255,255,255,0.18)",
      background: "rgba(12,13,16,0.98)",
      backdropFilter: "blur(12px) saturate(140%)",
      WebkitBackdropFilter: "blur(12px) saturate(140%)",
      boxShadow: "0 3px 14px rgba(0,0,0,0.5)",
      willChange: "transform, opacity, backdrop-filter",
    };
  }
  return PLAYER_GLASS_SURFACE;
}
