import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { Layers, LogIn, ShieldCheck } from 'lucide-react';
import { useAuth } from './AuthContext';
import { Alert, Button, Field, Input } from '@/components/ui';
import { toApiError } from '@/lib/api';

const schema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});
type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormValues>({ resolver: zodResolver(schema) });
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (user) return <Navigate to={from} replace />;

  const onSubmit = async (v: FormValues) => {
    setError(null);
    try {
      await login(v.email, v.password);
      navigate(from, { replace: true });
    } catch (e) {
      setError(toApiError(e).message);
    }
  };

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="relative hidden w-[44%] flex-col justify-between overflow-hidden bg-brand-900 p-10 text-white lg:flex">
        <div className="absolute inset-0 opacity-[0.12]" aria-hidden
          style={{ backgroundImage: 'linear-gradient(#adc6e6 1px, transparent 1px), linear-gradient(90deg, #adc6e6 1px, transparent 1px)', backgroundSize: '44px 44px' }} />
        <div className="relative flex items-center gap-2 text-lg font-semibold"><Layers className="h-6 w-6 text-accent-300" /> LandStack</div>
        <div className="relative space-y-4">
          <h1 className="text-3xl font-semibold leading-tight">Land information integration &amp; governance dashboard</h1>
          <p className="max-w-md text-sm text-brand-100">
            Import heterogeneous CSV, Excel and PDF land datasets, map them to one schema with full source lineage,
            explore them on a map and review data-quality flags.
          </p>
        </div>
        <p className="relative flex items-start gap-2 text-xs text-brand-200">
          <ShieldCheck className="h-4 w-4 shrink-0" />
          Academic project using synthetic sample data. LandStack does not verify ownership, title or legal status
          and is not an official land-record system.
        </p>
      </aside>
      <main className="flex flex-1 items-center justify-center p-6">
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="w-full max-w-sm space-y-5" aria-label="Sign in">
          <div className="lg:hidden flex items-center gap-2 text-lg font-semibold text-brand-800"><Layers className="h-6 w-6 text-accent-600" /> LandStack</div>
          <div>
            <h2 className="text-xl font-semibold text-slate-900">Sign in</h2>
            <p className="mt-1 text-sm text-slate-500">Accounts are provisioned by an administrator.</p>
          </div>
          {error && <Alert tone="error">{error}</Alert>}
          <Field label="Email" htmlFor="email" error={errors.email?.message}>
            <Input id="email" type="email" autoComplete="username" invalid={!!errors.email} {...register('email')} />
          </Field>
          <Field label="Password" htmlFor="password" error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="current-password" invalid={!!errors.password} {...register('password')} />
          </Field>
          <Button type="submit" className="w-full" loading={isSubmitting} icon={<LogIn className="h-4 w-4" />}>Sign in</Button>
        </form>
      </main>
    </div>
  );
}
