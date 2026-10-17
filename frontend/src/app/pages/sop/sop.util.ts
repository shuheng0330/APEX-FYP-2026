/** The limits on a course's estimated duration, matching the server (FR-07-15). */
export const MIN_DURATION_MINUTES = 30;
export const MAX_DURATION_MINUTES = 24 * 60;

/** Minutes as "45 min", "1 h" or "1 h 30 min"; "-" when unknown. */
export function formatDuration(minutes: number | null | undefined): string {
  if (minutes == null || !Number.isFinite(minutes) || minutes <= 0) return '-';
  const hours = Math.floor(minutes / 60);
  const rest = Math.round(minutes % 60);
  if (!hours) return `${rest} min`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}
