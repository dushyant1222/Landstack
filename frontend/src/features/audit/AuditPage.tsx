import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ScrollText } from 'lucide-react';
import { Alert, Badge, Card, EmptyState, Input, PageHeader, Pagination } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { useAuth } from '@/features/auth/AuthContext';
import { cleanParams, get } from '@/lib/api';
import { formatDateTime, humanize, ROLE_LABEL } from '@/lib/format';
import type { AuditEvent, Page } from '@/types/api';

export function AuditPage() {
  const { hasRole } = useAuth();
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const q = useQuery({
    queryKey: ['audit', page, action],
    queryFn: () => get<Page<AuditEvent>>('/audit-events', cleanParams({ page, size: 50, action: action.trim().toUpperCase() })),
  });
  return (
    <>
      <PageHeader title="Audit trail" description="Append-only record of important actions. Summaries never contain passwords, tokens or owner names." />
      {!hasRole('ADMINISTRATOR') && <Alert tone="info" className="mb-4">As a data operator you see your own actions. Administrators see all events.</Alert>}
      <Card>
        <div className="border-b border-slate-100 p-4">
          <Input aria-label="Filter by action" placeholder="Action, e.g. IMPORT_CONFIRMED" className="max-w-xs" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} />
        </div>
        <QueryState query={q} isEmpty={(d) => d.content.length === 0} empty={<EmptyState icon={<ScrollText className="h-5 w-5" />} title="No events" />}>
          {(d) => (
            <>
              <div className="overflow-x-auto">
                <table className="table-base">
                  <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Target</th><th>Summary</th></tr></thead>
                  <tbody>
                    {d.content.map((e) => (
                      <tr key={e.id}>
                        <td className="whitespace-nowrap text-xs">{formatDateTime(e.occurredAt)}</td>
                        <td className="text-xs">{e.actorName ?? (e.actorRole === 'SYSTEM' ? 'System' : '—')}{e.actorRole && e.actorRole !== 'SYSTEM' && <p className="text-slate-400">{ROLE_LABEL[e.actorRole] ?? e.actorRole}</p>}</td>
                        <td><Badge tone={e.action.includes('FAIL') || e.action.includes('REJECT') ? 'red' : 'slate'}>{humanize(e.action)}</Badge></td>
                        <td className="text-xs">{e.targetType ? humanize(e.targetType) : '—'}{e.targetId && <p className="mono text-slate-400">{e.targetId.slice(0, 8)}…</p>}</td>
                        <td className="max-w-md text-xs text-slate-600">{Object.entries(e.summary).map(([k, v]) => `${k}: ${String(v)}`).join(' · ') || '—'}</td>
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
    </>
  );
}
