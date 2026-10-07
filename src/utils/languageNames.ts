/**
 * Nombres de idioma legibles para el reproductor.
 *
 * mpv y los add-ons deliversen el idioma como codigo ISO ("en", "ja", "spa") y a
 * veces el nombre en ingles ("English"). El menu de audio de la referencia pide
 * texto legible, no codigos: "Inglés", "Japonés", "Español". Aqui vive el unico
 * diccionario, para que audio y subtitulos digan exactamente lo mismo.
 */

const LANGUAGE_NAMES: Record<string, string> = {
  // ISO 639-1
  es: "Español",
  en: "Inglés",
  fr: "Francés",
  de: "Alemán",
  it: "Italiano",
  pt: "Portugués",
  nl: "Neerlandés",
  ru: "Ruso",
  ja: "Japonés",
  ko: "Coreano",
  zh: "Chino",
  ar: "Árabe",
  he: "Hebreo",
  hi: "Hindi",
  th: "Tailandés",
  vi: "Vietnamita",
  id: "Indonesio",
  ms: "Malayo",
  tr: "Turco",
  pl: "Polaco",
  cs: "Checo",
  sk: "Eslovaco",
  hu: "Húngaro",
  ro: "Rumano",
  bg: "Búlgaro",
  el: "Griego",
  uk: "Ucraniano",
  sr: "Serbio",
  hr: "Croata",
  sl: "Esloveno",
  bs: "Bosnio",
  mk: "Macedonio",
  sq: "Albanés",
  ca: "Catalán",
  gl: "Gallego",
  eu: "Euskera",
  is: "Islandés",
  ga: "Irlandés",
  cy: "Galés",
  et: "Estonio",
  lv: "Letón",
  lt: "Lituano",
  da: "Danés",
  sv: "Sueco",
  no: "Noruego",
  fi: "Finés",
  nb: "Noruego",
  nn: "Noruego",
  af: "Afrikáans",
  sw: "Suajili",
  tl: "Tagalo",
  ta: "Tamil",
  te: "Telugu",
  ml: "Malayalam",
  kn: "Canará",
  mr: "Maratí",
  gu: "Gujarati",
  pa: "Panyabí",
  ur: "Urdu",
  bn: "Bengalí",
  my: "Birmano",
  km: "Jemer",
  lo: "Laosiano",
  ka: "Georgiano",
  hy: "Armenio",
  az: "Azerbaiyano",
  kk: "Kazajo",
  uz: "Uzbeko",
  be: "Bielorruso",
  af_ZA: "Afrikáans",
  // ISO 639-2/3 que llegan tal cual desde los streams
  spa: "Español",
  eng: "Inglés",
  fra: "Francés",
  fre: "Francés",
  deu: "Alemán",
  ger: "Alemán",
  ita: "Italiano",
  por: "Portugués",
  nld: "Neerlandés",
  dut: "Neerlandés",
  rus: "Ruso",
  jpn: "Japonés",
  kor: "Coreano",
  zho: "Chino",
  chi: "Chino",
  ara: "Árabe",
  heb: "Hebreo",
  hin: "Hindi",
  tha: "Tailandés",
  vie: "Vietnamita",
  ind: "Indonesio",
  msa: "Malayo",
  may: "Malayo",
  tur: "Turco",
  pol: "Polaco",
  ces: "Checo",
  cze: "Checo",
  slk: "Eslovaco",
  slo: "Esloveno",
  hun: "Húngaro",
  ron: "Rumano",
  rum: "Rumano",
  bul: "Búlgaro",
  ell: "Griego",
  gre: "Griego",
  ukr: "Ucraniano",
  srp: "Serbio",
  hrv: "Croata",
  slv: "Esloveno",
  alb: "Albanés",
  cat: "Catalán",
  glg: "Gallego",
  eus: "Euskera",
  baq: "Euskera",
  isl: "Islandés",
  ice: "Islandés",
  gle: "Irlandés",
  cym: "Galés",
  wel: "Galés",
  est: "Estonio",
  lav: "Letón",
  lit: "Lituano",
  dan: "Danés",
  swe: "Sueco",
  nor: "Noruego",
  fin: "Finés",
  afr: "Afrikáans",
  swh: "Suajili",
  tgl: "Tagalo",
  tam: "Tamil",
  tel: "Telugu",
  mal: "Malayalam",
  kan: "Canará",
  mar: "Maratí",
  guj: "Gujarati",
  pan: "Panyabí",
  urd: "Urdu",
  ben: "Bengalí",
  mya: "Birmano",
  bur: "Birmano",
  khm: "Jemer",
  lao: "Laosiano",
  kat: "Georgiano",
  geo: "Georgiano",
  hye: "Armenio",
  arm: "Armenio",
  aze: "Azerbaiyano",
  kaz: "Kazajo",
  uzb: "Uzbeko",
  bel: "Bielorruso",
  // Marcas de sin idioma definido
  und: "Original",
  mul: "Multilingüe",
  zxx: "Sin idioma",
};

/** Etiquetas que el stream usa para el idioma original o la pista principal. */
const ORIGINAL_PATTERNS = [
  /^(original|originale|original sound|ori|audiodescription original)$/i,
  /^(original|originale)\s+(audio|sonido|pista)$/i,
];

const CODE_ONLY = /^[a-z]{2,3}(?:[-_][a-z]{2,4})?$/i;
const CODE_PREFIXED = /^([a-z]{2,3})(?:[-_][a-z]{2,4})?\b[\s:-]*(.*)$/i;

/** Los nombres que mpv y los add-ons escriben en el rotulo, en ingles. */
const ENGLISH_NAME_TO_CODE: Record<string, string> = {
  english: "en",
  spanish: "es",
  castilian: "es",
  french: "fr",
  german: "de",
  italian: "it",
  portuguese: "pt",
  brazilian: "pt",
  dutch: "nl",
  russian: "ru",
  japanese: "ja",
  korean: "ko",
  chinese: "zh",
  mandarin: "zh",
  cantonese: "zh",
  arabic: "ar",
  hebrew: "he",
  hindi: "hi",
  thai: "th",
  vietnamese: "vi",
  indonesian: "id",
  malay: "ms",
  turkish: "tr",
  polish: "pl",
  czech: "cs",
  slovak: "sk",
  hungarian: "hu",
  romanian: "ro",
  bulgarian: "bg",
  greek: "el",
  ukrainian: "uk",
  serbian: "sr",
  croatian: "hr",
  slovenian: "sl",
  bosnian: "bs",
  macedonian: "mk",
  albanian: "sq",
  catalan: "ca",
  galician: "gl",
  gallego: "gl",
  basque: "eu",
  euskera: "eu",
  icelandic: "is",
  irish: "ga",
  welsh: "cy",
  estonian: "et",
  latvian: "lv",
  lithuanian: "lt",
  danish: "da",
  swedish: "sv",
  norwegian: "no",
  finnish: "fi",
  afrikaans: "af",
  swahili: "sw",
  tagalog: "tl",
  tamil: "ta",
  telugu: "te",
  malayalam: "ml",
  kannada: "kn",
  marathi: "mr",
  gujarati: "gu",
  punjabi: "pa",
  urdu: "ur",
  bengali: "bn",
  burmese: "my",
  myanmar: "my",
  khmer: "km",
  lao: "lo",
  georgian: "ka",
  armenian: "hy",
  azerbaijani: "az",
  kazakh: "kk",
  uzbek: "uz",
  belarusian: "be",
  commentary: "en",
  descriptive: "en",
  stereo: "en",
  main: "en",
};

/**
 * Nombre legible de un rotulo de pista.
 *
 * Acepta codigos ("en", "spa", "pt-BR"), nombres en ingles ("English") y
 * "Original"/"AD". Si el rotulo es un codigo suelto, se traduce; si ya es un
 * nombre ("English", "Ingles"), se respeta tal cual.
 */
export function languageDisplayName(raw: string): string {
  const text = String(raw ?? "").trim();
  if (!text) return "";
  if (ORIGINAL_PATTERNS.some(pattern => pattern.test(text))) return "Original";

  // Nombre en ingles tal cual ("English", "Original Sound", "Commentary").
  const englishName = ENGLISH_NAME_TO_CODE[text.toLowerCase()];
  if (englishName) return LANGUAGE_NAMES[englishName];

  if (CODE_ONLY.test(text)) {
    const direct = LANGUAGE_NAMES[text.toLowerCase()];
    if (direct) return direct;
    const base = text.toLowerCase().split(/[-_]/)[0];
    return LANGUAGE_NAMES[base] ?? text;
  }
  // "en AD" / "spa: Comentario": el codigo de delante se traduce y el resto se
  // respeta ("Inglés AD").
  const prefixed = text.match(CODE_PREFIXED);
  if (prefixed) {
    const head = prefixed[1].toLowerCase();
    const language = LANGUAGE_NAMES[head] ?? (ENGLISH_NAME_TO_CODE[head] ? LANGUAGE_NAMES[ENGLISH_NAME_TO_CODE[head]] : undefined);
    if (language) {
      const rest = prefixed[2].trim();
      if (!rest) return language;
      // "en English": el nombre ya venia en el rotulo, no hay que duplicarlo.
      const restName = LANGUAGE_NAMES[rest.toLowerCase().split(/[\s(]/)[0]]
        ?? (ENGLISH_NAME_TO_CODE[rest.toLowerCase()] ? LANGUAGE_NAMES[ENGLISH_NAME_TO_CODE[rest.toLowerCase()]] : undefined);
      if (restName) return language;
      return `${language} · ${rest}`;
    }
  }
  return text;
}

/** ¿El texto es un codigo de idioma sin nombre? */
export function isLanguageCode(raw: string): boolean {
  const text = String(raw ?? "").trim();
  return CODE_ONLY.test(text) && Boolean(LANGUAGE_NAMES[text.toLowerCase().split(/[-_]/)[0]]);
}