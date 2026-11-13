import { useEffect, useState } from 'react';
import { Dialog } from '@/components/Dialog';
import { Alert, Badge, Button, Input } from '@/components/ui';
import { RowStateBadge } from '@/components/StatusBadges';
import type { StagedRow } from '@/types/api';
import { humanize } from '@/lib/format';

/** Side-by-side raw source values vs normalized values for one staged row, with operator corrections. */
export function RowInspector({ row, columns, open, onClose, onSave, saving, canEdit }: {
  row: StagedRow | null; columns: string[]; open: boolean; onClose: () => void; saving: boolean; canEdit: boolean;
  onSave: (values: Record<string, string>) => void;
}) {
  const [edits, setEdits] = useState<Record<string, string>>({});
  useEffect(() => setEdits({}), [row?.id]);
  if (!row) return null;
  const effective = (c: string) => edits[c] ?? row.correctedValues?.[c] ?? row.rawValues[c] ?? '';
  const changed = Object.keys(edits).filter((c) => edits[c] !== (row.correctedValues?.[c] ?? row.rawValues[c] ?? ''));
  const traces = Object.entries(row.normalized ?? {});
  return (
    <Dialog open={open} onClose={onClose} wide title={`Row ${row.rowNumber}`}
      description={<span className="flex flex-wrap items-center gap-2"><RowStateBadge state={row.state} />
        {row.page && <Badge>Page {row.page}</Badge>}{row.extractionMethod && <Badge tone="violet">Machine-extracted · {row.extractionMethod}</Badge>}</span>}
      footer={canEdit ? <>
        <Button variant="secondary" onClick={onClose}>Close</Button>
        <Button disabled={changed.length === 0} loading={saving} onClick={() => onSave(Object.fromEntries(changed.map((c) => [c, edits[c] ?? ''])))}>
          Save corrections ({changed.length})
        </Button>
      </> : <Button variant="secondary" onClick={onClose}>Close</Button>}>
      <div className="space-y-5">
        {row.validation && row.validation.length > 0 && (
          <div className="space-y-2">
            {row.validation.map((i, idx) => (
              <Alert key={idx} tone={i.blocking ? 'error' : i.severity === 'INFO' ? 'info' : 'warning'}
                title={`${i.blocking ? 'Blocking · ' : ''}${humanize(i.ruleId)}`}>{i.message}</Alert>
            ))}
          </div>
        )}
        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Normalization result</h3>
          <div className="overflow-x-auto rounded border border-slate-200">
            <table className="table-base">
              <thead><tr><th>Field</th><th>Source column</th><th>Source value</th><th>Normalized</th><th>Transformation</th></tr></thead>
              <tbody>
                {traces.length === 0 && <tr><td colSpan={5} className="text-slate-500">Save a mapping to see normalized values.</td></tr>}
                {traces.map(([field, t]) => (
                  <tr key={field}>
                    <td className="font-medium">{humanize(field)}</td>
                    <td className="text-xs text-slate-500">{t.column ?? '—'}</td>
                    <td className="mono break-all">{t.raw === null || t.raw === undefined || t.raw === '' ? <span className="text-slate-400">(empty)</span> : t.raw}</td>
                    <td className="mono break-all">{t.value ?? <span className="text-slate-400">null</span>}</td>
                    <td className="text-xs text-slate-500">{t.transformation}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Source values {canEdit && '(edit to correct before import)'}</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            {columns.map((c) => (
              <label key={c} className="block text-xs">
                <span className="mb-1 flex items-center gap-1 font-medium text-slate-600">{c}
                  {row.correctedValues?.[c] !== undefined && <Badge tone="blue" title={`Original: ${row.rawValues[c] ?? ''}`}>corrected</Badge>}
                </span>
                <Input value={effective(c)} disabled={!canEdit} onChange={(e) => setEdits((x) => ({ ...x, [c]: e.target.value }))} />
              </label>
            ))}
          </div>
        </div>
      </div>
    </Dialog>
  );
}
