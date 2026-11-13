export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso.length === 10 ? iso + 'T00:00:00Z' : iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: '2-digit', timeZone: 'UTC' });
}

export function formatNumber(n?: number | null, digits = 2): string {
  if (n === undefined || n === null || Number.isNaN(n)) return '—';
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(n);
}

export function formatBytes(b: number): string {
  if (b < 1024) return `${b} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
  return `${(b / 1024 / 1024).toFixed(1)} MB`;
}

export function formatArea(value?: number | null, unit?: string | null, sqm?: number | null): string {
  if (value === undefined || value === null) return '—';
  const base = `${formatNumber(value, 4)} ${unit ?? '(no unit)'}`;
  return sqm !== undefined && sqm !== null ? `${base} · ${formatNumber(sqm, 1)} m²` : base;
}

export function humanize(code?: string | null): string {
  if (!code) return '—';
  return code.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
}

export const ROLE_LABEL: Record<string, string> = {
  ADMINISTRATOR: 'Administrator',
  DATA_OPERATOR: 'Data operator',
  READ_ONLY_REVIEWER: 'Read-only reviewer',
};
