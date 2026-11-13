import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { KeyRound } from 'lucide-react';
import { useAuth } from './AuthContext';
import { Alert, Badge, Button, Card, CardBody, CardHeader, Field, Input, KeyValue, PageHeader } from '@/components/ui';
import { post, toApiError } from '@/lib/api';
import { formatDateTime, ROLE_LABEL } from '@/lib/format';
import { useToast } from '@/components/Toast';

const schema = z.object({
  currentPassword: z.string().min(1, 'Required'),
  newPassword: z.string().min(10, 'At least 10 characters').max(128)
    .regex(/[A-Za-z]/, 'Include a letter').regex(/\d/, 'Include a digit'),
  confirm: z.string(),
}).refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });
type FormValues = z.infer<typeof schema>;

export function ProfilePage() {
  const { user, refresh } = useAuth();
  const toast = useToast();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) });
  const change = useMutation({
    mutationFn: (v: FormValues) => post('/auth/password', { currentPassword: v.currentPassword, newPassword: v.newPassword }),
    onSuccess: async () => {
      reset();
      toast.success('Password changed. Please sign in again.');
      await refresh();
    },
    onError: (e) => {
      const err = toApiError(e);
      err.fieldErrors.forEach((f) => setError(f.field as keyof FormValues, { message: f.message }));
      if (!err.fieldErrors.length) toast.error(err.message);
    },
  });
  if (!user) return null;
  return (
    <>
      <PageHeader title="Profile & settings" description="Your account details. Roles are assigned by an administrator." />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader title="Account" />
          <CardBody>
            <KeyValue items={[
              { label: 'Name', value: user.displayName },
              { label: 'Email', value: user.email },
              { label: 'Role', value: <Badge tone="blue">{ROLE_LABEL[user.role]}</Badge> },
              { label: 'Last sign-in', value: formatDateTime(user.lastLoginAt) },
            ]} />
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Change password" icon={<KeyRound className="h-4 w-4" />} subtitle="All your sessions end after a password change." />
          <CardBody>
            <form className="space-y-3" onSubmit={handleSubmit((v) => change.mutate(v))} noValidate>
              <Field label="Current password" htmlFor="cp" error={errors.currentPassword?.message}>
                <Input id="cp" type="password" autoComplete="current-password" {...register('currentPassword')} />
              </Field>
              <Field label="New password" htmlFor="np" error={errors.newPassword?.message} hint="10–128 characters with at least one letter and one digit.">
                <Input id="np" type="password" autoComplete="new-password" {...register('newPassword')} />
              </Field>
              <Field label="Confirm new password" htmlFor="cf" error={errors.confirm?.message}>
                <Input id="cf" type="password" autoComplete="new-password" {...register('confirm')} />
              </Field>
              <Button type="submit" loading={change.isPending}>Update password</Button>
            </form>
          </CardBody>
        </Card>
        <Alert tone="info" title="About LandStack" className="lg:col-span-2">
          LandStack standardises and reviews sample land datasets for academic purposes. Quality flags indicate potential
          issues for human review only; they are not legal findings, and LandStack does not verify ownership, title,
          registration or parcel boundaries.
        </Alert>
      </div>
    </>
  );
}
