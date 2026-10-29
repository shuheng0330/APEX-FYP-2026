/** Colours for a course category: a cover gradient plus a tag ink and tint. */
export interface CategoryTheme {
  from: string;
  to: string;
  ink: string;
  tint: string;
}

const THEMES: CategoryTheme[] = [
  { from: '#0f766e', to: '#2dd4bf', ink: '#0f766e', tint: '#e6fffb' },
  { from: '#1d4ed8', to: '#60a5fa', ink: '#1d4ed8', tint: '#eff6ff' },
  { from: '#6d28d9', to: '#a78bfa', ink: '#6d28d9', tint: '#f5f3ff' },
  { from: '#b45309', to: '#fbbf24', ink: '#b45309', tint: '#fffbeb' },
  { from: '#be123c', to: '#fb7185', ink: '#be123c', tint: '#fff1f2' },
  { from: '#0369a1', to: '#38bdf8', ink: '#0369a1', tint: '#f0f9ff' },
  { from: '#4d7c0f', to: '#84cc16', ink: '#4d7c0f', tint: '#f7fee7' },
];

/**
 * A category's colours: from its own colour when it has one (FR-19-01), otherwise the same category always gets the
 * same colours.
 */
export function categoryTheme(category: string, color?: string): CategoryTheme {
  if (color && /^#[0-9a-f]{6}$/i.test(color)) {
    return { from: color, to: color + '99', ink: color, tint: color + '14' };
  }
  let hash = 0;
  for (const ch of category) {
    hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  }
  return THEMES[Math.abs(hash) % THEMES.length];
}

/** Whole days from today to an ISO date (yyyy-MM-dd); negative once it has passed. */
export function daysUntil(isoDate: string): number {
  const [y, m, d] = isoDate.split('-').map(Number);
  const now = new Date();
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())) / 86_400_000);
}
