import { cleanParams, get, post } from '@/lib/api';
import type { IssueStatus, Page, QualityIssue, QualityRule, RecordSummary } from '@/types/api';

export interface IssueFilters {
  status?: string;
  severity?: string;
  category?: string;
  ruleId?: string;
  district?: string;
  importJobId?: string;
  landRecordId?: string;
  source?: string;
}

export const qualityApi = {
  list: (f: IssueFilters, page: number, size: number) => get<Page<QualityIssue>>('/quality/issues', cleanParams({ ...f, page, size })),
  get: (id: string) => get<{ issue: QualityIssue; relatedRecords: RecordSummary[] }>(`/quality/issues/${id}`),
  review: (id: string, status: IssueStatus, note?: string) => post<QualityIssue>(`/quality/issues/${id}/review`, { status, note }),
  rerun: (importJobId?: string) => post<{ recordsEvaluated: number; issuesCreated: number; rulesVersion: string }>('/quality/runs', importJobId ? { importJobId } : {}),
  statistical: () => post<{ recordsConsidered: number; groupsEvaluated: number; groupsTooSmall: number; issuesCreated: number; version: string }>('/quality/statistical-runs'),
  rules: () => get<QualityRule[]>('/quality/rules'),
};
