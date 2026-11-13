import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Info, Loader2, OctagonAlert } from 'lucide-react';
import { cn } from '@/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent';
type Size = 'sm' | 'md';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white hover:bg-brand-800 disabled:bg-brand-300',
  accent: 'bg-accent-600 text-white hover:bg-accent-700 disabled:bg-accent-300',
  secondary: 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 disabled:text-slate-400',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:text-slate-300',
  danger: 'bg-rose-600 text-white hover:bg-rose-700 disabled:bg-rose-300',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, icon, className, children, disabled, type = 'button', ...rest }, ref) {
  return (
    <button ref={ref} type={type} disabled={disabled || loading}
      className={cn('inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:cursor-not-allowed',
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-9 px-4 text-sm', VARIANTS[variant], className)} {...rest}>
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
});

const fieldBase = 'block w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 disabled:bg-slate-100';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ className, invalid, ...rest }, ref) {
    return <input ref={ref} aria-invalid={invalid || undefined} className={cn(fieldBase, 'h-9', invalid && 'border-rose-400', className)} {...rest} />;
  });

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }>(
  function Select({ className, invalid, children, ...rest }, ref) {
    return <select ref={ref} aria-invalid={invalid || undefined} className={cn(fieldBase, 'h-9 pr-8', invalid && 'border-rose-400', className)} {...rest}>{children}</select>;
  });

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...rest }, ref) {
    return <textarea ref={ref} className={cn(fieldBase, 'min-h-[80px] py-2', className)} {...rest} />;
  });

export function Field({ label, htmlFor, error, hint, children, className }: {
  label: string; htmlFor?: string; error?: string; hint?: string; children: ReactNode; className?: string;
}) {
  return (
    <div className={cn('space-y-1', className)}>
      <label htmlFor={htmlFor} className="block text-xs font-medium text-slate-600">{label}</label>
      {children}
      {error ? <p role="alert" className="text-xs text-rose-600">{error}</p> : hint ? <p className="text-xs text-slate-500">{hint}</p> : null}
    </div>
  );
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={cn('rounded-lg border border-slate-200 bg-white shadow-card', className)}>{children}</section>;
}

export function CardHeader({ title, subtitle, actions, icon }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode; icon?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-4 py-3">
      <div className="flex items-start gap-2">
        {icon && <span className="mt-0.5 text-brand-600">{icon}</span>}
        <div>
          <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
          {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('p-4', className)}>{children}</div>;
}

type Tone = 'slate' | 'blue' | 'green' | 'amber' | 'red' | 'teal' | 'violet';
const TONES: Record<Tone, string> = {
  slate: 'bg-slate-100 text-slate-700 ring-slate-200',
  blue: 'bg-brand-50 text-brand-700 ring-brand-200',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  red: 'bg-rose-50 text-rose-700 ring-rose-200',
  teal: 'bg-accent-50 text-accent-700 ring-accent-200',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200',
};

export function Badge({ tone = 'slate', children, className, title }: { tone?: Tone; children: ReactNode; className?: string; title?: string }) {
  return <span title={title} className={cn('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', TONES[tone], className)}>{children}</span>;
}

type AlertTone = 'info' | 'success' | 'warning' | 'error';
export function Alert({ tone = 'info', title, children, className, action }: {
  tone?: AlertTone; title?: string; children?: ReactNode; className?: string; action?: ReactNode;
}) {
  const styles: Record<AlertTone, [string, ReactNode]> = {
    info: ['border-brand-200 bg-brand-50 text-brand-800', <Info key="i" className="h-4 w-4" />],
    success: ['border-emerald-200 bg-emerald-50 text-emerald-800', <CheckCircle2 key="s" className="h-4 w-4" />],
    warning: ['border-amber-200 bg-amber-50 text-amber-900', <AlertTriangle key="w" className="h-4 w-4" />],
    error: ['border-rose-200 bg-rose-50 text-rose-800', <OctagonAlert key="e" className="h-4 w-4" />],
  };
  const [cls, icon] = styles[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('flex gap-3 rounded-md border px-3 py-2.5 text-sm', cls, className)}>
      <span className="mt-0.5 shrink-0">{icon}</span>
      <div className="min-w-0 flex-1">
        {title && <p className="font-medium">{title}</p>}
        {children && <div className={cn(title && 'mt-0.5', 'text-[0.82rem] leading-relaxed')}>{children}</div>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded bg-slate-200/80', className)} aria-hidden />;
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-slate-500" role="status">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {label}
    </span>
  );
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      {icon && <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-500">{icon}</div>}
      <p className="text-sm font-semibold text-slate-700">{title}</p>
      {children && <div className="mt-1 max-w-md text-sm text-slate-500">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onPage, onSize }: {
  page: number; totalPages: number; totalElements: number; size: number; onPage: (p: number) => void; onSize?: (s: number) => void;
}) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-2.5 text-xs text-slate-600">
      <span>{from}–{to} of {totalElements.toLocaleString()}</span>
      <div className="flex items-center gap-2">
        {onSize && (
          <label className="flex items-center gap-1">
            <span className="sr-only sm:not-sr-only">Rows</span>
            <select aria-label="Rows per page" className="h-7 rounded border border-slate-300 bg-white px-1 text-xs" value={size}
              onChange={(e) => onSize(Number(e.target.value))}>
              {[10, 25, 50, 100].map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
        )}
        <Button variant="secondary" size="sm" aria-label="Previous page" disabled={page <= 0} onClick={() => onPage(page - 1)} icon={<ChevronLeft className="h-3.5 w-3.5" />} />
        <span aria-live="polite">Page {totalPages === 0 ? 0 : page + 1} / {totalPages}</span>
        <Button variant="secondary" size="sm" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} icon={<ChevronRight className="h-3.5 w-3.5" />} />
      </div>
    </nav>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, icon, tone = 'blue' }: { label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; tone?: 'blue' | 'teal' | 'amber' | 'red' | 'slate' }) {
  const ring = { blue: 'text-brand-600 bg-brand-50', teal: 'text-accent-600 bg-accent-50', amber: 'text-amber-600 bg-amber-50', red: 'text-rose-600 bg-rose-50', slate: 'text-slate-600 bg-slate-100' }[tone];
  return (
    <Card className="p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
          {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
        </div>
        {icon && <span className={cn('rounded-md p-2', ring)}>{icon}</span>}
      </div>
    </Card>
  );
}

export function Tabs<T extends string>({ tabs, value, onChange }: { tabs: { id: T; label: ReactNode; disabled?: boolean }[]; value: T; onChange: (v: T) => void }) {
  return (
    <div role="tablist" className="flex gap-1 overflow-x-auto border-b border-slate-200">
      {tabs.map((t) => (
        <button key={t.id} role="tab" type="button" aria-selected={value === t.id} disabled={t.disabled} onClick={() => onChange(t.id)}
          className={cn('whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:text-slate-300',
            value === t.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-700')}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function KeyValue({ items, className }: { items: { label: string; value: ReactNode; hint?: string }[]; className?: string }) {
  return (
    <dl className={cn('grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2', className)}>
      {items.map((i) => (
        <div key={i.label} className="min-w-0">
          <dt className="text-xs font-medium text-slate-500" title={i.hint}>{i.label}</dt>
          <dd className="mt-0.5 break-words text-sm text-slate-800">{i.value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
