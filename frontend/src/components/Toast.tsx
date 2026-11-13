import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { CheckCircle2, OctagonAlert, Info, X } from 'lucide-react';
import { cn } from '@/lib/cn';

type ToastTone = 'success' | 'error' | 'info';
interface Toast { id: number; tone: ToastTone; message: string }

const ToastContext = createContext<{ push: (tone: ToastTone, message: string) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback((tone: ToastTone, message: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-3), { id, tone, message }]);
    setTimeout(() => dismiss(id), tone === 'error' ? 8000 : 4500);
  }, [dismiss]);
  const value = useMemo(() => ({ push }), [push]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[1200] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2">
        {toasts.map((t) => (
          <div key={t.id} role={t.tone === 'error' ? 'alert' : 'status'}
            className={cn('pointer-events-auto flex items-start gap-2 rounded-md border bg-white px-3 py-2.5 text-sm shadow-lg',
              t.tone === 'success' && 'border-emerald-200', t.tone === 'error' && 'border-rose-200', t.tone === 'info' && 'border-brand-200')}>
            {t.tone === 'success' ? <CheckCircle2 className="mt-0.5 h-4 w-4 text-emerald-600" /> : t.tone === 'error'
              ? <OctagonAlert className="mt-0.5 h-4 w-4 text-rose-600" /> : <Info className="mt-0.5 h-4 w-4 text-brand-600" />}
            <span className="flex-1 text-slate-700">{t.message}</span>
            <button type="button" aria-label="Dismiss" onClick={() => dismiss(t.id)} className="text-slate-400 hover:text-slate-600"><X className="h-4 w-4" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return { success: (m: string) => ctx.push('success', m), error: (m: string) => ctx.push('error', m), info: (m: string) => ctx.push('info', m) };
}
