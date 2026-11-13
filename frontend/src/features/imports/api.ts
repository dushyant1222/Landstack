import { cleanParams, del, get, post, put, patch, upload } from '@/lib/api';
import type {
  ConfirmResult, DiagnosticsAttempt, ImportJob, ImportStatus, MappingRequest, MappingSuggestions, MappingTemplate, Page,
  PreviewSummary, RowState, StagedRow, TimelineEntry,
} from '@/types/api';

export const ACTIVE_STATUSES: ImportStatus[] = ['RECEIVED', 'VALIDATING', 'PARSING', 'CONFIRMING'];
export const ALLOWED_EXTENSIONS = ['csv', 'xlsx', 'pdf'];
export const MAX_UPLOAD_MB = 20;

export const importKeys = {
  all: ['imports'] as const,
  list: (p: object) => ['imports', 'list', p] as const,
  one: (id: string) => ['imports', id] as const,
};

export const importsApi = {
  upload: (file: File, onProgress?: (pct: number) => void) => upload<ImportJob>('/imports', file, onProgress),
  list: (p: { status?: string; q?: string; page: number; size: number; sort?: string }) => get<Page<ImportJob>>('/imports', cleanParams(p)),
  get: (id: string) => get<ImportJob>(`/imports/${id}`),
  timeline: (id: string) => get<TimelineEntry[]>(`/imports/${id}/timeline`),
  diagnostics: (id: string) => get<DiagnosticsAttempt[]>(`/imports/${id}/diagnostics`),
  pdfText: (id: string) => get<{ pageCount: number; pages: { page: number; charCount: number; likelyScanned: boolean; text: string; truncated: boolean }[] }>(`/imports/${id}/pdf-text`),
  rows: (id: string, p: { page: number; size: number; state?: RowState[] }) => get<Page<StagedRow>>(`/imports/${id}/rows`, cleanParams(p)),
  selectSheet: (id: string, body: { sheetName?: string; headerRow?: number }) => post<ImportJob>(`/imports/${id}/sheet-selection`, body),
  cancel: (id: string) => post<ImportJob>(`/imports/${id}/cancel`),
  retry: (id: string) => post<ImportJob>(`/imports/${id}/retry`),
  remove: (id: string) => del(`/imports/${id}`, { confirm: true }),
  suggestions: (id: string) => get<MappingSuggestions>(`/imports/${id}/mapping/suggestions`),
  saveMapping: (id: string, body: MappingRequest) => put<PreviewSummary>(`/imports/${id}/mapping`, body),
  summary: (id: string) => get<PreviewSummary>(`/imports/${id}/preview-summary`),
  correctRow: (id: string, rowId: string, body: { values?: Record<string, string>; excluded?: boolean; reviewed?: boolean }) =>
    patch<PreviewSummary>(`/imports/${id}/rows/${rowId}`, body),
  confirm: (id: string, key: string, includeWarningRows: boolean) =>
    post<ConfirmResult>(`/imports/${id}/confirm`, { confirmationKey: key, includeWarningRows }, { 'Idempotency-Key': key }),
  templates: () => get<MappingTemplate[]>('/mapping-templates'),
  saveTemplate: (body: { name: string; description?: string; fromImportJobId: string }) => post<MappingTemplate>('/mapping-templates', body),
};
