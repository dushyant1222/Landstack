import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Database, FileStack, MapPin, ShieldAlert } from 'lucide-react';
import { Card, CardBody, CardHeader, EmptyState, Input, PageHeader, Select, StatCard } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { ImportStatusBadge } from '@/components/StatusBadges';
import { cleanParams, get } from '@/lib/api';
import { formatDateTime, humanize } from '@/lib/format';
import type { DashboardSummary } from '@/types/api';
import { recordsApi } from '@/features/land-records/api';

const SEVERITY_COLOR: Record<string, string> = { HIGH: '#e11d48', WARNING: '#d97706', INFO: '#64748b' };

export function DashboardPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [district, setDistrict] = useState('');
  const facets = useQuery({ queryKey: ['records', 'facets'], queryFn: recordsApi.facets, staleTime: 60_000 });
  const q = useQuery({
    queryKey: ['dashboard', from, to, district],
    queryFn: () => get<DashboardSummary>('/dashboard/summary', cleanParams({ from, to, district })),
    refetchInterval: 60_000,
  });
  return (
    <>
      <PageHeader title="Dashboard" description="All figures are computed by the server from records and issues stored in PostgreSQL."
        actions={<div className="flex flex-wrap items-center gap-2">
          <Select aria-label="District" className="h-8 w-40 text-xs" value={district} onChange={(e) => setDistrict(e.target.value)}>
            <option value="">All districts</option>{facets.data?.districts.map((d) => <option key={d} value={d}>{d}</option>)}
          </Select>
          <Input aria-label="From date" type="date" className="h-8 w-36 text-xs" value={from} onChange={(e) => setFrom(e.target.value)} />
          <Input aria-label="To date" type="date" className="h-8 w-36 text-xs" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>} />
      <QueryState query={q}>
        {(d) => (
          <div className="space-y-5">
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatCard label="Confirmed records" value={d.confirmedRecords.toLocaleString()} icon={<Database className="h-5 w-5" />} hint={`${d.archivedRecords} archived`} />
              <StatCard label="Import jobs" value={d.totalImports.toLocaleString()} icon={<FileStack className="h-5 w-5" />} tone="slate"
                hint={`${d.importsByStatus.filter((s) => s.key.startsWith('COMPLETED')).reduce((a, b) => a + b.count, 0)} completed · ${d.importsByStatus.find((s) => s.key === 'FAILED')?.count ?? 0} failed`} />
              <StatCard label="Mappable records" value={d.mappableRecords.toLocaleString()} icon={<MapPin className="h-5 w-5" />} tone="teal" hint={`${d.notMappableRecords.toLocaleString()} not mappable`} />
              <StatCard label="Open quality issues" value={d.openIssues.toLocaleString()} icon={<ShieldAlert className="h-5 w-5" />} tone={d.openIssues > 0 ? 'amber' : 'slate'}
                hint={d.openIssuesBySeverity.map((s) => `${s.count} ${s.key.toLowerCase()}`).join(' · ') || 'none'} />
            </div>

            <div className="grid gap-5 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader title="Records added per day" subtitle={`${d.from} to ${d.to} (UTC)${d.district ? ` · ${d.district}` : ''}`} />
                <CardBody>
                  {d.recordsAddedPerDay.every((x) => x.count === 0) ? <EmptyState title="No records added in this period" /> : (
                    <div className="h-56">
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={d.recordsAddedPerDay} margin={{ left: -16, right: 8 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                          <XAxis dataKey="day" tick={{ fontSize: 11 }} tickFormatter={(v: string) => v.slice(5)} />
                          <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                          <Tooltip />
                          <Area type="monotone" dataKey="count" name="Records" stroke="#244e86" fill="#adc6e6" />
                        </AreaChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Open issues by category" subtitle="Potential issues awaiting human review" />
                <CardBody>
                  {d.openIssuesByCategory.length === 0 ? <EmptyState title="No open issues" /> : (
                    <div className="h-56">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={d.openIssuesByCategory.map((c) => ({ name: humanize(c.key), count: c.count }))} layout="vertical" margin={{ left: 24 }}>
                          <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                          <YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 11 }} />
                          <Tooltip />
                          <Bar dataKey="count" name="Open issues" fill="#d97706" radius={[0, 3, 3, 0]} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardBody>
              </Card>
            </div>

            <div className="grid gap-5 lg:grid-cols-3">
              <Card>
                <CardHeader title="Records by land type" />
                <CardBody>
                  {d.recordsByLandType.length === 0 ? <EmptyState title="No records yet" /> : (
                    <div className="h-56">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={d.recordsByLandType.map((c) => ({ name: humanize(c.key), count: c.count }))} layout="vertical" margin={{ left: 24 }}>
                          <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                          <YAxis type="category" dataKey="name" width={100} tick={{ fontSize: 11 }} />
                          <Tooltip />
                          <Bar dataKey="count" name="Records" fill="#22917c" radius={[0, 3, 3, 0]} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Open issues by severity" />
                <CardBody>
                  {d.openIssuesBySeverity.length === 0 ? <EmptyState title="No open issues" /> : (
                    <div className="h-56">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={d.openIssuesBySeverity.map((c) => ({ name: humanize(c.key), key: c.key, count: c.count }))}>
                          <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                          <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                          <Tooltip />
                          <Bar dataKey="count" name="Open issues">{d.openIssuesBySeverity.map((c) => <Cell key={c.key} fill={SEVERITY_COLOR[c.key] ?? '#64748b'} />)}</Bar>
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Top districts" />
                <CardBody>
                  {d.recordsByDistrict.length === 0 ? <EmptyState title="No records yet" /> : (
                    <ul className="space-y-2 text-sm">
                      {d.recordsByDistrict.map((r) => {
                        const max = d.recordsByDistrict[0]?.count ?? 1;
                        return (
                          <li key={r.key}>
                            <div className="flex justify-between text-xs"><span>{r.key}</span><span className="tabular-nums text-slate-500">{r.count}</span></div>
                            <div className="mt-1 h-1.5 rounded bg-slate-100"><div className="h-full rounded bg-brand-500" style={{ width: `${(r.count / max) * 100}%` }} /></div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </CardBody>
              </Card>
            </div>

            <div className="grid gap-5 lg:grid-cols-2">
              <Card>
                <CardHeader title="Recent imports" actions={<Link className="text-xs text-brand-700 underline" to="/imports">All imports</Link>} />
                {d.recentImports.length === 0 ? <EmptyState title="No imports yet" /> : (
                  <ul className="divide-y divide-slate-100">
                    {d.recentImports.map((i) => (
                      <li key={i.id} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
                        <div className="min-w-0"><Link to={`/imports/${i.id}`} className="block truncate font-medium text-brand-700 hover:underline">{i.filename}</Link>
                          <p className="text-xs text-slate-500">{formatDateTime(i.createdAt)}{i.importedRows !== undefined && i.importedRows !== null ? ` · ${i.importedRows} imported` : ''}</p></div>
                        <ImportStatusBadge status={i.status} />
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
              <Card>
                <CardHeader title="Recent quality reviews" />
                {!d.reviewActivityVisible ? <EmptyState title="Not available for your role" /> : d.recentReviews.length === 0 ? <EmptyState title="No reviews yet" /> : (
                  <ul className="divide-y divide-slate-100">
                    {d.recentReviews.map((r, idx) => (
                      <li key={idx} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
                        <span>{humanize(r.action.replace('ISSUE_', ''))} by {r.actorName ?? 'unknown'}</span>
                        <span className="text-xs text-slate-500">{formatDateTime(r.at)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
            <p className="text-xs text-slate-400">Generated {formatDateTime(d.generatedAt)}. Import counts are not filtered by district.</p>
          </div>
        )}
      </QueryState>
    </>
  );
}
