import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { FileUp, History } from 'lucide-react';
import { Button, Card, EmptyState, Input, PageHeader, Pagination, Select } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { ImportStatusBadge } from '@/components/StatusBadges';
import { useAuth, CAN_OPERATE } from '@/features/auth/AuthContext';
import { useDebounce } from '@/hooks/useDebounce';
import { formatBytes, formatDateTime, humanize } from '@/lib/format';
import type { ImportStatus } from '@/types/api';
import { ACTIVE_STATUSES, importKeys, importsApi } from './api';
import { useEffect, useState } from 'react';

const STATUSES: ImportStatus[] = ['PARSING', 'AWAITING_SHEET_SELECTION', 'AWAITING_MAPPING', 'PREVIEW_READY', 'COMPLETED',
  'COMPLETED_WITH_WARNINGS', 'FAILED', 'CANCELLED', 'EXPIRED', 'REMOVED'];

export function ImportHistoryPage() {
  const { hasRole } = useAuth();
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 25);
  const status = params.get('status') ?? '';
  const [q, setQ] = useState(params.get('q') ?? '');
  const dq = useDebounce(q);

  const set = (patch: Record<string, string | number | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v === null || v === '' ? next.delete(k) : next.set(k, String(v))));
    setParams(next, { replace: true });
  };
  useEffect(() => {
    if (dq !== (params.get('q') ?? '')) set({ q: dq, page: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dq]);

  const query = useQuery({
    queryKey: importKeys.list({ page, size, status, q: dq }),
    queryFn: () => importsApi.list({ page, size, status: status || undefined, q: dq || undefined }),
    refetchInterval: (qr) => (qr.state.data?.content.some((j) => ACTIVE_STATUSES.includes(j.status)) ? 3000 : false),
  });

  return (
    <>
      <PageHeader title="Import history" description="Every upload is a traceable import job with its source file, status and result counts."
        actions={hasRole(...CAN_OPERATE) && <Link to="/imports/new"><Button icon={<FileUp className="h-4 w-4" />}>New import</Button></Link>} />
      <Card>
        <div className="flex flex-wrap gap-3 border-b border-slate-100 p-4">
          <Input aria-label="Search by file name" placeholder="Search file name…" className="max-w-xs" value={q} onChange={(e) => setQ(e.target.value)} />
          <Select aria-label="Filter by status" className="max-w-[14rem]" value={status} onChange={(e) => set({ status: e.target.value, page: 0 })}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
          </Select>
        </div>
        <QueryState query={query} isEmpty={(d) => d.content.length === 0}
          empty={<EmptyState icon={<History className="h-5 w-5" />} title="No imports found">{status || dq ? 'No imports match these filters.' : 'Uploaded datasets will appear here.'}</EmptyState>}>
          {(d) => (
            <>
              <div className="overflow-x-auto">
                <table className="table-base">
                  <thead><tr><th>File</th><th>Status</th><th>Rows</th><th>Imported</th><th>Uploaded by</th><th>Created</th></tr></thead>
                  <tbody>
                    {d.content.map((j) => (
                      <tr key={j.id}>
                        <td>
                          <Link to={`/imports/${j.id}`} className="font-medium text-brand-700 hover:underline">{j.originalFilename}</Link>
                          <p className="text-xs text-slate-500">{j.fileType} · {formatBytes(j.sizeBytes)}</p>
                        </td>
                        <td><ImportStatusBadge status={j.status} />{j.statusMessage && <p className="mt-1 max-w-xs text-xs text-slate-500">{j.statusMessage}</p>}</td>
                        <td className="tabular-nums">{j.totalRows ?? '—'}</td>
                        <td className="tabular-nums">{j.importedRows ?? '—'}</td>
                        <td>{j.createdByName ?? '—'}</td>
                        <td className="whitespace-nowrap">{formatDateTime(j.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size}
                onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s, page: 0 })} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  );
}
