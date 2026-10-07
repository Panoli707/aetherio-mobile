import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { ChevronRight, ImagePlus, Info, UserRound } from "lucide-react";
import { PIN_LENGTH } from "../../utils/localProfiles.ts";
import PinPad from "../pin/PinPad.tsx";
import NameKeyboard, { type KeyboardMode } from "./NameKeyboard.tsx";
import mascota3 from "../../assets/mascotas/mascota-3.png";
import mascota4 from "../../assets/mascotas/mascota-4.png";
import mascota5 from "../../assets/mascotas/mascota-5.png";
import "./QuickStartProfile.css";

export type ProfileFlowStage = "name" | "pin" | "image" | "summary" | "welcome";
/** Lo que se pinta: los pasos, mas el pad abierto desde la fila PIN del resumen. */
type ProfileFlowView = ProfileFlowStage | "pin-edit";

interface QuickStartProfileProps {
  stage: ProfileFlowStage;
  name: string;
  pin: string;
  avatarDataUrl?: string;
  error: string;
  busy?: boolean;
  onNameChange: (value: string) => void;
  onPinChange: (value: string) => void;
  onStageChange: (stage: ProfileFlowStage) => void;
  onChooseImage: (event: ChangeEvent<HTMLInputElement>) => void;
  /** Mascota por defecto: la convierte a data URL y la pone como avatar. */
  onPickPreset: (src: string) => void;
  onClearImage: () => void;
  /** B / Esc en el nombre: se sale del flujo y se vuelve al selector. */
  onExit: () => void;
  onSubmit: () => void;
}

/** Avatares por defecto de "Elige una imagen", antes de la imagen propia. */
const MASCOT_AVATARS = [mascota3, mascota4, mascota5];

/**
 * Que data URL salio de cada mascot. El perfil solo guarda data URLs
 * (localProfiles la normaliza) y la burbuja necesita saber cual de las cinco es
 * la elegida: se apunta aqui al convertirlas, no se comparan URLs sueltas.
 */
const presetDataUrls = new Map<string, string>();

export function registerPresetDataUrl(src: string, dataUrl: string) {
  presetDataUrls.set(src, dataUrl);
}

/** ¿El avatar actual es una mascota del catálogo o una imagen que subió la persona? */
function isPresetDataUrl(dataUrl: string | undefined) {
  if (!dataUrl) return false;
  for (const value of presetDataUrls.values()) {
    if (value === dataUrl) return true;
  }
  return false;
}

function initial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "A";
}

/**
 * Flujo de creación de perfil de Quick Start (referencias 1-5): nombre con
 * teclado en pantalla, avatar, resumen y bienvenida.
 *
 * Los textos están adaptados a la realidad de Aetherio: no hay perfiles
 * infantiles (la fila protegida es el PIN), no hay servidor de cuentas (el
 * perfil vive en este equipo) y el enlace de privacidad es un control real que
 * despliega qué se guarda, no un link muerto.
 */
export default function QuickStartProfile({
  stage,
  name,
  pin,
  avatarDataUrl,
  error,
  busy = false,
  onNameChange,
  onPinChange,
  onStageChange,
  onChooseImage,
  onPickPreset,
  onClearImage,
  onExit,
  onSubmit,
}: QuickStartProfileProps) {
  const [mode, setMode] = useState<KeyboardMode>("abc");
  const [disclosed, setDisclosed] = useState(false);
  // El PIN se pone con el mismo pad de acceso que usa la page de perfiles: la
  // fila del resumen abre el teclado en vez de ser un dato que no se puede tocar.
  const [pinOpen, setPinOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  // Lo que se ve. El pad tambien se abre desde la fila PIN del resumen, para
  // cambiarlo: es la misma pantalla con otra clave de vista.
  const viewKey: ProfileFlowView = stage === "summary" && pinOpen ? "pin-edit" : stage;
  const [displayed, setDisplayed] = useState<ProfileFlowView>(viewKey);
  const fadeRef = useRef<Animation | null>(null);

  function prefersReducedMotion() {
    return typeof window !== "undefined"
      && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  /**
   * Los pasos se encadenan con fades, no con un salto: primero se desvanece lo
   * que hay, luego se cambia el contenido y entra. Es el mismo intercambio que
   * hace Profiles entre las burbujas y el passcode.
   */
  useEffect(() => {
    if (displayed === viewKey) return;
    const node = stageRef.current;
    if (!node || prefersReducedMotion()) {
      setDisplayed(viewKey);
      return;
    }
    const out = node.animate(
      [{ opacity: 1 }, { opacity: 0 }],
      { duration: 130, easing: "cubic-bezier(0.4, 0, 1, 1)", fill: "forwards" },
    );
    fadeRef.current = out;
    out.onfinish = () => setDisplayed(viewKey);
    return () => {
      out.onfinish = null;
      out.cancel();
    };
  }, [displayed, viewKey]);

  useEffect(() => {
    const node = stageRef.current;
    if (!node || prefersReducedMotion()) return;
    fadeRef.current?.cancel();
    const enter = node.animate(
      [{ opacity: 0 }, { opacity: 1 }],
      { duration: 220, easing: "cubic-bezier(0.22, 1, 0.36, 1)" },
    );
    fadeRef.current = enter;
    return () => {
      enter.cancel();
    };
  }, [displayed]);

  /**
   * B / Esc entre pasos del sub-flujo: pin -> nombre, imagen -> pin,
   * resumen -> imagen, y en el nombre se sale del flujo al selector de perfiles
   * (de donde se vino).
   *
   * En la bienvenida no se retrocede: el perfil ya esta creado y volver al
   * resumen dejaria un OK que crearia otro. Con el pad abierto desde el resumen
   * no se toca nada aqui: ese Escape lo consume el propio PinPad (su onCancel)
   * para cerrar el pad y seguir en el resumen.
   */
  useEffect(() => {
    if (stage === "welcome" || pinOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape" && event.key !== "Esc" && event.code !== "Escape") return;
      const target = event.target as HTMLElement | null;
      if (target && /^(INPUT|TEXTAREA|SELECT)$/i.test(target.tagName)) return;
      event.preventDefault();
      event.stopPropagation();
      if (stage === "name") {
        onExit();
        return;
      }
      const previous: ProfileFlowStage =
        stage === "pin" ? "name" : stage === "image" ? "pin" : "image";
      onStageChange(previous);
    };
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [onExit, onStageChange, pinOpen, stage]);

  const nameReady = name.trim().length > 0;
  const nameClean = name.trim();

  if (displayed === "name") {
    return (
      <div ref={stageRef} className="qs-profile">
        <div className="qs-profile__stage">
          <div className="qs-name">
            <NameKeyboard
              value={name}
              onChange={onNameChange}
              mode={mode}
              onModeChange={setMode}
              maxLength={32}
            />

            <div className="qs-name__form">
              <h1 className="qs-name__title">Elige un nombre</h1>
              <p className="qs-name__field">{name}</p>
              <button
                type="button"
                className="qs-name__cta"
                disabled={!nameReady}
                onClick={() => onStageChange("pin")}
              >
                Continuar
                <ChevronRight size={20} strokeWidth={2.6} />
              </button>
              <p className="qs-name__hint">
                El nombre aparece en tu perfil y en los puntos donde tengas sesión iniciada.
              </p>
            </div>
          </div>
        </div>

        {error ? <p className="qs-profile__error">{error}</p> : null}
      </div>
    );
  }

  if (displayed === "image") {
    return (
      <div ref={stageRef} className="qs-profile">
        <div className="qs-profile__stage">
          <div className="qs-avatar">
            <h1 className="qs-avatar__title">Elige una imagen</h1>

            <div className="qs-avatar__grid">
              {MASCOT_AVATARS.map((src, index) => {
                // Sin avatar no hay ninguna elegida: comparar contra undefined
                // daria true en las cinco (Map.get y avatarDataUrl vacios).
                const selected = !!avatarDataUrl && presetDataUrls.get(src) === avatarDataUrl;
                return (
                  <button
                    key={src}
                    type="button"
                    className={selected ? "qs-avatar__bubble is-preset is-selected" : "qs-avatar__bubble is-preset"}
                    aria-label={selected ? `Quitar la mascota ${index + 1}` : `Usar la mascota ${index + 1}`}
                    aria-pressed={selected}
                    onClick={() => (selected ? onClearImage() : onPickPreset(src))}
                  >
                    <img src={src} alt="" />
                  </button>
                );
              })}

              {avatarDataUrl && !isPresetDataUrl(avatarDataUrl) ? (
                <button
                  type="button"
                  className="qs-avatar__bubble is-selected"
                  aria-label="Quitar la imagen"
                  onClick={onClearImage}
                >
                  <img src={avatarDataUrl} alt="" />
                </button>
              ) : (
                <button
                  type="button"
                  className="qs-avatar__bubble qs-avatar__bubble--own"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <ImagePlus size={30} strokeWidth={2} />
                  <span>Añadir imagen propia</span>
                </button>
              )}

              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="sr-only"
                onChange={onChooseImage}
              />
            </div>

            <button
              type="button"
              className="qs-name__cta"
              onClick={() => onStageChange("summary")}
            >
              Continuar
              <ChevronRight size={20} strokeWidth={2.6} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (displayed === "pin") {
    return (
      <div ref={stageRef} className="qs-profile">
        <div className="qs-profile__stage">
          <div className="qs-pin">
            {/* Paso propio del flujo, justo detras del nombre. Al completar los
                cuatro digitos se queda puesto y sigue a la imagen; "Saltar" es
                la via para no ponerlo. */}
            <PinPad
              value={pin}
              onChange={onPinChange}
              onComplete={() => onStageChange("image")}
              onCancel={() => onStageChange("name")}
              title="Elige un PIN"
              subtitle="Cuatro dígitos para proteger este perfil en este equipo."
            />

            <div className="qs-pin__actions">
              <button
                type="button"
                className="qs-pin__skip"
                onClick={() => onStageChange("image")}
              >
                Saltar
              </button>
            </div>
          </div>
        </div>

        {error ? <p className="qs-profile__error">{error}</p> : null}
      </div>
    );
  }

  if (displayed === "pin-edit") {
    return (
      <div ref={stageRef} className="qs-profile">
        <div className="qs-profile__stage">
          <div className="qs-pin">
            {/* Es la MISMA pantalla que la de acceso de Profiles —mismo pad, mismo
                gesto— con los textos de "ponerlo": al completar los cuatro
                dígitos queda puesto, sin botón Guardar, y B / Esc vuelve al
                resumen. */}
            <PinPad
              value={pin}
              onChange={onPinChange}
              onComplete={() => setPinOpen(false)}
              onCancel={() => setPinOpen(false)}
              title="Elige un PIN"
              subtitle="Cuatro dígitos para proteger este perfil en este equipo. Se guarda como hash: no se puede leer ni recuperar."
            />

            {pin ? (
              <div className="qs-pin__actions">
                <button
                  type="button"
                  className="qs-pin__action"
                  onClick={() => { onPinChange(""); setPinOpen(false); }}
                >
                  Quitar PIN
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    );
  }

  if (displayed === "summary") {
    return (
      <div ref={stageRef} className="qs-profile">
        <div className="qs-profile__stage">
          <div className="qs-summary">
            <div className="qs-summary__left">
              <h1 className="qs-summary__title">Completar perfil</h1>

              <div className="qs-summary__rows">
                <button type="button" className="qs-summary__row" onClick={() => onStageChange("name")}>
                  Nombre
                  <span className="qs-summary__value">
                    {nameClean}
                    <ChevronRight size={20} strokeWidth={2.6} />
                  </span>
                </button>

                <button type="button" className="qs-summary__row" onClick={() => onStageChange("image")}>
                  Imagen
                  <span className="qs-summary__value">
                    {avatarDataUrl ? "Elegida" : "Sin imagen"}
                    <ChevronRight size={20} strokeWidth={2.6} />
                  </span>
                </button>

                <button type="button" className="qs-summary__row" onClick={() => setPinOpen(true)}>
                  PIN
                  <span className="qs-summary__value">
                    {pin ? `${pin.length} de ${PIN_LENGTH} dígitos` : "Sin PIN"}
                    <ChevronRight size={20} strokeWidth={2.6} />
                  </span>
                </button>
              </div>

              <div className="qs-summary__actions">
                <button
                  type="button"
                  className="qs-summary__cta"
                  disabled={busy || !nameReady}
                  onClick={onSubmit}
                >
                  {busy ? "Creando perfil..." : "OK"}
                </button>
              </div>
            </div>

            <div className="qs-summary__card">
              <div className="qs-summary__avatars">
                <span className="qs-summary__avatar">{initial(nameClean)}</span>
                <span className="qs-summary__avatar qs-summary__avatar--badge">
                  <UserRound size={26} strokeWidth={2.2} />
                </span>
              </div>

              <h2 className="qs-summary__card-title">
                {nameClean} va a tener su propio perfil en Aetherio
              </h2>

              <p className="qs-summary__card-body">
                El perfil guarda sus propias claves destreaming, complementos, progreso y
                preferencias. Todo vive en este equipo: Aetherio no crea cuentas en la nube ni
                envía tu historial a ningún servidor.
              </p>

              <button
                type="button"
                className="qs-summary__disclose"
                aria-expanded={disclosed}
                onClick={() => setDisclosed(current => !current)}
              >
                <Info size={18} strokeWidth={2.4} />
                {disclosed ? "Ocultar qué se guarda" : "Qué se guarda en este equipo"}
              </button>

              {disclosed ? (
                <ul className="qs-summary__list">
                  <li>Claves de Addons y de MDBList solo en el almacén seguro del sistema.</li>
                  <li>Progreso de películas y series, y chaptermarks de Up Next.</li>
                  <li>Preferencias de reproducción, idioma y catálogos de la portada.</li>
                  <li>El PIN se guarda como hash SHA-256 con salt: nunca en claro.</li>
                </ul>
              ) : null}
            </div>
          </div>
        </div>

        {error ? <p className="qs-profile__error">{error}</p> : null}
      </div>
    );
  }

  return (
    <div ref={stageRef} className="qs-profile">
      <div className="qs-profile__stage">
        <div className="qs-welcome">
          <span className="qs-welcome__avatar">
            {avatarDataUrl ? <img src={avatarDataUrl} alt="" /> : initial(nameClean)}
          </span>

          <h1 className="qs-welcome__title">
            Te damos la bienvenida,<br />
            {nameClean}
          </h1>

          <p className="qs-welcome__body">
            Este perfil es tuyo y solo tuyo en este equipo. Aetherio usa lo que ves y lo que
            marcas como visto para ordenar la portada y seguir donde lo dejaste; el resto —
            audio, subtítulos, complementos y catálogo— es configuración local, sin cuentas ni
            telemetría.
          </p>

          <button type="button" className="qs-welcome__cta" onClick={onSubmit} disabled={busy}>
            {busy ? "Preparando..." : "Comenzar a ver"}
          </button>
        </div>
      </div>
    </div>
  );
}
