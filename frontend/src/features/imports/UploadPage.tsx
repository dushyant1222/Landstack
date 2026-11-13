import { useRef, useState, type DragEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { FileSpreadsheet, FileText, FileUp, UploadCloud } from 'lucide-react';
import { Alert, Button, Card, CardBody, CardHeader, PageHeader } from '@/components/ui';
import { ImportStatusBadge } from '@/components/StatusBadges';
import { useToast } from '@/components/Toast';
import { toApiError } from '@/lib/api';
import { formatBytes, formatDateTime } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { ImportJob } from '@/types/api';
import { ALLOWED_EXTENSIONS, importKeys, importsApi, MAX_UPLOAD_MB } from './api';

/** Client-side checks for obvious mistakes only; the backend repeats every check (type, signature, size). */
export function validateFile(file: File): string | null {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  if (ext === 'xls') return 'Legacy .xls files are not supported. Save the workbook as .xlsx.';
  if (!ALLOWED_EXTENSIONS.includes(ext)) return 'Unsupported file type. Allowed: .csv, .xlsx, .pdf.';
  if (file.size === 0) return 'The file is empty.';
  if (file.size > MAX_UPLOAD_MB * 1024 * 1024) return `The file is larger than ${MAX_UPLOAD_MB} MB.`;
  return null;
}

export function UploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
  const [drag, setDrag] = useState(false);
  const [result, setResult] = useState<ImportJob | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const qc = useQueryClient();
  const navigate = useNavigate();

  const mutation = useMutation({
    mutationFn: (f: File) => importsApi.upload(f, setProgress),
    onSuccess: (job) => {
      setResult(job);
      void qc.invalidateQueries({ queryKey: importKeys.all });
      toast.success('File received. Parsing has started.');
    },
  });

  const choose = (f: File | undefined | null) => {
    setResult(null);
    mutation.reset();
    setProgress(0);
    if (!f) return;
    const err = validateFile(f);
    setClientError(err);
    setFile(err ? null : f);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDrag(false);
    choose(e.dataTransfer.files?.[0]);
  };

  const apiError = mutation.isError ? toApiError(mutation.error) : null;

  return (
    <>
      <PageHeader title="Import a dataset" description="Upload a CSV, Excel (.xlsx) or text-based PDF. Nothing becomes a land record until you map the columns, review the preview and confirm." />
      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Choose a file" icon={<FileUp className="h-4 w-4" />} subtitle={`Allowed: .csv, .xlsx, .pdf · max ${MAX_UPLOAD_MB} MB`} />
          <CardBody className="space-y-4">
            <div
              onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
              onDragLeave={() => setDrag(false)}
              onDrop={onDrop}
              className={cn('flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors',
                drag ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-slate-50')}>
              <UploadCloud className="h-10 w-10 text-brand-500" aria-hidden />
              <p className="mt-3 text-sm font-medium text-slate-700">Drag and drop a file here</p>
              <p className="text-xs text-slate-500">or</p>
              <Button variant="secondary" className="mt-2" onClick={() => inputRef.current?.click()}>Browse files</Button>
              <input ref={inputRef} type="file" className="sr-only" aria-label="Select dataset file"
                accept=".csv,.xlsx,.pdf,text/csv,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={(e) => { choose(e.target.files?.[0]); e.target.value = ''; }} />
            </div>

            {clientError && <Alert tone="error">{clientError}</Alert>}
            {file && (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2.5">
                <div className="flex min-w-0 items-center gap-2">
                  {file.name.toLowerCase().endsWith('.pdf') ? <FileText className="h-5 w-5 text-rose-500" /> : <FileSpreadsheet className="h-5 w-5 text-emerald-600" />}
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-800">{file.name}</p>
                    <p className="text-xs text-slate-500">{formatBytes(file.size)}</p>
                  </div>
                </div>
                <Button onClick={() => mutation.mutate(file)} loading={mutation.isPending} disabled={!!result}>Upload</Button>
              </div>
            )}
            {mutation.isPending && (
              <div aria-label="Upload progress" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
                <div className="h-2 w-full overflow-hidden rounded bg-slate-200"><div className="h-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} /></div>
                <p className="mt-1 text-xs text-slate-500">{progress < 100 ? `Uploading… ${progress}%` : 'Upload complete; validating on the server…'}</p>
              </div>
            )}
            {apiError && <Alert tone="error" title="Upload rejected">{apiError.message}</Alert>}
            {result && (
              <Alert tone="success" title="Import job created"
                action={<Button size="sm" onClick={() => navigate(`/imports/${result.id}`)}>Open import</Button>}>
                <div className="space-y-0.5">
                  <p>Job <span className="mono">{result.id}</span> · <ImportStatusBadge status={result.status} /></p>
                  <p>{result.originalFilename} · {formatBytes(result.sizeBytes)} · {formatDateTime(result.createdAt)}</p>
                  {result.duplicateOfJobId && (
                    <p className="text-amber-800">An identical file (same SHA-256) was uploaded before — <Link className="underline" to={`/imports/${result.duplicateOfJobId}`}>see earlier import</Link>. This upload is kept as a separate job.</p>
                  )}
                </div>
              </Alert>
            )}
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="What happens next" />
          <CardBody>
            <ol className="space-y-3 text-sm text-slate-600">
              {[
                ['Validate', 'File type is checked from its content (not just the name), size is limited and a checksum is recorded.'],
                ['Parse / extract', 'CSV and Excel are read as text; PDFs go to the extraction service. Scanned PDFs are reported, not guessed.'],
                ['Map columns', 'Match source columns to LandStack fields. Suggestions must be confirmed by you.'],
                ['Review', 'See raw and normalized values side by side, warnings and blocking errors. Correct or exclude rows.'],
                ['Confirm', 'Selected rows are saved in one transaction with full lineage to this file and job.'],
              ].map(([t, d], i) => (
                <li key={t} className="flex gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">{i + 1}</span>
                  <span><span className="font-medium text-slate-800">{t}.</span> {d}</span>
                </li>
              ))}
            </ol>
          </CardBody>
        </Card>
      </div>
    </>
  );
}
