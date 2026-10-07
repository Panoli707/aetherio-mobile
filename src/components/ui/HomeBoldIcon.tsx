/**
 * Icono de Inicio para el rail de Big Picture.
 *
 * Casa "bold" de SVG Repo (`home-bold.svg`): silueta maciza con el hueco de la
 * puerta resuelto con `fill-rule="evenodd"` (no es un recorte, el subtractor va
 * en la misma `d`). Cubre 2..22 en ambos ejes, asi que opticalmente llena el
 * viewBox igual que la lupa.
 *
 * Macizo a proposito: convive con `LibraryIcon`, tambien macizo, y las dos son
 * las de mayor peso visual de la fila. Con un contorno de 2px las dos se
 *diluyen frente a los iconos de trazo y la columna deja de leerse como una
 * rejilla.
 *
 * `currentColor` para heredar el estado activo/inactivo del nav sin duplicar la
 * paleta.
 */
export default function HomeBoldIcon({ size = 24, className }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        fillRule="evenodd"
        clipRule="evenodd"
        d="M2.5192 7.82274C2 8.77128 2 9.91549 2 12.2039V13.725C2 17.6258 2 19.5763 3.17157 20.7881C4.34315 22 6.22876 22 10 22H14C17.7712 22 19.6569 22 20.8284 20.7881C22 19.5763 22 17.6258 22 13.725V12.2039C22 9.91549 22 8.77128 21.4808 7.82274C20.9616 6.87421 20.0131 6.28551 18.116 5.10812L16.116 3.86687C14.1106 2.62229 13.1079 2 12 2C10.8921 2 9.88939 2.62229 7.88403 3.86687L5.88403 5.10813C3.98695 6.28551 3.0384 6.87421 2.5192 7.82274ZM9 17.25C8.58579 17.25 8.25 17.5858 8.25 18C8.25 18.4142 8.58579 18.75 9 18.75H15C15.4142 18.75 15.75 18.4142 15.75 18C15.75 17.5858 15.4142 17.25 15 17.25H9Z"
      />
    </svg>
  );
}