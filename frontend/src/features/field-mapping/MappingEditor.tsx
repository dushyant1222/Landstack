import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Save, Wand2 } from 'lucide-react';
import { Alert, Badge, Button, Card, CardBody, CardHeader, Field, Input, Select } from '@/components/ui';
import { QueryState } from '@/components/QueryState';
import { useToast } from '@/components/Toast';
import { toApiError } from '@/lib/api';
import { humanize } from '@/lib/format';
import type { ImportJob, MappingRequest, MappingSuggestions, MappingTemplate } from '@/types/api';
import { importKeys, importsApi } from '@/features/imports/api';

type Status = 'confirmed' | 'suggested' | 'ambiguous' | 'unmapped';

export function MappingEditor({ job, onSaved }: { job: ImportJob; onSaved: () => void }) {
  const query = useQuery({ queryKey: [...importKeys.one(job.id), 'suggestions', job.version], queryFn: () => importsApi.suggestions(job.id) });
  return (
    <QueryState query={query}>
      {(s) => <Editor job={job} s={s} onSaved={onSaved} />}
    </QueryState>
  );
}

function Editor({ job, s, onSaved }: { job: ImportJob; s: MappingSuggestions; onSaved: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const opts = s.current?.options ?? {};
  const [mapping, setMapping] = useState<Record<string, string>>(() => {
    if (s.current) return { ...s.current.mapping };
    const m: Record<string, string> = {};
    s.fields.forEach((f) => { if (f.status === 'SUGGESTED' && f.suggestedColumn) m[f.field] = f.suggestedColumn; });
    return m;
  });
  const [touched, setTouched] = useState<Set<string>>(() => new Set(s.current ? Object.keys(s.current.mapping) : []));
  const [dayFirst, setDayFirst] = useState<boolean>(Boolean(opts.dayFirst));
  const [declaredAreaUnit, setUnit] = useState<string>(String(opts.declaredAreaUnit ?? ''));
  const [declaredState, setState] = useState<string>(String(opts.declaredState ?? ''));
  const [requireIdentifier, setReqId] = useState<boolean>(opts.requireIdentifier !== false);
  const [requireDistrict, setReqDistrict] = useState<boolean>(Boolean(opts.requireDistrict));
  const [templateId, setTemplateId] = useState<string | undefined>(s.current?.templateId);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [templateName, setTemplateName] = useState('');

  const templates = useQuery({ queryKey: ['mapping-templates'], queryFn: importsApi.templates });

  useEffect(() => setFieldErrors({}), [mapping]);

  const statusOf = (field: string): Status => {
    const sug = s.fields.find((f) => f.field === field);
    if (mapping[field] && touched.has(field)) return 'confirmed';
    if (mapping[field] && sug?.status === 'SUGGESTED' && sug.suggestedColumn === mapping[field]) return 'suggested';
    if (mapping[field]) return 'confirmed';
    if (sug?.status === 'AMBIGUOUS') return 'ambiguous';
    return 'unmapped';
  };

  const usedColumns = useMemo(() => {
    const c: Record<string, number> = {};
    Object.values(mapping).forEach((v) => { if (v) c[v] = (c[v] ?? 0) + 1; });
    return c;
  }, [mapping]);

  const save = useMutation({
    mutationFn: () => {
      const body: MappingRequest = {
        mapping: Object.fromEntries(Object.entries(mapping).filter(([, v]) => v)),
        dayFirst, requireIdentifier, requireDistrict,
        declaredAreaUnit: declaredAreaUnit.trim() || undefined,
        declaredState: declaredState.trim() || undefined,
        templateId,
      };
      return importsApi.saveMapping(job.id, body);
    },
    onSuccess: (summary) => {
      toast.success(`Mapping saved. ${summary.totalRows} rows normalized and validated.`);
      void qc.invalidateQueries({ queryKey: importKeys.one(job.id) });
      onSaved();
    },
    onError: (e) => {
      const err = toApiError(e);
      const fe: Record<string, string> = {};
      err.fieldErrors.forEach((f) => { fe[f.field.replace('mapping.', '')] = f.message; });
      setFieldErrors(fe);
      toast.error(err.message);
    },
  });

  const saveTemplate = useMutation({
    mutationFn: () => importsApi.saveTemplate({ name: templateName.trim(), fromImportJobId: job.id }),
    onSuccess: (t) => {
      toast.success(`Template “${t.name}” saved as version ${t.version}.`);
      setTemplateName('');
      void qc.invalidateQueries({ queryKey: ['mapping-templates'] });
    },
    onError: (e) => toast.error(toApiError(e).message),
  });

  const applyTemplate = (t: MappingTemplate) => {
    const next: Record<string, string> = {};
    const missing: string[] = [];
    Object.entries(t.mapping).forEach(([field, col]) => {
      if (s.sourceColumns.includes(col)) next[field] = col; else missing.push(col);
    });
    setMapping(next);
    setTouched(new Set(Object.keys(next)));
    setTemplateId(t.id);
    setDayFirst(Boolean(t.options.dayFirst));
    setUnit(String(t.options.declaredAreaUnit ?? ''));
    setState(String(t.options.declaredState ?? ''));
    setReqId(t.options.requireIdentifier !== false);
    setReqDistrict(Boolean(t.options.requireDistrict));
    if (missing.length) toast.info(`Template applied. Columns not in this file were skipped: ${missing.join(', ')}`);
    else toast.success('Template applied. Review the mapping, then save.');
  };

  const pendingSuggestions = s.fields.filter((f) => statusOf(f.field) === 'suggested').length;

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader title="Map source columns to LandStack fields" icon={<Wand2 className="h-4 w-4" />}
          subtitle="Suggestions come only from exact, known column names and are not applied until you save. Optional fields may stay unmapped."
          actions={
            templates.data && templates.data.length > 0 ? (
              <Select aria-label="Apply mapping template" className="h-8 w-56 text-xs" value="" onChange={(e) => {
                const t = templates.data.find((x) => x.id === e.target.value);
                if (t) applyTemplate(t);
              }}>
                <option value="">Apply a saved template…</option>
                {templates.data.map((t) => <option key={t.id} value={t.id}>{t.name} (v{t.version})</option>)}
              </Select>
            ) : undefined
          } />
        {pendingSuggestions > 0 && (
          <div className="px-4 pt-3"><Alert tone="info">{pendingSuggestions} field(s) have suggested columns. Check each one; they are only used once you save the mapping.</Alert></div>
        )}
        <div className="overflow-x-auto">
          <table className="table-base">
            <thead><tr><th>LandStack field</th><th>Source column</th><th>Status</th><th>Sample source values</th></tr></thead>
            <tbody>
              {s.fields.map((f) => {
                const st = statusOf(f.field);
                const col = mapping[f.field] ?? '';
                const sug = s.fields.find((x) => x.field === f.field);
                return (
                  <tr key={f.field}>
                    <td className="min-w-[11rem]">
                      <p className="font-medium text-slate-800">{f.label}</p>
                      <p className="text-xs text-slate-500">{humanize(f.kind)}</p>
                    </td>
                    <td className="min-w-[14rem]">
                      <Select aria-label={`Source column for ${f.label}`} value={col} invalid={!!fieldErrors[f.field]}
                        onChange={(e) => {
                          setMapping((m) => ({ ...m, [f.field]: e.target.value }));
                          setTouched((t) => new Set(t).add(f.field));
                        }}>
                        <option value="">— not mapped —</option>
                        {s.sourceColumns.map((c) => (
                          <option key={c} value={c}>{c}{usedColumns[c] && mapping[f.field] !== c ? ' (in use)' : ''}</option>
                        ))}
                      </Select>
                      {fieldErrors[f.field] && <p className="mt-1 text-xs text-rose-600">{fieldErrors[f.field]}</p>}
                      {col && (usedColumns[col] ?? 0) > 1 && <p className="mt-1 text-xs text-rose-600">This column is mapped to more than one field.</p>}
                    </td>
                    <td>
                      {st === 'confirmed' && <Badge tone="green">Mapped</Badge>}
                      {st === 'suggested' && <Badge tone="amber" title="Suggested from the column name - not confirmed">Suggested · not confirmed</Badge>}
                      {st === 'ambiguous' && <Badge tone="violet" title={sug?.candidates.join(', ')}>Ambiguous: {sug?.candidates.join(' / ')}</Badge>}
                      {st === 'unmapped' && <Badge>Unmapped</Badge>}
                    </td>
                    <td className="max-w-xs text-xs text-slate-600">
                      {col ? (s.sampleValues[col] ?? []).map((v) => <span key={v} className="mr-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 font-mono">{v}</span>) : <span className="text-slate-400">—</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <CardHeader title="Dataset options" subtitle="Declared values apply to the whole file and are recorded as operator-declared in each record's lineage." />
        <CardBody className="grid gap-4 md:grid-cols-2">
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input type="checkbox" className="mt-1" checked={dayFirst} onChange={(e) => setDayFirst(e.target.checked)} />
            <span><span className="font-medium">Dates are day-first</span> (dd/mm/yyyy). Without this, dates such as 04/05/2019 are flagged as ambiguous and left empty.</span>
          </label>
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input type="checkbox" className="mt-1" checked={requireIdentifier} onChange={(e) => setReqId(e.target.checked)} />
            <span><span className="font-medium">Require an identifier</span> (parcel ID/ULPIN or survey/khasra). Rows without one are blocked.</span>
          </label>
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input type="checkbox" className="mt-1" checked={requireDistrict} onChange={(e) => setReqDistrict(e.target.checked)} />
            <span><span className="font-medium">Require district</span>. Rows without a district are blocked.</span>
          </label>
          <div />
          <Field label="Area unit for rows without one (optional)" htmlFor="du" error={fieldErrors.declaredAreaUnit}
            hint="Only used when a row has no unit, e.g. if the column header says 'Area (acres)'.">
            <Input id="du" placeholder="e.g. acre, hectare, sq ft" value={declaredAreaUnit} onChange={(e) => setUnit(e.target.value)} />
          </Field>
          <Field label="State for the whole file (optional)" htmlFor="ds" error={fieldErrors.declaredState}
            hint="Used only to pick regional unit factors (bigha, katha, kanal…) when rows have no state.">
            <Input id="ds" placeholder="e.g. Bihar" value={declaredState} onChange={(e) => setState(e.target.value)} />
          </Field>
        </CardBody>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-3">
          <div className="flex items-center gap-2">
            <Input aria-label="Template name" placeholder="Template name" className="h-8 w-48 text-xs" value={templateName} onChange={(e) => setTemplateName(e.target.value)} />
            <Button size="sm" variant="secondary" disabled={!templateName.trim() || !s.current} loading={saveTemplate.isPending}
              title={!s.current ? 'Save the mapping first' : undefined} onClick={() => saveTemplate.mutate()}>Save as template</Button>
          </div>
          <Button icon={<Save className="h-4 w-4" />} loading={save.isPending} onClick={() => save.mutate()}>Save mapping &amp; preview</Button>
        </div>
      </Card>
    </div>
  );
}
