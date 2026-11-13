import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { api } from '@/lib/api';
import { renderWithProviders } from '@/test/utils';
import { RequireAuth, RequireRole } from './RequireAuth';

function me(role: string | null) {
  vi.spyOn(api, "get").mockImplementation(() => role
    ? Promise.resolve({ data: { id: '1', email: 'x@test.local', displayName: 'X', role, active: true, createdAt: '' } } as never)
    : Promise.reject(new Error('401')));
}

describe('route guards', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('redirects unauthenticated users to login', async () => {
    me(null);
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<p>login page</p>} />
        <Route element={<RequireAuth />}><Route path="/" element={<p>secret</p>} /></Route>
      </Routes>);
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('shows permission denied for a reviewer on an admin page', async () => {
    me('READ_ONLY_REVIEWER');
    renderWithProviders(
      <Routes><Route element={<RequireAuth />}><Route path="/" element={<RequireRole roles={['ADMINISTRATOR']}><p>admin</p></RequireRole>} /></Route></Routes>);
    expect(await screen.findByText('Permission denied')).toBeInTheDocument();
    expect(screen.queryByText('admin')).not.toBeInTheDocument();
  });
});
