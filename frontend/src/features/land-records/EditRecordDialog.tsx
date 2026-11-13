import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog } from '@/components/Dialog';
import { Alert, Button, Field, Input, Textarea } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { toApiError } from '@/lib/api';
import type { LandRecordDetail } from '@/types/api';
import { recordsApi } from './api';

const FIELDS: { key: string; label: string; get: (r: LandRecordDetail['record']) => string | number | undefined }[] = [
  { key: 'parcel_id', label: 'Parcel ID / ULPIN', get: (r) => r.parcelId },
  { key: 'survey_khasra_no', label: 'Survey / Khasra no.', get: (r) => r.surveyKhasraNo },
  { key: 'owner_name', label: 'Owner name', get: (r) => r.ownerName },
  { key: 'land_area_value', label: 'Area value', get: (r) => r.landAreaValue },
  { key: 'land_area_unit', label: 'Area unit', get: (r) => r.landAreaUnitRaw },
  { key: 'land_type', label: 'Land type', get: (r) => r.landTypeRaw },
  { key: 'registration_ref', label: 'Registration ref.', get: (r) => r.registrationRef },
  { key: 'registration_date', label: 'Registration date (yyyy-mm-dd)', get: (r) => r.registrationDate },
  { key: 'village_locality', label: 'Village / locality', get: (r) => r.villageLocality },
  { key: 'tehsil', label: 'Tehsil', get: (r) => r.tehsil },
  { key: 'district', label: 'District', get: (r) => r.district },
  { key: 'state', label: 'State', get: (r) => r.state },
  { key: 'latitude', label: 'Latitude', get: (r) => r.latitude },
  { key: 'longitude', label: 'Longitude', get: (r) => r.longitude },
];

/** Operator correction. Lineage fields are not editable; values go through backend normalization. */
export function EditRecordDialog({ detail, open, onClose }: { detail: LandRecordDetail; open: boolean; onClose: () => void }) {
  const initial = Object.fromEntries(FIELDS.map((f) => [f.key, f.get(detail.record)?.toString() ?? '']));
  const [values, setValues] = useState<Record<string, string>>(initial);
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<{ field: string; message: string }[]>([]);
  const toast = useToast();
  const qc = useQueryClient();
  const changes = Object.fromEntries(Object.entries(values).filter(([k, v]) => v !== initial[k]));
  const m = useMutation({
    mutationFn: () => recordsApi.update(detail.record.id, { version: detail.version, reason: reason.trim(), changes }),
    onSuccess: () => {
      toast.success('Record corrected. The change is recorded in its lineage.');
      void qc.invalidateQueries({ queryKey: ['records'] });
      void qc.invalidateQueries({ queryKey: ['quality'] });
      onClose();
    },
    onError: (e) => {
      const err = toApiError(e);
      setErrors(err.fieldErrors.length ? err.fieldErrors : [{ field: '', message: err.message }]);
    },
  });
  return (
    <Dialog open={open} onClose={onClose} wide title="Correct record" description="Source lineage (file, import job, row) cannot be changed. Every correction is audited."
      footer={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={Object.keys(changes).length === 0 || !reason.trim()} loading={m.isPending} onClick={() => m.mutate()}>Save {Object.keys(changes).length} change(s)</Button>
      </>}>
      <div className="space-y-4">
        {detail.ownerMasked && <Alert tone="info">Owner name is masked for your role.</Alert>}
        {errors.length > 0 && <Alert tone="error">{errors.map((e, i) => <p key={i}>{e.field ? `${e.field.replace('changes.', '')}: ` : ''}{e.message}</p>)}</Alert>}
        <div className="grid gap-3 sm:grid-cols-2">
          {FIELDS.map((f) => (
            <Field key={f.key} label={f.label} htmlFor={`f-${f.key}`}>
              <Input id={`f-${f.key}`} value={values[f.key] ?? ''} onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
                className={values[f.key] !== initial[f.key] ? 'border-brand-400 bg-brand-50/40' : undefined} />
            </Field>
          ))}
        </div>
        <Field label="Reason for correction (required)" htmlFor="reason">
          <Textarea id="reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Village name misspelt in the source sheet" />
        </Field>
      </div>
    </Dialog>
  );
}
