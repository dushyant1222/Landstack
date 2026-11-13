import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Archive, ArchiveRestore, FileText, MapPin, Pencil, ShieldAlert } from 'lucide-react';
import { MapContainer, TileLayer, CircleMarker, GeoJSON } from 'react-leaflet';
import type { GeoJsonObject } from 'geojson';
import { Alert, Badge, Button, Card, CardBody, CardHeader, EmptyState, Field, KeyValue, PageHeader, Textarea } from '@/components/ui';
import { Dialog } from '@/components/Dialog';
import { QueryState } from '@/components/QueryState';
import { IssueStatusBadge, MappableBadge, SeverityBadge, SourceTag, SystemTag } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { CAN_OPERATE, useAuth } from '@/features/auth/AuthContext';
import { TILE_ATTRIBUTION, TILE_URL } from '@/features/maps/tiles';
import { toApiError } from '@/lib/api';
import { formatArea, formatDate, formatDateTime, humanize } from '@/lib/format';
import { recordsApi } from './api';
import { EditRecordDialog } from './EditRecordDialog';

export function RecordDetailPage() {
  const { id = '' } = useParams();
  const { hasRole } = useAuth();
  const canEdit = hasRole(...CAN_OPERATE);
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [reason, setReason] = useState('');
  const toast = useToast();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['records', 'detail', id], queryFn: () => recordsApi.get(id) });
  const archive = useMutation({
    mutationFn: (restore: boolean) => (restore ? recordsApi.restore(id) : recordsApi.archive(id, reason.trim())),
    onSuccess: (_d, restore) => {
      toast.success(restore ? 'Record restored.' : 'Record archived. It is hidden from default searches but kept with its lineage.');
      setArchiving(false);
      void qc.invalidateQueries({ queryKey: ['records'] });
    },
    onError: (e) => toast.error(toApiError(e).message),
  });

  return (
    <QueryState query={q}>
      {(d) => {
        const r = d.record;
        return (
          <>
            <PageHeader title={r.parcelId ?? r.surveyKhasraNo ?? 'Land record'}
              description={<span className="flex flex-wrap items-center gap-2"><MappableBadge mappable={r.mappable} />
                {r.recordStatus === 'ARCHIVED' && <Badge>Archived</Badge>}<span className="mono text-xs">{r.id}</span></span>}
              actions={canEdit && <>
                {r.recordStatus === 'CONFIRMED'
                  ? <><Button variant="secondary" icon={<Pencil className="h-4 w-4" />} onClick={() => setEditing(true)}>Correct</Button>
                    <Button variant="secondary" icon={<Archive className="h-4 w-4" />} onClick={() => setArchiving(true)}>Archive</Button></>
                  : <Button variant="secondary" icon={<ArchiveRestore className="h-4 w-4" />} loading={archive.isPending} onClick={() => archive.mutate(true)}>Restore</Button>}
              </>} />
            {r.recordStatus === 'ARCHIVED' && <Alert tone="info" className="mb-4" title="Archived">{d.archivedReason}</Alert>}
            <div className="grid gap-5 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader title="Normalized values" subtitle="Produced by LandStack from the source file. Not an official or verified record." />
                <CardBody>
                  <KeyValue items={[
                    { label: 'Parcel ID / ULPIN', value: <span className="mono">{r.parcelId ?? '—'}</span> },
                    { label: 'Survey / Khasra no.', value: <span className="mono">{r.surveyKhasraNo ?? '—'}</span> },
                    { label: 'Owner name', value: <>{r.ownerName ?? '—'}{d.ownerMasked && <Badge className="ml-2">masked</Badge>}</> },
                    { label: 'Registration ref.', value: r.registrationRef },
                    { label: 'Registration date', value: formatDate(r.registrationDate) },
                    { label: 'Area (as supplied)', value: formatArea(r.landAreaValue, r.landAreaUnitRaw) },
                    { label: 'Area (normalized)', value: r.landAreaSqm !== undefined && r.landAreaSqm !== null ? `${r.landAreaSqm.toLocaleString()} m²` : 'Not converted' },
                    { label: 'Land type', value: <>{r.landTypeRaw ?? '—'}{r.landTypeCategory && <Badge tone="teal" className="ml-2">{humanize(r.landTypeCategory)}</Badge>}</> },
                    { label: 'Village / locality', value: r.villageLocality },
                    { label: 'Tehsil', value: r.tehsil },
                    { label: 'District', value: r.district },
                    { label: 'State', value: r.state },
                    { label: 'Coordinates', value: r.mappable ? <span className="mono">{r.latitude}, {r.longitude}</span> : 'No spatial data (not mappable)' },
                    { label: 'Geometry', value: d.geometry ? `${d.geometry.type} (source-supplied)` : 'None supplied' },
                  ]} />
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Provenance" icon={<FileText className="h-4 w-4" />} />
                <CardBody className="space-y-3 text-sm">
                  <KeyValue className="sm:grid-cols-1" items={[
                    { label: 'Source file', value: <>{d.sourceFile.originalFilename} <Badge>{d.sourceFile.type}</Badge></> },
                    { label: 'Source row', value: `${d.provenance.sourceRowNumber}${d.provenance.sourcePage ? ` (page ${d.provenance.sourcePage})` : ''}` },
                    { label: 'Import job', value: <Link className="text-brand-700 underline" to={`/imports/${d.importJob.id}`}>{d.importJob.id.slice(0, 8)}…</Link> },
                    { label: 'Confirmed', value: `${formatDateTime(d.importJob.confirmedAt)}${d.importJob.confirmedByName ? ` by ${d.importJob.confirmedByName}` : ''}` },
                    { label: 'Normalization', value: d.provenance.normalizationVersion ?? '—' },
                    { label: 'Mapping template', value: d.provenance.templateId ? `v${d.provenance.templateVersion}` : 'None (mapped manually)' },
                    { label: 'File SHA-256', value: <span className="mono break-all text-xs">{d.sourceFile.sha256}</span> },
                  ]} />
                </CardBody>
              </Card>

              <Card className="lg:col-span-2">
                <CardHeader title="Source values and transformations" subtitle="Each field: source column, original value, normalized value and how it was derived. Corrections are appended, never overwritten." />
                <div className="overflow-x-auto">
                  <table className="table-base">
                    <thead><tr><th>Field</th><th>Source column</th><th><SourceTag /></th><th><SystemTag /></th><th>Transformation</th><th>When</th></tr></thead>
                    <tbody>
                      {d.sourceValues.map((v, i) => (
                        <tr key={i} className={v.transformation.startsWith('MANUAL_CORRECTION') ? 'bg-brand-50/40' : undefined}>
                          <td className="font-medium">{humanize(v.field)}</td>
                          <td className="text-xs text-slate-500">{v.sourceColumn ?? '—'}</td>
                          <td className="mono break-all">{v.rawValue ?? <span className="text-slate-400">(empty)</span>}</td>
                          <td className="mono break-all">{v.normalizedValue ?? <span className="text-slate-400">null</span>}</td>
                          <td className="text-xs text-slate-600">{v.transformation}{v.changedByName && <p className="text-slate-400">by {v.changedByName}</p>}</td>
                          <td className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(v.at)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>

              <div className="space-y-5">
                <Card>
                  <CardHeader title="Location" icon={<MapPin className="h-4 w-4" />} />
                  {r.mappable && r.latitude !== undefined && r.longitude !== undefined ? (
                    <div className="h-56">
                      <MapContainer center={[r.latitude, r.longitude]} zoom={14} className="h-full w-full rounded-b-lg" scrollWheelZoom={false}>
                        <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
                        {d.geometry && d.geometry.type !== 'Point' && <GeoJSON data={d.geometry as unknown as GeoJsonObject} style={{ color: '#155e51', weight: 2 }} />}
                        <CircleMarker center={[r.latitude, r.longitude]} radius={7} pathOptions={{ color: '#1e3f6c', fillColor: '#3fae97', fillOpacity: 0.9 }} />
                      </MapContainer>
                    </div>
                  ) : <EmptyState title="Not mappable">This record has no valid source coordinates. LandStack never guesses a location.</EmptyState>}
                  <p className="px-4 py-2 text-[0.7rem] text-slate-500">Point shown from source coordinates only; not a surveyed boundary.</p>
                </Card>
                <Card>
                  <CardHeader title="Quality issues" icon={<ShieldAlert className="h-4 w-4" />} actions={<Link className="text-xs text-brand-700 underline" to={`/quality?landRecordId=${r.id}`}>Open in Data quality</Link>} />
                  <CardBody className="space-y-3">
                    {d.issues.length === 0 && <p className="text-sm text-slate-500">No issues recorded.</p>}
                    {d.issues.map((i) => (
                      <div key={i.id} className="space-y-1 border-b border-slate-100 pb-2 last:border-0">
                        <div className="flex flex-wrap items-center gap-1.5"><SeverityBadge severity={i.severity} /><IssueStatusBadge status={i.status} /><span className="mono text-xs">{i.ruleId}</span></div>
                        <p className="text-xs text-slate-600">{i.reason}</p>
                      </div>
                    ))}
                  </CardBody>
                </Card>
              </div>
            </div>
            {editing && <EditRecordDialog detail={d} open={editing} onClose={() => setEditing(false)} />}
            <Dialog open={archiving} onClose={() => setArchiving(false)} title="Archive record?" description="Archived records are hidden from default search, map and export, but remain with full lineage and can be restored."
              footer={<><Button variant="secondary" onClick={() => setArchiving(false)}>Cancel</Button>
                <Button variant="danger" disabled={!reason.trim()} loading={archive.isPending} onClick={() => archive.mutate(false)}>Archive</Button></>}>
              <Field label="Reason (required)" htmlFor="arch"><Textarea id="arch" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
            </Dialog>
          </>
        );
      }}
    </QueryState>
  );
}
