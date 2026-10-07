import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronLeft, ChevronRight, Loader2, Radio, Users } from "lucide-react";
import type { MediaStream } from "../../types/stream";
import { getSourceLogo } from "../../utils/sourceLogos";
import { getReportedSeeders } from "../../utils/torrentHealth";
import { buildSourceChips, normalizeSourceKey } from "../../utils/sourceFilterOrder";
import { sourceFilterPriority, useAddonStore } from "../../store/addonStore";
import { getStreamKind } from "./utils";
import PlayerSidePanel from "./PlayerSidePanel";

interface SourcePanelProps {
  visible: boolean;
  streams: MediaStream[];
  /** Resolviendo alternativas bajo demanda (no venia ninguna instantanea). */
  loading?: boolean;
  currentStreamId: string;
  onClose: () => void;
  onSelect: (stream: MediaStream) => void;
}

export default function SourcePanel({
  visible,
  streams,
  loading = false,
  currentStreamId,
  onClose,
  onSelect,
}: SourcePanelProps) {
  const providerRowRef = useRef<HTMLDivElement>(null);
  const [providerFilter, setProviderFilter] = useState<string | null>(null);
  const addonsById = useAddonStore(state => state.addons);
  const providers = useMemo(() => buildSourceChips({
    expectedSourceNames: [],
    streams,
    bundledById: new Map(addonsById.map(addon => [addon.id, Boolean(addon.bundled)])),
    isAnime: false,
    extractSourceName: stream => streamProviderName(stream),
    priorityOf: sourceFilterPriority,
  }).map(chip => ({ key: normalizeSourceKey(chip.name), name: chip.name, count: chip.count })),
  [streams, addonsById]);
  const filteredStreams = useMemo(
    () => providerFilter
      ? streams.filter(stream => normalizeProviderName(streamProviderName(stream)) === providerFilter)
      : streams,
    [providerFilter, streams],
  );

  useEffect(() => {
    if (providerFilter && !providers.some(provider => provider.key === providerFilter)) {
      setProviderFilter(null);
    }
  }, [providerFilter, providers]);

  const scrollProviders = (direction: -1 | 1) => {
    providerRowRef.current?.scrollBy({ left: direction * 220, behavior: "smooth" });
  };

  return (
    <PlayerSidePanel
      visible={visible}
      title="Fuentes"
      subtitle={loading
        ? "Buscando fuentes…"
        : `${streams.length} ${streams.length === 1 ? "opción disponible" : "opciones disponibles"}`}
      icon={<Radio size={18} />}
      onClose={onClose}
    >
      <div className="flex h-full min-h-0 flex-col">
        {streams.length && !loading ? (
          <div className="mb-3 flex shrink-0 items-center gap-1.5">
            <button
              type="button"
              onClick={() => scrollProviders(-1)}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-white/[0.07] bg-white/[0.06] text-white/62 gsap-transition hover:bg-white/[0.12] hover:text-white"
              aria-label="Fuentes anteriores"
            >
              <ChevronLeft size={15} />
            </button>
            <div ref={providerRowRef} className="flex min-w-0 flex-1 gap-1.5 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
              <ProviderFilterButton
                active={!providerFilter}
                label="Todas"
                count={streams.length}
                onClick={() => setProviderFilter(null)}
              />
              {providers.map(provider => (
                <ProviderFilterButton
                  key={provider.key}
                  active={providerFilter === provider.key}
                  label={provider.name}
                  count={provider.count}
                  onClick={() => setProviderFilter(provider.key)}
                />
              ))}
            </div>
            <button
              type="button"
              onClick={() => scrollProviders(1)}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-white/[0.07] bg-white/[0.06] text-white/62 gsap-transition hover:bg-white/[0.12] hover:text-white"
              aria-label="Fuentes siguientes"
            >
              <ChevronRight size={15} />
            </button>
          </div>
        ) : null}

        <div className="min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
          {filteredStreams.map(stream => {
            const active = stream.id === currentStreamId;
            const provider = streamProviderName(stream);
            const providerLogo = getSourceLogo(provider);
            const rawTitle = stream.title || stream.name || provider;
            const title = sanitizeDisplayText(rawTitle) || provider;
            const quality = readBehaviorText(stream, "quality");
            const language = stream.languages?.join(" · ") || readBehaviorText(stream, "language") || readBehaviorText(stream, "audio");
            const kind = getStreamKind(stream);
            const seeders = kind === "p2p" ? getReportedSeeders(stream) : undefined;
            return (
              <button
                key={stream.id}
                type="button"
                onClick={() => onSelect(stream)}
                aria-current={active ? "true" : undefined}
                className={`group flex w-full items-center gap-3 rounded-2xl border px-3 py-3.5 text-left gsap-transition ${
                  active
                    ? "border-white/[0.16] bg-white/[0.14] shadow-[inset_0_1px_0_rgba(255,255,255,0.06)]"
                    : "border-white/[0.065] bg-white/[0.045] hover:border-white/[0.11] hover:bg-white/[0.09]"
                }`}
              >
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-black/20 text-sm font-semibold uppercase text-white/82">
                  {provider.slice(0, 2)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-white">{provider}</span>
                  <span className="mt-0.5 block truncate text-xs font-medium text-white/54">{title}</span>
                  <span className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] font-semibold text-white/46">
                    <span className="rounded-full bg-white/[0.07] px-2 py-0.5 uppercase">{kind}</span>
                    {quality ? <span>{quality}</span> : null}
                    {language ? <span>{language}</span> : null}
                    {kind === "p2p" ? (
                      <span className={`inline-flex items-center gap-1 ${
                        seeders === undefined ? "text-white/40" : seeders === 0 ? "text-red-300/80" : seeders < 10 ? "text-amber-200/80" : "text-emerald-200/80"
                      }`}>
                        <Users size={11} />
                        {seeders === undefined ? "Sin reporte" : `${seeders} seeders`}
                      </span>
                    ) : null}
                  </span>
                </span>
                {active ? (
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white text-black">
                    <Check size={15} strokeWidth={2.5} />
                  </span>
                ) : providerLogo ? (
                  <img src={providerLogo} alt={`Logo de ${provider}`} className="h-7 w-7 shrink-0 object-contain opacity-85" />
                ) : (
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-white/[0.07] text-[10px] font-bold uppercase text-white/48">
                    {provider.slice(0, 2)}
                  </span>
                )}
              </button>
            );
          })}
          {!filteredStreams.length ? (
            <div className="flex h-full min-h-48 flex-col items-center justify-center text-center text-white/45">
              {loading ? (
                <>
                  <Loader2 size={24} className="mb-3 animate-spin" />
                  <p className="text-sm font-medium">Buscando fuentes alternativas…</p>
                </>
              ) : (
                <>
                  <Radio size={24} className="mb-3" />
                  <p className="text-sm font-medium">
                    {streams.length ? "No hay fuentes de este proveedor." : "No hay otras fuentes para este contenido."}
                  </p>
                </>
              )}
            </div>
          ) : null}
        </div>
      </div>
    </PlayerSidePanel>
  );
}

function ProviderFilterButton({
  active,
  label,
  count,
  onClick,
}: {
  active: boolean;
  label: string;
  count: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs font-semibold gsap-transition ${
        active
          ? "border-white/[0.18] bg-white/[0.16] text-white"
          : "border-white/[0.07] bg-white/[0.055] text-white/58 hover:bg-white/[0.1] hover:text-white/88"
      }`}
    >
      <span>{label}</span>
      <span className="text-[10px] text-white/38">{count}</span>
    </button>
  );
}

function sanitizeDisplayText(value: string) {
  return value
    .replace(/\b(captura|capture|TO\s*AI)\b/gi, "")
    .replace(/\s{2,}/g, " ")
    .replace(/^[·\-–—\s]+|[·\-–—\s]+$/g, "")
    .trim();
}

function streamProviderName(stream: MediaStream) {
  const raw = stream.addonName || readBehaviorText(stream, "providerName") || stream.name || "Fuente";
  return sanitizeDisplayText(raw) || "Fuente";
}

function normalizeProviderName(value: string) {
  return normalizeSourceKey(value);
}

function readBehaviorText(stream: MediaStream, key: string) {
  const value = stream.behaviorHints?.[key];
  return typeof value === "string" ? value : "";
}
