import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { Button } from './ui';
import { cn } from '@/lib/cn';

/** Accessible modal dialog: focus moves inside, Escape closes, background is inert to clicks. */
export function Dialog({ open, onClose, title, description, children, footer, wide }: {
  open: boolean; onClose: () => void; title: string; description?: ReactNode; children?: ReactNode; footer?: ReactNode; wide?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const t = setTimeout(() => {
      const el = ref.current?.querySelector<HTMLElement>('input, select, textarea, button:not([data-close])');
      (el ?? ref.current)?.focus();
    }, 0);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => { clearTimeout(t); document.removeEventListener('keydown', onKey); prev?.focus?.(); };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[1100] flex items-end justify-center bg-slate-900/40 p-0 sm:items-center sm:p-4" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="dialog-title"
        className={cn('max-h-[92vh] w-full overflow-y-auto rounded-t-xl bg-white shadow-xl sm:rounded-xl', wide ? 'sm:max-w-3xl' : 'sm:max-w-lg')}>
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 id="dialog-title" className="text-base font-semibold text-slate-900">{title}</h2>
            {description && <div className="mt-1 text-sm text-slate-500">{description}</div>}
          </div>
          <button type="button" data-close aria-label="Close" onClick={onClose} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600">
            <X className="h-4 w-4" />
          </button>
        </div>
        {children && <div className="px-5 py-4">{children}</div>}
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 bg-slate-50 px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ open, onClose, onConfirm, title, children, confirmLabel = 'Confirm', danger, loading }: {
  open: boolean; onClose: () => void; onConfirm: () => void; title: string; children?: ReactNode; confirmLabel?: string; danger?: boolean; loading?: boolean;
}) {
  return (
    <Dialog open={open} onClose={onClose} title={title}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={loading}>Cancel</Button>
        <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
      </>}>
      <div className="text-sm text-slate-600">{children}</div>
    </Dialog>
  );
}
