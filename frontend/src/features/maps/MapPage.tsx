import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { CircleMarker, GeoJSON, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';
import type { GeoJsonObject } from 'geojson';
import L from 'leaflet';
import { MapPinOff, Table2 } from 'lucide-react';
import { Alert, Badge, Button, Card, CardHeader, EmptyState, PageHeader, StatCard } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { humanize } from '@/lib/format';
import type { MapPoint } from '@/types/api';
import { FILTER_KEYS, filtersFromParams, recordsApi, type RecordFilters } from '@/features/land-records/api';
import { RecordFiltersBar } from '@/features/land-records/RecordFiltersBar';
import { CATEGORY_COLORS, TILE_ATTRIBUTION, TILE_URL, UNCATEGORISED_COLOR } from './tiles';

function FitBounds({ points }: { points: MapPoint[] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 0) return;
    const b = L.latLngBounds(points.map((p) => [p.lat, p.lon] as [number, number]));
    map.fitBounds(b.pad(0.15), { maxZoom: 15 });
  }, [points, map]);
  return null;
}

export function MapPage() {
  const [params, setParams] = useSearchParams();
  const filters = filtersFromParams(params);
  const [showGeometry, setShowGeometry] = useState(false);
  const [tileError, setTileError] = useState(false);
  const setFilters = (f: RecordFilters) => {
    const next = new URLSearchParams();
    FILTER_KEYS.forEach((k) => { if (f[k] && k !== 'mappable') next.set(k, f[k]!); });
    setParams(next, { replace: true });
  };
  // The map always shows mappable records; the "mappable" filter only applies to the table view.
  const mapFilters: RecordFilters = { ...filters, mappable: undefined };
  const q = useQuery({
    queryKey: ['records', 'map', mapFilters, showGeometry],
    queryFn: () => recordsApi.map(mapFilters, showGeometry),
    placeholderData: (prev) => prev,
  });
  const categories = useMemo(() => {
    const s = new Set<string>();
    q.data?.points.forEach((p) => s.add(p.landTypeCategory ?? 'UNCATEGORISED'));
    return [...s].sort();
  }, [q.data]);
  const tableLink = `/records?${new URLSearchParams({ ...(Object.fromEntries(Object.entries(mapFilters).filter(([, v]) => v)) as Record<string, string>), mappable: 'false' }).toString()}`;

  return (
    <>
      <PageHeader title="Map view" description="Only records whose own source coordinates passed validation are drawn. No geocoding, no estimated or default locations."
        actions={<Link to={tableLink}><Button variant="secondary" icon={<Table2 className="h-4 w-4" />}>Non-mappable records</Button></Link>} />
      {q.data && (
        <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
          <StatCard label="Mappable" value={q.data.mappableCount.toLocaleString()} tone="teal" />
          <StatCard label="Not mappable" value={q.data.notMappableCount.toLocaleString()} tone="slate" hint="Missing or invalid coordinates" />
          <StatCard label="Drawn" value={q.data.returned.toLocaleString()} tone="blue" hint={q.data.truncated ? 'Limited to 5,000 — narrow filters' : 'All matching mappable records'} />
          <StatCard label="Categories" value={categories.length} tone="slate" />
        </div>
      )}
      <Card>
        <RecordFiltersBar filters={mapFilters} onChange={setFilters} />
        <CardHeader title="Records with valid source coordinates"
          subtitle="Points mark supplied coordinates only. They are not surveyed parcel boundaries and do not indicate official verification."
          actions={<label className="flex items-center gap-2 text-xs text-slate-600"><input type="checkbox" checked={showGeometry} onChange={(e) => setShowGeometry(e.target.checked)} />Show source-supplied boundaries</label>} />
        {tileError && <div className="px-4 pt-3"><Alert tone="warning">Base-map tiles could not be loaded. Record points are still shown; check your network or tile provider.</Alert></div>}
        <QueryState query={q} isEmpty={(d) => d.points.length === 0}
          empty={<EmptyState icon={<MapPinOff className="h-5 w-5" />} title="No mappable records">
            {(q.data?.notMappableCount ?? 0) > 0 ? `${q.data?.notMappableCount} matching record(s) have no valid coordinates and are listed in the table view.` : 'No confirmed records match these filters.'}
          </EmptyState>}>
          {(d) => (
            <div className="relative">
              <div className="h-[560px]">
                <MapContainer preferCanvas className="h-full w-full" bounds={L.latLngBounds(d.points.map((p) => [p.lat, p.lon] as [number, number])).pad(0.15)}>
                  <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} eventHandlers={{ tileerror: () => setTileError(true) }} />
                  <FitBounds points={d.points} />
                  {showGeometry && d.points.filter((p) => p.geometry && p.geometry.type !== 'Point').map((p) => (
                    <GeoJSON key={`g-${p.id}`} data={p.geometry as unknown as GeoJsonObject}
                      style={{ color: CATEGORY_COLORS[p.landTypeCategory ?? ''] ?? UNCATEGORISED_COLOR, weight: 1.5, fillOpacity: 0.15 }} />
                  ))}
                  {d.points.map((p) => (
                    <CircleMarker key={p.id} center={[p.lat, p.lon]} radius={6}
                      pathOptions={{ color: '#ffffff', weight: 1, fillColor: CATEGORY_COLORS[p.landTypeCategory ?? ''] ?? UNCATEGORISED_COLOR, fillOpacity: 0.9 }}>
                      <Popup>
                        <div className="space-y-1 text-xs">
                          <p className="font-semibold">{p.parcelId ?? p.surveyKhasraNo ?? 'Record'}</p>
                          {p.surveyKhasraNo && <p>Khasra: {p.surveyKhasraNo}</p>}
                          <p>{[p.village, p.district].filter(Boolean).join(', ') || '—'}</p>
                          <p>{p.landTypeRaw ?? 'Land type not supplied'}</p>
                          <p className="text-slate-500">{p.lat}, {p.lon}</p>
                          <Link className="text-brand-700 underline" to={`/records/${p.id}`}>Open record</Link>
                        </div>
                      </Popup>
                    </CircleMarker>
                  ))}
                </MapContainer>
              </div>
              <div className="absolute bottom-6 left-3 z-[500] max-w-[14rem] rounded-md border border-slate-200 bg-white/95 p-2.5 text-xs shadow">
                <p className="mb-1 font-semibold text-slate-700">Land type</p>
                {categories.map((c) => (
                  <p key={c} className="flex items-center gap-2"><span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: CATEGORY_COLORS[c] ?? UNCATEGORISED_COLOR }} />{humanize(c)}</p>
                ))}
                <p className="mt-1 text-[0.65rem] text-slate-400">Source coordinates · not boundaries</p>
              </div>
            </div>
          )}
        </QueryState>
      </Card>
      {q.data?.truncated && <Badge tone="amber" className="mt-3">Showing the first 5,000 matching points. Narrow the filters to see the rest.</Badge>}
    </>
  );
}
