import { cleanParams, download, get, patch, post } from '@/lib/api';
import type { Facets, LandRecord, LandRecordDetail, MapResponse, Page } from '@/types/api';

export interface RecordFilters {
  q?: string;
  village?: string;
  tehsil?: string;
  district?: string;
  state?: string;
  landType?: string;
  importJobId?: string;
  sourceFileId?: string;
  status?: string;
  mappable?: string;
  hasOpenIssues?: string;
}

export const FILTER_KEYS: (keyof RecordFilters)[] = ['q', 'village', 'tehsil', 'district', 'state', 'landType', 'importJobId', 'sourceFileId', 'status', 'mappable', 'hasOpenIssues'];

export function filtersFromParams(p: URLSearchParams): RecordFilters {
  const f: RecordFilters = {};
  FILTER_KEYS.forEach((k) => { const v = p.get(k); if (v) f[k] = v; });
  return f;
}

export const recordsApi = {
  search: (f: RecordFilters, page: number, size: number, sort?: string) =>
    get<Page<LandRecord>>('/land-records', cleanParams({ ...f, page, size, sort })),
  get: (id: string) => get<LandRecordDetail>(`/land-records/${id}`),
  facets: () => get<Facets>('/land-records/facets'),
  update: (id: string, body: { version: number; reason: string; changes: Record<string, string> }) => patch<LandRecordDetail>(`/land-records/${id}`, body),
  archive: (id: string, reason: string) => post<LandRecordDetail>(`/land-records/${id}/archive`, { reason }),
  restore: (id: string) => post<LandRecordDetail>(`/land-records/${id}/restore`),
  map: (f: RecordFilters, includeGeometry: boolean) => get<MapResponse>('/map/points', cleanParams({ ...f, includeGeometry })),
  exportCsv: (f: RecordFilters) => download('/exports/land-records.csv', cleanParams(f), 'landstack-records.csv'),
};
