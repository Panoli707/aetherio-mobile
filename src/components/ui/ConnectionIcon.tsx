/**
 * Icono de Extensiones (complementos) para el rail de Big Picture.
 *
 * "connection" de SVG Repo: dos eslabones unidos en diagonal. Sustituye al
 * `Plug` de lucide porque el enchufe de unix es el mismo para cualquier cosa
 * electrica y aqui no dice nada —los complementos son justo lo que se conecta
 * a la app. Dos eslabones ademas comparten el peso macizo de Inicio y
 * Biblioteca, y la fila deja de mezclar un contorno de 2px con tres macizos.
 *
 * `currentColor` para heredar el estado activo/inactivo del nav sin duplicar la
 * paleta.
 */
export default function ConnectionIcon({ size = 24, className }: { size?: number; className?: string }) {
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
        d="M21.4 7.5c.8.8.8 2.1 0 2.8l-2.8 2.8l-7.8-7.8l2.8-2.8c.8-.8 2.1-.8 2.8 0l1.8 1.8l3-3l1.4 1.4l-3 3zm-5.8 5.8l-1.4-1.4l-2.8 2.8l-2.1-2.1l2.8-2.8l-1.4-1.4l-2.8 2.8l-1.5-1.4l-2.8 2.8c-.8.8-.8 2.1 0 2.8l1.8 1.8l-4 4l1.4 1.4l4-4l1.8 1.8c.8.8 2.1.8 2.8 0l2.8-2.8l-1.4-1.4z"
      />
    </svg>
  );
}