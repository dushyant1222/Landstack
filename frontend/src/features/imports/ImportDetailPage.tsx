import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Ban, Download, FileSearch, Loader2, RotateCcw, Trash2 } from 'lucide-react';
import { Alert, Badge, Button, Card, CardBody, CardHeader, EmptyState, Field, Input, KeyValue, PageHeader, Pagination, Select, Spinner, Tabs } from '@/components/ui';
import { ConfirmDialog } from '@/components/Dialog';
import { QueryState } from '@/components/QueryState';
import { ImportStatusBadge } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { CAN_OPERATE, useAuth } from '@/features/auth/AuthContext';
import { MappingEditor } from '@/features/field-mapping/MappingEditor';
import { ReviewPanel } from '@/features/field-mapping/ReviewPanel';
import { download, toApiError } from '@/lib/api';
import { formatBytes, formatDateTime, humanize } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { DiagnosticMessage, ImportJob, ImportStatus } from '@/types/api';
import { ACTIVE_STATUSES, importKeys, importsApi } from './api';

const STEPS = ['Upload', 'Parse', 'Map', 'Review', 'Confirmed'];

function stepOf(s: ImportStatus): number {
  if (s === 'RECEIVED' || s === 'VALIDATING') return 0;
  if (s === 'PARSING' || s === 'AWAITING_SHEET_SELECTION') return 1;
  if (s === 'AWAITING_MAPPING') return 2;
  if (s === 'PREVIEW_READY' || s === 'CONFIRMING') return 3;
  if (s === 'COMPLETED' || s === 'COMPLETED_WITH_WARNINGS') return 4;
  return -1;
}

function Stepper({ status }: { status: ImportStatus }) {
  const current = stepOf(status);
  return (
    <ol className="flex flex-wrap items-center gap-2 text-xs" aria-label="Import progress">
      {STEPS.map((s, i) => (
        <li key={s} className="flex items-center gap-2">
          <span className={cn('flex h-6 items-center rounded-full px-2.5 font-medium',
            current < 0 ? 'bg-slate-100 text-slate-400' : i < current ? 'bg-accent-100 text-accent-800' : i === current ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-500')}
            aria-current={i === current ? 'step' : undefined}>{i + 1}. {s}</span>
          {i < STEPS.length - 1 && <span className="h-px w-4 bg-slate-300" aria-hidden />}
        </li>
      ))}
    </ol>
  );
}

export function ImportDetailPage() {
  const { id = '' } = useParams();
  const { hasRole } = useAuth();
  const canOperate = hasRole(...CAN_OPERATE);
  const isAdmin = hasRole('ADMINISTRATOR');
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const [tab, setTab] = useState<'source' | 'mapping' | 'review' | 'pdf'>('source');
  const [dialog, setDialog] = useState<'cancel' | 'remove' | null>(null);

  const job = useQuery({
    queryKey: importKeys.one(id),
    queryFn: () => importsApi.get(id),
    refetchInterval: (q) => (q.state.data && ACTIVE_STATUSES.includes(q.state.data.status) ? 1500 : false),
  });

  const status = job.data?.status;
  useEffect(() => {
    if (status === 'PREVIEW_READY') setTab('review');
    else if (status === 'AWAITING_MAPPING') setTab((t) => (t === 'review' ? 'mapping' : t));
  }, [status]);

  const action = useMutation({
    mutationFn: async (kind: 'cancel' | 'retry' | 'remove') => {
      if (kind === 'cancel') return importsApi.cancel(id);
      if (kind === 'retry') return importsApi.retry(id);
      return importsApi.remove(id);
    },
    onSuccess: (_d, kind) => {
      setDialog(null);
      toast.success(kind === 'remove' ? 'Import and its records removed. Audit history is kept.' : kind === 'retry' ? 'Processing restarted.' : 'Import cancelled.');
      void qc.invalidateQueries({ queryKey: importKeys.all });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => { setDialog(null); toast.error(toApiError(e).message); },
  });

  return (
    <QueryState query={job}>
      {(j) => (
        <>
          <PageHeader title={j.originalFilename}
            description={<span className="flex flex-wrap items-center gap-2"><ImportStatusBadge status={j.status} /><Badge>{j.fileType}</Badge>
              <span className="text-xs">Job <span className="mono">{j.id}</span></span></span>}
            actions={<>
              {canOperate && <Button variant="secondary" size="sm" icon={<Download className="h-3.5 w-3.5" />}
                onClick={() => download(`/source-files/${j.sourceFileId}/content`, {}, j.originalFilename).catch((e) => toast.error(toApiError(e).message))}>Source file</Button>}
              {canOperate && j.status === 'FAILED' && j.retryable && <Button size="sm" icon={<RotateCcw className="h-3.5 w-3.5" />} loading={action.isPending} onClick={() => action.mutate('retry')}>Retry</Button>}
              {canOperate && !['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED', 'EXPIRED', 'CANCELLED', 'REMOVED', 'CONFIRMING'].includes(j.status) &&
                <Button variant="secondary" size="sm" icon={<Ban className="h-3.5 w-3.5" />} onClick={() => setDialog('cancel')}>Cancel import</Button>}
              {isAdmin && ['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED', 'EXPIRED', 'CANCELLED'].includes(j.status) &&
                <Button variant="danger" size="sm" icon={<Trash2 className="h-3.5 w-3.5" />} onClick={() => setDialog('remove')}>Remove import</Button>}
            </>} />

          <Card className="mb-5">
            <CardBody className="space-y-4">
              <Stepper status={j.status} />
              <KeyValue className="lg:grid-cols-4" items={[
                { label: 'Uploaded by', value: j.createdByName },
                { label: 'Uploaded', value: formatDateTime(j.createdAt) },
                { label: 'Size', value: formatBytes(j.sizeBytes) },
                { label: 'SHA-256', value: <span className="mono" title={j.sha256}>{j.sha256.slice(0, 16)}…</span> },
                { label: 'Rows parsed', value: j.totalRows ?? '—' },
                { label: 'Rows imported', value: j.importedRows ?? '—' },
                { label: 'Worksheet', value: j.selectedSheet ?? (j.fileType === 'XLSX' ? 'not selected' : 'n/a') },
                { label: 'Confirmed', value: formatDateTime(j.confirmedAt) },
              ]} />
              {j.duplicateOfJobId && <Alert tone="warning">An identical file was uploaded earlier: <Link className="underline" to={`/imports/${j.duplicateOfJobId}`}>view earlier import</Link>. Records are never merged automatically.</Alert>}
            </CardBody>
          </Card>

          <StageContent job={j} canOperate={canOperate} tab={tab} setTab={setTab} />

          <div className="mt-5"><Timeline id={j.id} version={j.version} /></div>

          <ConfirmDialog open={dialog === 'cancel'} onClose={() => setDialog(null)} onConfirm={() => action.mutate('cancel')} loading={action.isPending}
            title="Cancel this import?" confirmLabel="Cancel import" danger>
            Staged preview rows will be discarded. The uploaded file and audit history are kept. No land records are affected.
          </ConfirmDialog>
          <ConfirmDialog open={dialog === 'remove'} onClose={() => setDialog(null)} danger loading={action.isPending}
            onConfirm={() => action.mutate('remove', { onSuccess: () => navigate('/imports') })}
            title="Remove this import and its records?" confirmLabel="Remove permanently">
            This deletes {j.importedRows ?? 0} confirmed record(s) created by this import, their lineage and quality issues, and the stored source file
            if no other import uses it. Intended for demonstration data. The audit trail is retained. This cannot be undone.
          </ConfirmDialog>
        </>
      )}
    </QueryState>
  );
}

function StageContent({ job, canOperate, tab, setTab }: { job: ImportJob; canOperate: boolean; tab: 'source' | 'mapping' | 'review' | 'pdf'; setTab: (t: 'source' | 'mapping' | 'review' | 'pdf') => void }) {
  if (['RECEIVED', 'VALIDATING', 'PARSING', 'CONFIRMING'].includes(job.status)) {
    return (
      <Card><CardBody className="flex items-center gap-3 py-10 text-sm text-slate-600">
        <Loader2 className="h-5 w-5 animate-spin text-brand-600" />
        <div><p className="font-medium text-slate-800">{humanize(job.status)}…</p><p className="text-xs">Status refreshes automatically from the server.</p></div>
      </CardBody></Card>
    );
  }
  if (job.status === 'FAILED') {
    return (
      <div className="space-y-4">
        <Alert tone="error" title={`Import failed · ${humanize(job.statusCode)}`}>
          {job.statusMessage} {job.retryable ? 'This problem may be temporary; you can retry.' : 'Fix the file and upload it again.'} No records were saved.
        </Alert>
        {canOperate && <Diagnostics id={job.id} version={job.version} />}
      </div>
    );
  }
  if (job.status === 'CANCELLED' || job.status === 'EXPIRED' || job.status === 'REMOVED') {
    return <Alert tone="info" title={humanize(job.status)}>{job.status === 'EXPIRED' ? 'This import was not confirmed in time; its preview was discarded by the retention policy.'
      : job.status === 'REMOVED' ? 'An administrator removed this import and its records. Audit history is retained.' : 'This import was cancelled before confirmation. No records were saved.'}</Alert>;
  }
  if (job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_WARNINGS') return <ResultPanel job={job} />;
  if (!canOperate) {
    return <Alert tone="info" title="Preview in progress">This import is being prepared by a data operator. Records appear in the catalogue once it is confirmed.</Alert>;
  }
  if (job.status === 'AWAITING_SHEET_SELECTION') return <SheetSelector job={job} />;

  const tabs: { id: 'source' | 'mapping' | 'review' | 'pdf'; label: string; disabled?: boolean }[] = [
    { id: 'source', label: 'Source preview' },
    { id: 'mapping', label: 'Field mapping' },
    { id: 'review', label: 'Review & confirm', disabled: job.status !== 'PREVIEW_READY' },
  ];
  if (job.fileType === 'PDF') tabs.push({ id: 'pdf', label: 'Extracted PDF text' });
  return (
    <div className="space-y-4">
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {job.fileType === 'PDF' && <Alert tone="warning" title="Machine-extracted values">Values from PDFs are extracted automatically and may be incomplete or misread. Review every row; rows stay flagged until you mark them reviewed or correct them.</Alert>}
      {tab === 'source' && <SourcePreview job={job} />}
      {tab === 'mapping' && <MappingEditor job={job} onSaved={() => setTab('review')} />}
      {tab === 'review' && job.status === 'PREVIEW_READY' && <ReviewPanel job={job} canEdit={canOperate} />}
      {tab === 'pdf' && <PdfText id={job.id} />}
    </div>
  );
}

function SheetSelector({ job }: { job: ImportJob }) {
  const [sheet, setSheet] = useState(job.sheetNames?.[0] ?? '');
  const [headerRow, setHeaderRow] = useState('');
  const qc = useQueryClient();
  const toast = useToast();
  const m = useMutation({
    mutationFn: () => importsApi.selectSheet(job.id, { sheetName: sheet, headerRow: headerRow ? Number(headerRow) : undefined }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: importKeys.one(job.id) }),
    onError: (e) => toast.error(toApiError(e).message),
  });
  return (
    <Card>
      <CardHeader title="Select a worksheet" icon={<FileSearch className="h-4 w-4" />} subtitle={`This workbook has ${job.sheetNames?.length ?? 0} worksheets. Choose the one that contains the land records.`} />
      <CardBody className="space-y-4">
        <fieldset className="space-y-2">
          <legend className="sr-only">Worksheet</legend>
          {job.sheetNames?.map((n) => (
            <label key={n} className={cn('flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm', sheet === n ? 'border-brand-500 bg-brand-50' : 'border-slate-200')}>
              <input type="radio" name="sheet" value={n} checked={sheet === n} onChange={() => setSheet(n)} /> {n}
            </label>
          ))}
        </fieldset>
        <Field label="Header row (optional)" htmlFor="hr" hint="Leave empty to detect automatically (first non-blank row).">
          <Input id="hr" type="number" min={1} className="max-w-[8rem]" value={headerRow} onChange={(e) => setHeaderRow(e.target.value)} />
        </Field>
        <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!sheet}>Read worksheet</Button>
      </CardBody>
    </Card>
  );
}

function SourcePreview({ job }: { job: ImportJob }) {
  const [page, setPage] = useState(0);
  const [headerRow, setHeaderRow] = useState('');
  const qc = useQueryClient();
  const toast = useToast();
  const rows = useQuery({ queryKey: [...importKeys.one(job.id), 'rows', 'source', page, job.version], queryFn: () => importsApi.rows(job.id, { page, size: 25 }) });
  const reparse = useMutation({
    mutationFn: (body: { sheetName?: string; headerRow?: number }) => importsApi.selectSheet(job.id, body),
    onSuccess: () => { toast.info('Re-reading the file…'); void qc.invalidateQueries({ queryKey: importKeys.one(job.id) }); },
    onError: (e) => toast.error(toApiError(e).message),
  });
  const cols = job.sourceColumns ?? [];
  return (
    <div className="space-y-4">
      <Diagnostics id={job.id} version={job.version} />
      <Card>
        <CardHeader title="Source rows (as read, unverified)" subtitle={`${job.totalRows ?? 0} rows · ${cols.length} columns. Column names are shown exactly as found.`}
          actions={job.fileType !== 'PDF' && <div className="flex items-center gap-2">
            {job.fileType === 'XLSX' && job.sheetNames && job.sheetNames.length > 1 && (
              <Select aria-label="Change worksheet" className="h-8 w-40 text-xs" value={job.selectedSheet ?? ''} onChange={(e) => reparse.mutate({ sheetName: e.target.value })}>
                {job.sheetNames.map((n) => <option key={n} value={n}>{n}</option>)}
              </Select>
            )}
            <Input aria-label="Header row number" type="number" min={1} placeholder="Header row" className="h-8 w-28 text-xs" value={headerRow} onChange={(e) => setHeaderRow(e.target.value)} />
            <Button size="sm" variant="secondary" disabled={!headerRow} loading={reparse.isPending}
              onClick={() => reparse.mutate({ sheetName: job.selectedSheet, headerRow: Number(headerRow) })}>Use as header</Button>
          </div>} />
        <QueryState query={rows} isEmpty={(d) => d.content.length === 0} empty={<EmptyState title="No rows staged" />}>
          {(d) => (
            <>
              <div className="max-h-[480px] overflow-auto">
                <table className="table-base">
                  <thead className="sticky top-0"><tr><th>#</th>{cols.map((c) => <th key={c}>{c}</th>)}</tr></thead>
                  <tbody>
                    {d.content.map((r) => (
                      <tr key={r.id}>
                        <td className="tabular-nums text-slate-400">{r.rowNumber}{r.page ? ` · p${r.page}` : ''}</td>
                        {cols.map((c) => <td key={c} className="mono max-w-[16rem] truncate" title={r.rawValues[c]}>{r.rawValues[c] || <span className="text-slate-300">∅</span>}</td>)}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
    </div>
  );
}

function Diagnostics({ id, version }: { id: string; version: number }) {
  const q = useQuery({ queryKey: [...importKeys.one(id), 'diagnostics', version], queryFn: () => importsApi.diagnostics(id) });
  const latest = q.data?.[0];
  const messages = (latest?.details.messages ?? []) as DiagnosticMessage[];
  if (!latest) return null;
  return (
    <Card>
      <CardHeader title="Processing diagnostics" subtitle={`${latest.stage} via ${latest.processor}${latest.processorVersion ? ` (${latest.processorVersion})` : ''} · ${humanize(latest.outcome)}`} />
      <CardBody className="space-y-1.5">
        {messages.length === 0 && <p className="text-sm text-slate-500">No messages.</p>}
        {messages.slice(0, 50).map((m, i) => (
          <p key={i} className={cn('text-sm', m.level === 'ERROR' ? 'text-rose-700' : m.level === 'WARNING' ? 'text-amber-800' : 'text-slate-600')}>
            <span className="mono mr-2 text-xs">{m.code}</span>{m.message}
            {(m.row || m.column) && <span className="ml-1 text-xs text-slate-400">[{m.row ? `row ${m.row}` : ''}{m.row && m.column ? ', ' : ''}{m.column ? `column “${m.column}”` : ''}]</span>}
          </p>
        ))}
        {messages.length > 50 && <p className="text-xs text-slate-400">{messages.length - 50} more message(s) not shown.</p>}
      </CardBody>
    </Card>
  );
}

function PdfText({ id }: { id: string }) {
  const q = useQuery({ queryKey: [...importKeys.one(id), 'pdf-text'], queryFn: () => importsApi.pdfText(id) });
  return (
    <Card>
      <CardHeader title="Extracted text by page" subtitle="Plain text from the PDF for side-by-side checking. Shown as text only; nothing in it is executed." />
      <QueryState query={q} isEmpty={(d) => d.pages.length === 0} empty={<EmptyState title="No extracted text" />}>
        {(d) => (
          <div className="divide-y divide-slate-100">
            {d.pages.map((p) => (
              <div key={p.page} className="p-4">
                <p className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">Page {p.page}
                  {p.likelyScanned && <Badge tone="amber">No text layer (likely scanned) — not processed</Badge>}{p.truncated && <Badge>Truncated</Badge>}</p>
                <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded bg-slate-50 p-3 font-mono text-xs text-slate-700">{p.text || '(empty)'}</pre>
              </div>
            ))}
          </div>
        )}
      </QueryState>
    </Card>
  );
}

function ResultPanel({ job }: { job: ImportJob }) {
  const rejected = job.resultSummary?.rejectedRows ?? [];
  return (
    <div className="space-y-4">
      <Alert tone={job.status === 'COMPLETED' ? 'success' : 'warning'} title={job.status === 'COMPLETED' ? 'Import completed' : 'Import completed with warnings'}
        action={<Link to={`/records?importJobId=${job.id}`}><Button size="sm">View {job.importedRows} records</Button></Link>}>
        {job.importedRows} record(s) saved · {job.rejectedRows ?? 0} row(s) not imported · {job.resultSummary?.qualityIssuesCreated ?? 0} quality issue(s) opened for review.
        Normalization {job.resultSummary?.normalizationVersion} · rules {job.resultSummary?.rulesVersion}.
      </Alert>
      {(job.resultSummary?.qualityIssuesCreated ?? 0) > 0 && <Link className="text-sm text-brand-700 underline" to={`/quality?importJobId=${job.id}`}>Review quality issues from this import →</Link>}
      {rejected.length > 0 && <RejectedRows rows={rejected} truncated={!!job.resultSummary?.rejectedRowsTruncated} />}
    </div>
  );
}

function RejectedRows({ rows, truncated }: { rows: { row: number; state: string; rules: string[] }[]; truncated: boolean }) {
  const [page, setPage] = useState(0);
  const size = 20;
  const slice = rows.slice(page * size, (page + 1) * size);
  return (
    <Card>
      <CardHeader title="Rows not imported" subtitle="Row numbers refer to the source file. Values are not repeated here." />
      <table className="table-base">
        <thead><tr><th>Source row</th><th>Reason</th><th>Rules</th></tr></thead>
        <tbody>{slice.map((r) => <tr key={r.row}><td>{r.row}</td><td>{humanize(r.state)}</td><td className="text-xs">{r.rules.map(humanize).join(', ') || '—'}</td></tr>)}</tbody>
      </table>
      <Pagination page={page} totalPages={Math.ceil(rows.length / size)} totalElements={rows.length} size={size} onPage={setPage} />
      {truncated && <p className="px-4 pb-3 text-xs text-slate-500">Only the first 500 rejected rows are listed.</p>}
    </Card>
  );
}

function Timeline({ id, version }: { id: string; version: number }) {
  const q = useQuery({ queryKey: [...importKeys.one(id), 'timeline', version], queryFn: () => importsApi.timeline(id) });
  return (
    <Card>
      <CardHeader title="Job timeline" subtitle="Audit events and processing attempts for this import." />
      <QueryState query={q} skeleton={<div className="p-4"><Spinner /></div>} isEmpty={(d) => d.length === 0} empty={<EmptyState title="No events yet" />}>
        {(d) => (
          <ol className="relative space-y-3 px-6 py-4">
            {d.map((e, i) => (
              <li key={i} className="relative border-l border-slate-200 pl-4">
                <span className={cn('absolute -left-[5px] top-1.5 h-2.5 w-2.5 rounded-full', e.kind === 'PROCESSING' ? 'bg-accent-500' : 'bg-brand-500')} />
                <p className="text-sm font-medium text-slate-800">{humanize(e.label)}</p>
                <p className="text-xs text-slate-500">{formatDateTime(e.at)}{e.actorName ? ` · ${e.actorName}` : e.kind === 'AUDIT' ? ' · system' : ''}{e.detail ? ` · ${e.detail}` : ''}</p>
              </li>
            ))}
          </ol>
        )}
      </QueryState>
    </Card>
  );
}
