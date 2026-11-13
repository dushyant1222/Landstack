import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowDownUp, Download, Map as MapIcon, Table2 } from 'lucide-react';
import { useState } from 'react';
import { Badge, Button, Card, EmptyState, PageHeader, Pagination } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { MappableBadge } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { CAN_OPERATE, useAuth } from '@/features/auth/AuthContext';
import { toApiError } from '@/lib/api';
import { formatArea, humanize } from '@/lib/format';
import { FILTER_KEYS, filtersFromParams, recordsApi, type RecordFilters } from './api';
import { RecordFiltersBar } from './RecordFiltersBar';

const SORTS = [
  { id: 'createdAt,desc', label: 'Newest first' }, { id: 'parcelId,asc', label: 'Parcel ID' }, { id: 'surveyKhasraNo,asc', label: 'Khasra no.' },
  { id: 'district,asc', label: 'District' }, { id: 'village,asc', label: 'Village' }, { id: 'area,desc', label: 'Largest area (m²)' },
];

export function LandRecordsPage() {
  const [params, setParams] = useSearchParams();
  const { hasRole } = useAuth();
  const toast = useToast();
  const [exporting, setExporting] = useState(false);
  const filters = filtersFromParams(params);
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 25);
  const sort = params.get('sort') ?? 'createdAt,desc';

  const update = (f: RecordFilters, extra: Record<string, string | number> = { page: 0 }) => {
    const next = new URLSearchParams();
    FILTER_KEYS.forEach((k) => { if (f[k]) next.set(k, f[k]!); });
    const merged = { page, size, sort, ...extra };
    Object.entries(merged).forEach(([k, v]) => next.set(k, String(v)));
    setParams(next, { replace: true });
  };

  const query = useQuery({
    queryKey: ['records', 'search', filters, page, size, sort],
    queryFn: () => recordsApi.search(filters, page, size, sort),
    placeholderData: (prev) => prev,
  });

  const exportCsv = async () => {
    setExporting(true);
    try {
      await recordsApi.exportCsv(filters);
      toast.success('Export downloaded.');
    } catch (e) {
      toast.error(toApiError(e).message);
    } finally {
      setExporting(false);
    }
  };

  const mapLink = `/map?${new URLSearchParams(Object.entries(filters).filter(([, v]) => v) as [string, string][]).toString()}`;

  return (
    <>
      <PageHeader title="Land records" description="Confirmed, normalized records with lineage to their source file. Search runs on the server."
        actions={<>
          <Link to={mapLink}><Button variant="secondary" icon={<MapIcon className="h-4 w-4" />}>Show on map</Button></Link>
          {hasRole(...CAN_OPERATE) && <Button variant="secondary" icon={<Download className="h-4 w-4" />} loading={exporting} onClick={exportCsv}>Export CSV</Button>}
        </>} />
      <Card>
        <RecordFiltersBar filters={filters} onChange={(f) => update(f)} />
        <div className="flex items-center justify-end gap-2 px-4 pt-3 text-xs text-slate-500">
          <ArrowDownUp className="h-3.5 w-3.5" />
          <select aria-label="Sort records" className="h-7 rounded border border-slate-300 bg-white px-1 text-xs" value={sort} onChange={(e) => update(filters, { page: 0, sort: e.target.value })}>
            {SORTS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </select>
        </div>
        <QueryState query={query} isEmpty={(d) => d.content.length === 0}
          empty={<EmptyState icon={<Table2 className="h-5 w-5" />} title="No records found">
            {Object.keys(filters).length ? 'No confirmed records match these filters.' : 'Confirmed imports will appear here.'}</EmptyState>}>
          {(d) => (
            <>
              <div className="overflow-x-auto">
                <table className="table-base">
                  <thead><tr><th>Parcel ID / ULPIN</th><th>Survey / Khasra</th><th>Owner</th><th>Locality</th><th>Area</th><th>Land type</th><th>Location</th><th>Quality</th></tr></thead>
                  <tbody>
                    {d.content.map((r) => (
                      <tr key={r.id}>
                        <td><Link to={`/records/${r.id}`} className="mono font-medium text-brand-700 hover:underline">{r.parcelId ?? '—'}</Link>
                          {r.recordStatus === 'ARCHIVED' && <Badge className="ml-1">Archived</Badge>}</td>
                        <td className="mono">{r.surveyKhasraNo ?? '—'}</td>
                        <td>{r.ownerName ?? <span className="text-slate-400">—</span>}</td>
                        <td className="text-xs">{r.villageLocality ?? '—'}<p className="text-slate-500">{[r.tehsil, r.district, r.state].filter(Boolean).join(', ')}</p></td>
                        <td className="whitespace-nowrap text-xs">{formatArea(r.landAreaValue, r.landAreaUnitRaw, r.landAreaSqm)}</td>
                        <td className="text-xs">{r.landTypeRaw ?? '—'}{r.landTypeCategory && <p className="text-slate-500">{humanize(r.landTypeCategory)}</p>}</td>
                        <td><MappableBadge mappable={r.mappable} /></td>
                        <td>{r.openIssues > 0 ? <Badge tone="amber">{r.openIssues} open</Badge> : <span className="text-xs text-slate-400">None open</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size}
                onPage={(p) => update(filters, { page: p })} onSize={(s) => update(filters, { page: 0, size: s })} />
            </>
          )}
        </QueryState>
      </Card>
    </>
  );
}
