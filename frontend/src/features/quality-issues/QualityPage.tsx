import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { BookOpen, FlaskConical, RefreshCw, ShieldAlert, ShieldCheck } from 'lucide-react';
import { Alert, Badge, Button, Card, CardBody, CardHeader, EmptyState, Field, KeyValue, PageHeader, Pagination, Select, Textarea } from '@/components/ui';
import { Dialog } from '@/components/Dialog';
import { QueryState } from '@/components/QueryState';
import { IssueStatusBadge, SeverityBadge } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { CAN_OPERATE, useAuth } from '@/features/auth/AuthContext';
import { toApiError } from '@/lib/api';
import { formatArea, formatDateTime, humanize } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { IssueStatus } from '@/types/api';
import { qualityApi, type IssueFilters } from './api';

const KEYS: (keyof IssueFilters)[] = ['status', 'severity', 'category', 'ruleId', 'district', 'importJobId', 'landRecordId', 'source'];
const CATEGORIES = ['COMPLETENESS', 'AREA', 'AREA_UNIT', 'DATE', 'SPATIAL', 'DUPLICATE', 'CONSISTENCY', 'EXTRACTION', 'CLASSIFICATION', 'STATISTICAL'];

export function QualityPage() {
  const [params, setParams] = useSearchParams();
  const { hasRole } = useAuth();
  const canReview = hasRole(...CAN_OPERATE);
  const toast = useToast();
  const qc = useQueryClient();
  const filters: IssueFilters = {};
  KEYS.forEach((k) => { const v = params.get(k); if (v) filters[k] = v; });
  if (!params.has('status')) filters.status = 'OPEN';
  if (filters.status === 'ALL') delete filters.status;
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 25);
  const [selected, setSelected] = useState<string | null>(null);
  const [rulesOpen, setRulesOpen] = useState(false);

  const set = (patch: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v === undefined || v === '' ? next.delete(k) : next.set(k, String(v))));
    if (!('page' in patch)) next.set('page', '0');
    setParams(next, { replace: true });
  };

  const list = useQuery({ queryKey: ['quality', 'list', filters, page, size], queryFn: () => qualityApi.list(filters, page, size), placeholderData: (p) => p });
  const rules = useQuery({ queryKey: ['quality', 'rules'], queryFn: qualityApi.rules, enabled: rulesOpen });

  const rerun = useMutation({
    mutationFn: () => qualityApi.rerun(filters.importJobId),
    onSuccess: (r) => { toast.success(`Checked ${r.recordsEvaluated} records: ${r.issuesCreated} new issue(s) (${r.rulesVersion}).`); void qc.invalidateQueries({ queryKey: ['quality'] }); void qc.invalidateQueries({ queryKey: ['dashboard'] }); },
    onError: (e) => toast.error(toApiError(e).message),
  });
  const stat = useMutation({
    mutationFn: qualityApi.statistical,
    onSuccess: (r) => { toast.success(`Statistical check: ${r.groupsEvaluated} group(s) evaluated, ${r.groupsTooSmall} too small, ${r.issuesCreated} advisory flag(s).`); void qc.invalidateQueries({ queryKey: ['quality'] }); },
    onError: (e) => toast.error(toApiError(e).message),
  });

  return (
    <>
      <PageHeader title="Data quality" description="Rule-based checks flag potential issues for human review. A flag is not a legal determination and never changes, merges or deletes a record."
        actions={<>
          <Button variant="secondary" icon={<BookOpen className="h-4 w-4" />} onClick={() => setRulesOpen(true)}>Rule catalogue</Button>
          {canReview && <Button variant="secondary" icon={<FlaskConical className="h-4 w-4" />} loading={stat.isPending} onClick={() => stat.mutate()}>Run statistical check</Button>}
          {canReview && <Button icon={<RefreshCw className="h-4 w-4" />} loading={rerun.isPending} onClick={() => rerun.mutate()}>Re-run rules</Button>}
        </>} />
      <div className="grid gap-5 xl:grid-cols-5">
        <Card className="xl:col-span-3">
          <div className="flex flex-wrap gap-2 border-b border-slate-100 p-4">
            <Select aria-label="Status" className="w-36" value={params.get('status') ?? 'OPEN'} onChange={(e) => set({ status: e.target.value })}>
              {['OPEN', 'REVIEWED', 'RESOLVED', 'DISMISSED', 'ALL'].map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
            <Select aria-label="Severity" className="w-36" value={filters.severity ?? ''} onChange={(e) => set({ severity: e.target.value })}>
              <option value="">All severities</option>{['HIGH', 'WARNING', 'INFO'].map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
            <Select aria-label="Category" className="w-40" value={filters.category ?? ''} onChange={(e) => set({ category: e.target.value })}>
              <option value="">All categories</option>{CATEGORIES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
            <Select aria-label="Source" className="w-36" value={filters.source ?? ''} onChange={(e) => set({ source: e.target.value })}>
              <option value="">Rules + statistical</option><option value="RULE">Rules only</option><option value="STATISTICAL">Statistical (advisory)</option>
            </Select>
            {(filters.importJobId || filters.landRecordId || filters.district) && (
              <Button size="sm" variant="ghost" onClick={() => set({ importJobId: undefined, landRecordId: undefined, district: undefined })}>
                Clear scope {filters.importJobId ? '(import)' : filters.landRecordId ? '(record)' : '(district)'}
              </Button>
            )}
          </div>
          <QueryState query={list} isEmpty={(d) => d.content.length === 0}
            empty={<EmptyState icon={<ShieldCheck className="h-5 w-5" />} title="No issues in this view">Nothing matches these filters.</EmptyState>}>
            {(d) => (
              <>
                <ul className="divide-y divide-slate-100">
                  {d.content.map((i) => (
                    <li key={i.id}>
                      <button type="button" onClick={() => setSelected(i.id)}
                        className={cn('w-full px-4 py-3 text-left hover:bg-slate-50', selected === i.id && 'bg-brand-50/60')}>
                        <div className="flex flex-wrap items-center gap-1.5">
                          <SeverityBadge severity={i.severity} /><IssueStatusBadge status={i.status} />
                          {i.source === 'STATISTICAL' && <Badge tone="violet">Advisory</Badge>}
                          <span className="mono text-xs text-slate-500">{i.ruleId}</span>
                          <span className="ml-auto text-xs text-slate-400">{formatDateTime(i.createdAt)}</span>
                        </div>
                        <p className="mt-1 line-clamp-2 text-sm text-slate-700">{i.reason}</p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {i.record ? `${i.record.parcelId ?? i.record.surveyKhasraNo ?? 'Record'} · ${[i.record.villageLocality, i.record.district].filter(Boolean).join(', ')}` : 'Import-level issue'}
                          {i.field && ` · field: ${humanize(i.field)}`}
                        </p>
                      </button>
                    </li>
                  ))}
                </ul>
                <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size}
                  onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />
              </>
            )}
          </QueryState>
        </Card>
        <div className="xl:col-span-2">
          {selected ? <IssueDetail id={selected} canReview={canReview} /> : (
            <Card><EmptyState icon={<ShieldAlert className="h-5 w-5" />} title="Select an issue">Choose an issue to see the record, the reason it was raised and review actions.</EmptyState></Card>
          )}
        </div>
      </div>
      <Dialog open={rulesOpen} onClose={() => setRulesOpen(false)} wide title="Quality rule catalogue" description="Deterministic rules (rules-1.0). Thresholds are configurable and versioned.">
        <QueryState query={rules}>
          {(r) => (
            <table className="table-base">
              <thead><tr><th>Rule</th><th>Category</th><th>Severity</th><th>Stage</th><th>Description</th></tr></thead>
              <tbody>{r.map((x) => <tr key={x.ruleId}><td className="mono text-xs">{x.ruleId}</td><td className="text-xs">{humanize(x.category)}</td><td className="text-xs">{x.severity}</td><td className="text-xs">{x.stage}</td><td className="text-xs">{x.description}</td></tr>)}</tbody>
            </table>
          )}
        </QueryState>
      </Dialog>
    </>
  );
}

function IssueDetail({ id, canReview }: { id: string; canReview: boolean }) {
  const q = useQuery({ queryKey: ['quality', 'detail', id], queryFn: () => qualityApi.get(id) });
  const [action, setAction] = useState<IssueStatus | null>(null);
  const [note, setNote] = useState('');
  const toast = useToast();
  const qc = useQueryClient();
  const review = useMutation({
    mutationFn: () => qualityApi.review(id, action!, note.trim() || undefined),
    onSuccess: () => {
      toast.success('Review recorded.');
      setAction(null);
      setNote('');
      void qc.invalidateQueries({ queryKey: ['quality'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['records'] });
    },
    onError: (e) => toast.error(toApiError(e).message),
  });
  const needsNote = action === 'RESOLVED' || action === 'DISMISSED';
  return (
    <QueryState query={q}>
      {({ issue: i, relatedRecords }) => (
        <Card className="sticky top-20">
          <CardHeader title={humanize(i.ruleId)} subtitle={`${humanize(i.category)} · ${i.ruleVersion}`}
            actions={<><SeverityBadge severity={i.severity} /><IssueStatusBadge status={i.status} /></>} />
          <CardBody className="space-y-4">
            {i.source === 'STATISTICAL' && <Alert tone="info" title="Advisory statistical flag">Generated by a statistical outlier check. Unusual values are not necessarily wrong.</Alert>}
            <p className="text-sm text-slate-700">{i.reason}</p>
            <KeyValue items={[
              { label: 'Affected field', value: i.field ? humanize(i.field) : '—' },
              { label: 'Observed value', value: i.observedValue ? <span className="mono">{i.observedValue}</span> : '—' },
              { label: 'Raised', value: formatDateTime(i.createdAt) },
              { label: 'Import', value: i.importJobId ? <Link className="text-brand-700 underline" to={`/imports/${i.importJobId}`}>{i.importJobId.slice(0, 8)}…</Link> : '—' },
            ]} />
            {i.record && (
              <div className="rounded-md border border-slate-200 p-3 text-sm">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Record</p>
                <Link to={`/records/${i.record.id}`} className="mono font-medium text-brand-700 hover:underline">{i.record.parcelId ?? i.record.surveyKhasraNo ?? i.record.id}</Link>
                <p className="text-xs text-slate-600">{[i.record.villageLocality, i.record.district, i.record.state].filter(Boolean).join(', ')}</p>
                <p className="text-xs text-slate-600">{formatArea(i.record.landAreaValue, i.record.landAreaUnitRaw, i.record.landAreaSqm)} · {i.record.ownerName ?? 'owner not supplied'}</p>
              </div>
            )}
            {relatedRecords.length > 0 && (
              <div>
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Possible duplicates (not merged)</p>
                <ul className="space-y-1 text-xs">
                  {relatedRecords.map((r) => (
                    <li key={r.id}><Link className="mono text-brand-700 underline" to={`/records/${r.id}`}>{r.parcelId ?? r.surveyKhasraNo ?? r.id.slice(0, 8)}</Link>
                      <span className="text-slate-500"> · {formatArea(r.landAreaValue, r.landAreaUnitRaw, r.landAreaSqm)} · {r.ownerName ?? '—'}</span></li>
                  ))}
                </ul>
              </div>
            )}
            {i.reviewedAt && (
              <Alert tone="info" title={`Reviewed by ${i.reviewerName ?? 'unknown'} · ${formatDateTime(i.reviewedAt)}`}>{i.reviewerNote ?? 'No note.'}</Alert>
            )}
            {canReview ? (
              <div className="space-y-3 border-t border-slate-100 pt-3">
                <div className="flex flex-wrap gap-2">
                  {(['REVIEWED', 'RESOLVED', 'DISMISSED', 'OPEN'] as IssueStatus[]).filter((s) => s !== i.status).map((s) => (
                    <Button key={s} size="sm" variant={action === s ? 'primary' : 'secondary'} onClick={() => setAction(s)}>
                      {s === 'OPEN' ? 'Reopen' : s === 'REVIEWED' ? 'Mark reviewed' : s === 'RESOLVED' ? 'Resolve' : 'Dismiss'}
                    </Button>
                  ))}
                </div>
                {action && (
                  <>
                    <Field label={needsNote ? 'Note (required)' : 'Note (optional)'} htmlFor="note" hint="Explain the decision for other reviewers. Avoid personal data.">
                      <Textarea id="note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
                    </Field>
                    <Button disabled={needsNote && !note.trim()} loading={review.isPending} onClick={() => review.mutate()}>Save review</Button>
                  </>
                )}
              </div>
            ) : <p className="border-t border-slate-100 pt-3 text-xs text-slate-500">Read-only reviewers can inspect issues but not change their status.</p>}
          </CardBody>
        </Card>
      )}
    </QueryState>
  );
}
