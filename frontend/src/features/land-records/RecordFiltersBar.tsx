import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import { Button, Input, Select } from '@/components/ui';
import { useDebounce } from '@/hooks/useDebounce';
import { humanize } from '@/lib/format';
import { recordsApi, type RecordFilters } from './api';

/** Shared filter bar for the record table and the map, so both show the same record set. */
export function RecordFiltersBar({ filters, onChange, showStatus = true }: {
  filters: RecordFilters; onChange: (f: RecordFilters) => void; showStatus?: boolean;
}) {
  const facets = useQuery({ queryKey: ['records', 'facets'], queryFn: recordsApi.facets, staleTime: 60_000 });
  const [q, setQ] = useState(filters.q ?? '');
  const [village, setVillage] = useState(filters.village ?? '');
  const dq = useDebounce(q);
  const dv = useDebounce(village);
  useEffect(() => {
    if ((filters.q ?? '') !== dq || (filters.village ?? '') !== dv) onChange({ ...filters, q: dq || undefined, village: dv || undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dq, dv]);
  const set = (k: keyof RecordFilters, v: string) => onChange({ ...filters, [k]: v || undefined });
  const active = Object.values(filters).some(Boolean);
  return (
    <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 p-4">
      <div className="relative w-full sm:w-72">
        <Search className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" aria-hidden />
        <Input aria-label="Search records" placeholder="Parcel ID, ULPIN, khasra, locality…" className="pl-8" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <Input aria-label="Village or locality" placeholder="Village / locality" className="w-full sm:w-44" value={village} onChange={(e) => setVillage(e.target.value)} />
      <Select aria-label="District" className="w-full sm:w-40" value={filters.district ?? ''} onChange={(e) => set('district', e.target.value)}>
        <option value="">All districts</option>
        {facets.data?.districts.map((d) => <option key={d} value={d}>{d}</option>)}
      </Select>
      <Select aria-label="State" className="w-full sm:w-36" value={filters.state ?? ''} onChange={(e) => set('state', e.target.value)}>
        <option value="">All states</option>
        {facets.data?.states.map((d) => <option key={d} value={d}>{d}</option>)}
      </Select>
      <Select aria-label="Land type" className="w-full sm:w-40" value={filters.landType ?? ''} onChange={(e) => set('landType', e.target.value)}>
        <option value="">All land types</option>
        {facets.data?.landTypes.map((d) => <option key={d} value={d}>{humanize(d)}</option>)}
        <option value="UNCATEGORISED">Uncategorised</option>
      </Select>
      <Select aria-label="Spatial data" className="w-full sm:w-36" value={filters.mappable ?? ''} onChange={(e) => set('mappable', e.target.value)}>
        <option value="">Any location</option>
        <option value="true">Mappable</option>
        <option value="false">Not mappable</option>
      </Select>
      <Select aria-label="Quality" className="w-full sm:w-40" value={filters.hasOpenIssues ?? ''} onChange={(e) => set('hasOpenIssues', e.target.value)}>
        <option value="">Any quality status</option>
        <option value="true">Has open issues</option>
        <option value="false">No open issues</option>
      </Select>
      {showStatus && (
        <Select aria-label="Record status" className="w-full sm:w-36" value={filters.status ?? ''} onChange={(e) => set('status', e.target.value)}>
          <option value="">Confirmed</option>
          <option value="ARCHIVED">Archived</option>
          <option value="ALL">All statuses</option>
        </Select>
      )}
      {filters.importJobId && <span className="rounded bg-brand-50 px-2 py-1 text-xs text-brand-700">Import {filters.importJobId.slice(0, 8)}…</span>}
      {active && <Button variant="ghost" size="sm" icon={<X className="h-3.5 w-3.5" />} onClick={() => { setQ(''); setVillage(''); onChange({}); }}>Clear</Button>}
    </div>
  );
}
