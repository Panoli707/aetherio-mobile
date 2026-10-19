import { useEffect, useRef } from "react";
import gsap from "gsap";
import type { ContentRating } from "./contentRating";
import { elegantEase } from "../../utils/motion";

const HOLD_MS = 4_200;
const ENTER_MS = 0.72;
const EXIT_MS = 0.6;

interface PlayerContentRatingProps {
  rating: ContentRating;
  bigPicture: boolean;
  /** Algo mas urgente tiene la pantalla: el aviso se retira sin ceremonia. */
  dismissed: boolean;
  onComplete: () => void;
}

/**
 * Aviso de clasificacion por edad sobre el video.
 *
 * Solo texto: sin panel, sin borde, sin chips y sin desenfoque de fondo. Sobre
 * una imagen en movimiento un marco de cristal compite con lo que se esta
 * viendo; una linea de texto con sombra se apoya en el video y se aparta.
 *
 * La entrada y la salida son un fundido largo y lento. Deliberadamente no hay
 * escala ni spring: un rebote delataria que el elemento acaba de aparecer, y
 * aqui interesa lo contrario -- que el aviso este y se vaya como si fuera parte
 * de la imagen.
 */
export default function PlayerContentRating({
  rating,
  bigPicture,
  dismissed,
  onComplete,
}: PlayerContentRatingProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const finishedRef = useRef(false);
  const holdTimerRef = useRef<number | null>(null);
  const exitStartedRef = useRef(false);
  const dismissRequestedRef = useRef(false);
  const contentKey = `${rating.age}:${rating.advisories.join("|")}`;

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    let active = true;

    const finish = () => {
      if (finishedRef.current) return;
      finishedRef.current = true;
      active = false;
      onComplete();
    };

    const startExit = () => {
      if (exitStartedRef.current || finishedRef.current) return;
      exitStartedRef.current = true;
      if (holdTimerRef.current !== null) {
        window.clearTimeout(holdTimerRef.current);
        holdTimerRef.current = null;
      }
      gsap.killTweensOf(root);
      // Desde el valor que hubiera en pantalla: si el descarte llega durante la
      // entrada, el fundido sale desde ahi y no da un salto.
      gsap.to(root, { opacity: 0, duration: EXIT_MS, ease: "power2.in", overwrite: "auto" });
      window.setTimeout(finish, EXIT_MS * 1000);
    };

    if (dismissed) dismissRequestedRef.current = true;
    if (dismissRequestedRef.current) {
      startExit();
    } else {
      gsap.fromTo(
        root,
        { opacity: 0 },
        {
          opacity: 1,
          duration: ENTER_MS,
          ease: elegantEase,
          overwrite: "auto",
          onComplete: () => {
            if (active) holdTimerRef.current = window.setTimeout(startExit, HOLD_MS);
          },
        },
      );
    }

    return () => {
      active = false;
      if (holdTimerRef.current !== null) window.clearTimeout(holdTimerRef.current);
      gsap.killTweensOf(root);
    };
  }, [contentKey, dismissed, onComplete]);

  const age = rating.age ?? "";

  return (
    <div
      data-player-content-rating
      role="status"
      aria-live="polite"
      aria-label={
        rating.advisories.length
          ? `Clasificacion ${age}. ${rating.advisories.join(". ")}`
          : `Clasificacion ${age}`
      }
      className="pointer-events-none absolute z-30"
      style={{
        top: bigPicture ? "clamp(28px, 5vh, 56px)" : "clamp(18px, 3.6vh, 40px)",
        left: bigPicture ? "clamp(24px, 3.6vw, 60px)" : "clamp(18px, 3vw, 44px)",
        // El ancho escala con el cuerpo para que la linea de avisos siga
        // cabiendo en una sola fila: a 18-19px el tope anterior la partia.
        maxWidth: bigPicture ? 630 : 540,
        color: "#fff",
        textShadow: bigPicture
          ? "0 1px 2px rgba(0,0,0,0.55), 0 2px 14px rgba(0,0,0,0.7)"
          : "0 1px 2px rgba(0,0,0,0.5), 0 2px 12px rgba(0,0,0,0.65)",
        fontFamily: "Inter, system-ui, sans-serif",
        fontOpticalSizing: "auto",
      }}
    >
      <div ref={rootRef} style={{ opacity: 0 }}>
        <p
          className={`${bigPicture ? "text-[45px] leading-none" : "text-[39px] leading-none"} font-bold tracking-[-0.02em]`}
        >
          {age}
        </p>
        {rating.advisories.length ? (
          <p
            className={`${bigPicture ? "mt-[9px] text-[19.5px] leading-[1.45]" : "mt-[9px] text-[18px] leading-[1.45]"} text-white/78`}
          >
            {rating.advisories.join(" · ")}
          </p>
        ) : null}
      </div>
    </div>
  );
}