import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { CheckCheck, Eye, EyeOff, ListChecks, ShieldCheck } from 'lucide-react';
import { Alert, Badge, Button, Card, CardBody, CardHeader, EmptyState, Pagination, Select, StatCard } from '@/components/ui';
import { ConfirmDialog } from '@/components/Dialog';
import { QueryState } from '@/components/QueryState';
import { RowStateBadge } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { toApiError } from '@/lib/api';
import { formatNumber, humanize } from '@/lib/format';
import type { ImportJob, RowState, StagedRow } from '@/types/api';
import { importKeys, importsApi } from '@/features/imports/api';
import { RowInspector } from './RowInspector';

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

const v = (r: StagedRow, f: string) => r.normalized?.[f]?.value ?? null;

export function ReviewPanel({ job, canEdit }: { job: ImportJob; canEdit: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [state, setState] = useState<RowState | ''>('');
  const [inspect, setInspect] = useState<StagedRow | null>(null);
  const [includeWarnings, setIncludeWarnings] = useState(true);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const keyRef = useRef<string>(uuid());

  const summary = useQuery({ queryKey: [...importKeys.one(job.id), 'summary', job.version], queryFn: () => importsApi.summary(job.id) });
  const rows = useQuery({
    queryKey: [...importKeys.one(job.id), 'rows', 'review', page, size, state, job.version],
    queryFn: () => importsApi.rows(job.id, { page, size, state: state ? [state] : undefined }),
  });

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: importKeys.one(job.id) });
  };

  const correct = useMutation({
    mutationFn: (p: { rowId: string; body: { values?: Record<string, string>; excluded?: boolean; reviewed?: boolean } }) =>
      importsApi.correctRow(job.id, p.rowId, p.body),
    onSuccess: () => { toast.success('Row updated and re-validated.'); setInspect(null); refresh(); },
    onError: (e) => toast.error(toApiError(e).message),
  });

  const confirm = useMutation({
    mutationFn: () => importsApi.confirm(job.id, keyRef.current, includeWarnings),
    onSuccess: (r) => {
      setConfirmOpen(false);
      toast.success(r.replayed ? 'This confirmation was already processed.' : `${r.importedRows} records saved.`);
      refresh();
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['records'] });
    },
    onError: (e) => {
      setConfirmOpen(false);
      const err = toApiError(e);
      toast.error(err.message);
      if (err.code === 'CONFIRMATION_FAILED') keyRef.current = uuid(); // rolled back: a new attempt needs a new key
      refresh();
    },
  });

  const s = summary.data;
  const toImport = s ? s.validRows + (includeWarnings ? s.warningRows : 0) : 0;

  return (
    <div className="space-y-5">
      {s && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
          <StatCard label="Total rows" value={s.totalRows} tone="slate" />
          <StatCard label="Valid" value={s.validRows} tone="teal" />
          <StatCard label="With warnings" value={s.warningRows} tone="amber" />
          <StatCard label="Blocked" value={s.blockedRows} tone="red" hint="Must be corrected or excluded" />
          <StatCard label="Excluded" value={s.excludedRows} tone="slate" />
        </div>
      )}
      {s && Object.keys(s.issueCounts).length > 0 && (
        <div className="flex flex-wrap gap-2">
          {Object.entries(s.issueCounts).map(([k, n]) => <Badge key={k} tone={k.startsWith('ID_') || k.startsWith('AREA_N') ? 'red' : 'amber'}>{humanize(k)}: {n}</Badge>)}
        </div>
      )}

      <Card>
        <CardHeader title="Preview: source vs normalized" icon={<ListChecks className="h-4 w-4" />}
          subtitle="These are unconfirmed source values. Open a row to compare every field, correct values or exclude it."
          actions={
            <Select aria-label="Filter rows by state" className="h-8 w-44 text-xs" value={state} onChange={(e) => { setState(e.target.value as RowState | ''); setPage(0); }}>
              <option value="">All rows</option>
              {(['VALID', 'WARNING', 'BLOCKED', 'EXCLUDED'] as RowState[]).map((x) => <option key={x} value={x}>{humanize(x)}</option>)}
            </Select>
          } />
        <QueryState query={rows} isEmpty={(d) => d.content.length === 0} empty={<EmptyState title="No rows in this view" />}>
          {(d) => (
            <>
              <div className="overflow-x-auto">
                <table className="table-base">
                  <thead><tr><th>Row</th><th>State</th><th>Parcel / Khasra</th><th>Locality</th><th>Area (normalized)</th><th>Coordinates</th><th>Issues</th><th /></tr></thead>
                  <tbody>
                    {d.content.map((r) => (
                      <tr key={r.id} className={r.excluded ? 'opacity-60' : undefined}>
                        <td className="tabular-nums">{r.rowNumber}{r.page ? <p className="text-[0.7rem] text-slate-400">p.{r.page}</p> : null}</td>
                        <td><RowStateBadge state={r.state} /></td>
                        <td className="mono">{v(r, 'parcel_id') ?? '—'}<br /><span className="text-slate-500">{v(r, 'survey_khasra_no') ?? '—'}</span></td>
                        <td className="text-xs">{[v(r, 'village_locality'), v(r, 'district'), v(r, 'state')].filter(Boolean).join(', ') || '—'}</td>
                        <td className="text-xs">{v(r, 'land_area_value') ? `${v(r, 'land_area_value')} ${v(r, 'land_area_unit') ?? '(no unit)'}` : '—'}</td>
                        <td className="mono text-xs">{v(r, 'latitude') && v(r, 'longitude') ? `${v(r, 'latitude')}, ${v(r, 'longitude')}` : <span className="text-slate-400">Not mappable</span>}</td>
                        <td className="max-w-xs text-xs text-slate-600">
                          {(r.validation ?? []).slice(0, 2).map((i, idx) => (
                            <p key={idx} className={i.blocking ? 'text-rose-700' : i.severity === 'INFO' ? 'text-slate-500' : 'text-amber-800'}>• {i.message}</p>
                          ))}
                          {(r.validation?.length ?? 0) > 2 && <p className="text-slate-400">+{(r.validation?.length ?? 0) - 2} more</p>}
                        </td>
                        <td className="whitespace-nowrap text-right">
                          <Button size="sm" variant="ghost" onClick={() => setInspect(r)}>Inspect</Button>
                          {canEdit && (
                            <Button size="sm" variant="ghost" icon={r.excluded ? <Eye className="h-3.5 w-3.5" /> : <EyeOff className="h-3.5 w-3.5" />}
                              onClick={() => correct.mutate({ rowId: r.id, body: { excluded: !r.excluded } })}>{r.excluded ? 'Include' : 'Exclude'}</Button>
                          )}
                          {canEdit && r.extractionMethod && (
                            <Button size="sm" variant="ghost" icon={<CheckCheck className="h-3.5 w-3.5" />}
                              onClick={() => correct.mutate({ rowId: r.id, body: { reviewed: true } })}>Mark reviewed</Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size} onPage={setPage} onSize={(n) => { setSize(n); setPage(0); }} />
            </>
          )}
        </QueryState>
      </Card>

      {canEdit && s && (
        <Card>
          <CardHeader title="Confirm import" icon={<ShieldCheck className="h-4 w-4" />}
            subtitle="Selected rows are saved in a single transaction. If anything fails, nothing is saved. Retrying is safe." />
          <CardBody className="space-y-3">
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input type="checkbox" className="mt-1" checked={includeWarnings} onChange={(e) => setIncludeWarnings(e.target.checked)} />
              <span>Include rows with warnings ({s.warningRows}). Their warnings become open data-quality issues for review.</span>
            </label>
            {s.blockedRows > 0 && <Alert tone="warning">{s.blockedRows} blocked row(s) will not be imported. Correct them first if they should be included.</Alert>}
            {job.statusCode === 'CONFIRMATION_ROLLED_BACK' && <Alert tone="error">{job.statusMessage}</Alert>}
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-600"><span className="font-semibold text-slate-900">{formatNumber(toImport, 0)}</span> row(s) will be saved as confirmed land records.</p>
              <Button variant="accent" disabled={toImport === 0} onClick={() => setConfirmOpen(true)}>Confirm import…</Button>
            </div>
          </CardBody>
        </Card>
      )}

      <ConfirmDialog open={confirmOpen} onClose={() => setConfirmOpen(false)} onConfirm={() => confirm.mutate()} loading={confirm.isPending}
        title="Confirm this import?" confirmLabel={`Save ${toImport} records`}>
        <p>{toImport} row(s) from <span className="font-medium">{job.originalFilename}</span> will be saved with lineage to this file and import job.</p>
        <p className="mt-2">Saved records are normalized data from the source file. They are not verified ownership or title records.</p>
      </ConfirmDialog>

      <RowInspector row={inspect} columns={job.sourceColumns ?? []} open={!!inspect} canEdit={canEdit}
        onClose={() => setInspect(null)} saving={correct.isPending}
        onSave={(values) => inspect && correct.mutate({ rowId: inspect.id, body: { values } })} />
      {job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_WARNINGS'
        ? <Button onClick={() => navigate(`/records?importJobId=${job.id}`)}>View records</Button> : null}
    </div>
  );
}
