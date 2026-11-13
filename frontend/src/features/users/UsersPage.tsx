import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRound, UserPlus, Users } from 'lucide-react';
import { Badge, Button, Card, EmptyState, Field, Input, PageHeader, Pagination, Select } from '@/components/ui';
import { ConfirmDialog, Dialog } from '@/components/Dialog';
import { QueryState } from '@/components/QueryState';
import { useToast } from '@/components/Toast';
import { useAuth } from '@/features/auth/AuthContext';
import { cleanParams, get, post, put, toApiError } from '@/lib/api';
import { useDebounce } from '@/hooks/useDebounce';
import { formatDateTime, ROLE_LABEL } from '@/lib/format';
import type { Page, Role, User } from '@/types/api';

const ROLES: Role[] = ['ADMINISTRATOR', 'DATA_OPERATOR', 'READ_ONLY_REVIEWER'];
const password = z.string().min(10, 'At least 10 characters').max(128).regex(/[A-Za-z]/, 'Include a letter').regex(/\d/, 'Include a digit');
const createSchema = z.object({
  email: z.string().trim().email('Enter a valid email'),
  displayName: z.string().trim().min(1, 'Required').max(120),
  password,
  role: z.enum(['ADMINISTRATOR', 'DATA_OPERATOR', 'READ_ONLY_REVIEWER']),
});
type CreateValues = z.infer<typeof createSchema>;

export function UsersPage() {
  const { user: me } = useAuth();
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const dq = useDebounce(q);
  const [creating, setCreating] = useState(false);
  const [deactivate, setDeactivate] = useState<User | null>(null);
  const [resetFor, setResetFor] = useState<User | null>(null);
  const [newPw, setNewPw] = useState('');
  const qc = useQueryClient();
  const toast = useToast();
  const list = useQuery({
    queryKey: ['users', page, dq, role],
    queryFn: () => get<Page<User>>('/users', cleanParams({ page, size: 25, q: dq, role })),
  });
  const done = (msg: string) => { toast.success(msg); void qc.invalidateQueries({ queryKey: ['users'] }); };
  const fail = (e: unknown) => toast.error(toApiError(e).message);

  const changeRole = useMutation({ mutationFn: (p: { id: string; role: Role }) => put<User>(`/users/${p.id}/role`, { role: p.role }), onSuccess: () => done('Role updated. The user\'s sessions were ended.'), onError: fail });
  const setActive = useMutation({
    mutationFn: (p: { id: string; active: boolean }) => post<User>(`/users/${p.id}/${p.active ? 'activate' : 'deactivate'}`),
    onSuccess: (_d, p) => { setDeactivate(null); done(p.active ? 'Account activated.' : 'Account deactivated and signed out everywhere.'); },
    onError: fail,
  });
  const resetPw = useMutation({
    mutationFn: () => post(`/users/${resetFor!.id}/password`, { newPassword: newPw }),
    onSuccess: () => { setResetFor(null); setNewPw(''); done('Password reset. Share it with the user through a secure channel.'); },
    onError: fail,
  });

  return (
    <>
      <PageHeader title="User management" description="Administrator-provisioned accounts. Roles are enforced by the server on every request."
        actions={<Button icon={<UserPlus className="h-4 w-4" />} onClick={() => setCreating(true)}>Add user</Button>} />
      <Card>
        <div className="flex flex-wrap gap-2 border-b border-slate-100 p-4">
          <Input aria-label="Search users" placeholder="Search name or email…" className="max-w-xs" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          <Select aria-label="Filter by role" className="w-52" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
            <option value="">All roles</option>{ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </Select>
        </div>
        <QueryState query={list} isEmpty={(d) => d.content.length === 0} empty={<EmptyState icon={<Users className="h-5 w-5" />} title="No users found" />}>
          {(d) => (
            <>
              <div className="overflow-x-auto">
                <table className="table-base">
                  <thead><tr><th>User</th><th>Role</th><th>Status</th><th>Last sign-in</th><th /></tr></thead>
                  <tbody>
                    {d.content.map((u) => (
                      <tr key={u.id}>
                        <td><p className="font-medium text-slate-800">{u.displayName}{u.id === me?.id && <Badge className="ml-2" tone="blue">You</Badge>}</p><p className="text-xs text-slate-500">{u.email}</p></td>
                        <td>
                          <Select aria-label={`Role for ${u.displayName}`} className="h-8 w-48 text-xs" value={u.role} disabled={u.id === me?.id || changeRole.isPending}
                            onChange={(e) => changeRole.mutate({ id: u.id, role: e.target.value as Role })}>
                            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                          </Select>
                        </td>
                        <td>{u.active ? <Badge tone="green">Active</Badge> : <Badge>Deactivated</Badge>}</td>
                        <td className="whitespace-nowrap text-xs">{formatDateTime(u.lastLoginAt)}</td>
                        <td className="whitespace-nowrap text-right">
                          <Button size="sm" variant="ghost" icon={<KeyRound className="h-3.5 w-3.5" />} onClick={() => setResetFor(u)}>Reset password</Button>
                          {u.id !== me?.id && (u.active
                            ? <Button size="sm" variant="ghost" className="text-rose-700" onClick={() => setDeactivate(u)}>Deactivate</Button>
                            : <Button size="sm" variant="ghost" onClick={() => setActive.mutate({ id: u.id, active: true })}>Activate</Button>)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={d.page} totalPages={d.totalPages} totalElements={d.totalElements} size={d.size} onPage={setPage} />
            </>
          )}
        </QueryState>
      </Card>
      <CreateUserDialog open={creating} onClose={() => setCreating(false)} onCreated={() => done('User created.')} />
      <ConfirmDialog open={!!deactivate} onClose={() => setDeactivate(null)} danger confirmLabel="Deactivate" loading={setActive.isPending}
        onConfirm={() => deactivate && setActive.mutate({ id: deactivate.id, active: false })} title={`Deactivate ${deactivate?.displayName ?? ''}?`}>
        The user is signed out immediately and cannot sign in until reactivated. Their past actions stay in the audit trail.
      </ConfirmDialog>
      <Dialog open={!!resetFor} onClose={() => setResetFor(null)} title={`Reset password for ${resetFor?.displayName ?? ''}`}
        footer={<><Button variant="secondary" onClick={() => setResetFor(null)}>Cancel</Button>
          <Button disabled={!password.safeParse(newPw).success} loading={resetPw.isPending} onClick={() => resetPw.mutate()}>Reset</Button></>}>
        <Field label="New password" htmlFor="npw" hint="10–128 characters with a letter and a digit. All of the user's sessions end.">
          <Input id="npw" type="password" autoComplete="new-password" value={newPw} onChange={(e) => setNewPw(e.target.value)} />
        </Field>
      </Dialog>
    </>
  );
}

function CreateUserDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm<CreateValues>({
    resolver: zodResolver(createSchema), defaultValues: { role: 'READ_ONLY_REVIEWER' },
  });
  const submit = async (v: CreateValues) => {
    try {
      await post<User>('/users', v);
      reset();
      onCreated();
      onClose();
    } catch (e) {
      const err = toApiError(e);
      if (err.fieldErrors.length) err.fieldErrors.forEach((f) => setError(f.field as keyof CreateValues, { message: f.message }));
      else setError('email', { message: err.message });
    }
  };
  return (
    <Dialog open={open} onClose={onClose} title="Add user" description="Share the initial password through a secure channel; the user can change it under Profile."
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="create-user" loading={isSubmitting}>Create user</Button></>}>
      <form id="create-user" className="space-y-3" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Email" htmlFor="cu-email" error={errors.email?.message}><Input id="cu-email" type="email" {...register('email')} /></Field>
        <Field label="Display name" htmlFor="cu-name" error={errors.displayName?.message}><Input id="cu-name" {...register('displayName')} /></Field>
        <Field label="Initial password" htmlFor="cu-pw" error={errors.password?.message}><Input id="cu-pw" type="password" autoComplete="new-password" {...register('password')} /></Field>
        <Field label="Role" htmlFor="cu-role" error={errors.role?.message}>
          <Select id="cu-role" {...register('role')}>{ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</Select>
        </Field>
      </form>
    </Dialog>
  );
}
