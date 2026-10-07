import { describe, expect, it } from "vitest";
import { isValidPin, PIN_LENGTH, pinLengthError } from "./localProfiles.ts";

describe("regla del PIN", () => {
  it("son exactamente 4 dígitos", () => {
    expect(PIN_LENGTH).toBe(4);
    expect(isValidPin("1234")).toBe(true);
    expect(isValidPin("0000")).toBe(true);
  });

  it("rechaza PIN de otra longitud (dejarían el perfil inaccesible)", () => {
    // El teclado de acceso pide 4: guardar 1-3 dejaría el perfil sin forma de
    // abrirlo, así que la UI avisa y la capa de datos lo descarta.
    for (const value of ["", "1", "12", "123", "12345", "123456", "12345678"]) {
      expect(isValidPin(value)).toBe(false);
    }
    expect(isValidPin(undefined)).toBe(false);
    expect(isValidPin(null)).toBe(false);
  });

  it("rechaza lo que no son dígitos", () => {
    for (const value of ["12a4", "abcd", "12 4", "12.4", "-123"]) {
      expect(isValidPin(value)).toBe(false);
    }
  });

  it("el mensaje solo aparece cuando el PIN está mal", () => {
    expect(pinLengthError("")).toBeNull();
    expect(pinLengthError("1234")).toBeNull();
    expect(pinLengthError("123")).toBe("El PIN debe tener 4 dígitos.");
  });
});