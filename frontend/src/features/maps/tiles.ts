// Tile provider (configurable). OpenStreetMap's standard tiles require attribution and light, interactive use only:
// https://operations.osmfoundation.org/policies/tiles/ - use an approved provider for heavy or production use.
export const TILE_URL = import.meta.env.VITE_TILE_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
export const TILE_ATTRIBUTION = import.meta.env.VITE_TILE_ATTRIBUTION
  ?? '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

export const CATEGORY_COLORS: Record<string, string> = {
  AGRICULTURAL: '#3f8f3a', RESIDENTIAL: '#2f62a3', COMMERCIAL: '#b45309', INDUSTRIAL: '#6b21a8', FOREST: '#166534',
  GOVERNMENT: '#475569', WATER_BODY: '#0284c7', PASTURE: '#65a30d', BARREN: '#a16207', INSTITUTIONAL: '#be123c', MIXED: '#0f766e',
};
export const UNCATEGORISED_COLOR = '#64748b';
