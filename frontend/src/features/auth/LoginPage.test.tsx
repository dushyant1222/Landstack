import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/lib/api';
import { renderWithProviders } from '@/test/utils';
import { LoginPage } from './LoginPage';

function axiosError(status: number, body: object) {
  const headers = new AxiosHeaders();
  return new AxiosError('fail', 'ERR', { headers } as never, null, { status, data: body, statusText: '', headers, config: { headers } as never });
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(api, 'get').mockRejectedValue(axiosError(401, { code: 'AUTH_REQUIRED', message: 'Sign in to continue.' }));
  });

  it('validates required fields before calling the server', async () => {
    const post = vi.spyOn(api, 'post');
    renderWithProviders(<LoginPage />, { route: '/login' });
    await userEvent.click(await screen.findByRole('button', { name: /sign in/i }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('shows the safe server message on invalid credentials', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(axiosError(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' }));
    renderWithProviders(<LoginPage />, { route: '/login' });
    await userEvent.type(await screen.findByLabelText('Email'), 'op@test.local');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password.'));
  });
});
