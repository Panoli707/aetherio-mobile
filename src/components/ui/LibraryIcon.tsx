/**
 * Icono de Biblioteca para toda la app (rail de Big Picture, sidebar de PC,
 * pagina de Biblioteca y menus contextuales).
 *
 * Glifo de "biblioteca" —tres baldas apiladas con la de encima y la de debajo
 * atenuadas— regulatorsio. Viene de SVG Repo (`library-svgrepo-com.svg`) y se
 * reescribio para lo que hace el resto de iconos del repo:
 *
 * - `currentColor` en vez de `fill="#000000"`, para heredar el estado
 *   activo/inactivo del nav sin duplicar la paleta. El original venia con
 *   `#000000` fijo, que en un nav oscuro es invisible.
 * - Los grupos `opacity="0.4"` / `opacity="0.7"` se conservan: son la lectura de
 *   "balda de al fondo / balda del medio" y sin ellos el glifo se aplana en un
 *   solo bloque negro.
 * - Se quitan el `<?xml ?>` y el DTD del fichero de SVG Repo: no aportan nada
 *   en un componente de React y hacen ruido en el bundle.
 *
 * Mismo contrato que `SportsIcon`: `size` en px y `aria-hidden`, porque el
 * nombre siempre lo pone el texto que lo acompaña.
 */
export default function LibraryIcon({ size = 24, className }: { size?: number; className?: string }) {
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
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentColor"
        d="M8.67239 7.54199H15.3276C18.7024 7.54199 20.3898 7.54199 21.3377 8.52882C22.2855 9.51565 22.0625 11.0403 21.6165 14.0895L21.1935 16.9811C20.8437 19.3723 20.6689 20.5679 19.7717 21.2839C18.8745 21.9999 17.5512 21.9999 14.9046 21.9999H9.09536C6.44881 21.9999 5.12553 21.9999 4.22834 21.2839C3.33115 20.5679 3.15626 19.3723 2.80648 16.9811L2.38351 14.0895C1.93748 11.0403 1.71447 9.51565 2.66232 8.52882C3.61017 7.54199 5.29758 7.54199 8.67239 7.54199ZM8 18.0001C8 17.5859 8.3731 17.2501 8.83333 17.2501H15.1667C15.6269 17.2501 16 17.5859 16 18.0001C16 18.4143 15.6269 18.7501 15.1667 18.7501H8.83333C8.3731 18.7501 8 18.4143 8 18.0001Z"
      />
      <g opacity="0.4">
        <path
          fill="currentColor"
          d="M8.51005 2.00001H15.4901C15.7226 1.99995 15.9009 1.99991 16.0567 2.01515C17.1645 2.12352 18.0712 2.78958 18.4558 3.68678H5.54443C5.92895 2.78958 6.8357 2.12352 7.94352 2.01515C8.09933 1.99991 8.27757 1.99995 8.51005 2.00001Z"
        />
      </g>
      <g opacity="0.7">
        <path
          fill="currentColor"
          d="M6.31069 4.72266C4.92007 4.72266 3.7798 5.56241 3.39927 6.67645C3.39134 6.69967 3.38374 6.72302 3.37646 6.74647C3.77461 6.6259 4.18898 6.54713 4.60845 6.49336C5.68882 6.35485 7.05416 6.35492 8.64019 6.35501L8.75863 6.35501L15.5323 6.35501C17.1183 6.35492 18.4837 6.35485 19.564 6.49336C19.9835 6.54713 20.3979 6.6259 20.796 6.74647C20.7887 6.72302 20.7811 6.69967 20.7732 6.67645C20.3927 5.56241 19.2524 4.72266 17.8618 4.72266H6.31069Z"
        />
      </g>
    </svg>
  );
}