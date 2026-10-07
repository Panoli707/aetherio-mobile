import { describe, expect, it } from "vitest";
import { isLanguageCode, languageDisplayName } from "./languageNames";

describe("languageDisplayName", () => {
  it("traduce los codigos que llegan del stream", () => {
    expect(languageDisplayName("en")).toBe("Inglés");
    expect(languageDisplayName("es")).toBe("Español");
    expect(languageDisplayName("ja")).toBe("Japonés");
    expect(languageDisplayName("spa")).toBe("Español");
    expect(languageDisplayName("pt-BR")).toBe("Portugués");
  });

  it("no duplica el nombre cuando el rotulo ya lo trae", () => {
    expect(languageDisplayName("en English")).toBe("Inglés");
    // La app esta en espanol: un nombre en ingles tambien se traduce.
    expect(languageDisplayName("English")).toBe("Inglés");
    expect(languageDisplayName("Japanese")).toBe("Japonés");
    expect(languageDisplayName("Inglés")).toBe("Inglés");
  });

  it("compone codigo + descripcion", () => {
    expect(languageDisplayName("en AD")).toBe("Inglés · AD");
    expect(languageDisplayName("spa: Comentario")).toBe("Español · Comentario");
  });

  it("la pista original tiene su propia etiqueta", () => {
    expect(languageDisplayName("Original")).toBe("Original");
    expect(languageDisplayName("und")).toBe("Original");
    expect(languageDisplayName("mul")).toBe("Multilingüe");
  });

  it("un idioma desconocido se respeta tal cual", () => {
    expect(languageDisplayName("Klingon")).toBe("Klingon");
    expect(languageDisplayName("xx")).toBe("xx");
    expect(languageDisplayName("")).toBe("");
  });
});

describe("isLanguageCode", () => {
  it("distingue codigo de nombre", () => {
    expect(isLanguageCode("en")).toBe(true);
    expect(isLanguageCode("Japanese")).toBe(false);
    expect(isLanguageCode("zzz")).toBe(false);
  });
});