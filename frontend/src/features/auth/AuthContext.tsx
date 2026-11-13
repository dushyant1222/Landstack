import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, resetCsrf, toApiError, UNAUTHORIZED_EVENT } from '@/lib/api';
import type { Role, User } from '@/types/api';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Client-side session state. The server-side session (HttpOnly cookie) is authoritative; this only
 * mirrors /auth/me for rendering. Role checks here are for usability; the backend enforces access.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const qc = useQueryClient();

  const refresh = useCallback(async () => {
    try {
      const r = await api.get<User>('/auth/me');
      setUser(r.data);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const onUnauthorized = () => {
      setUser(null);
      qc.clear();
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [qc]);

  const login = useCallback(async (email: string, password: string) => {
    try {
      const r = await api.post<User>('/auth/login', { email, password });
      resetCsrf(); // session changed; fetch a fresh token for the next mutation
      qc.clear();
      setUser(r.data);
      return r.data;
    } catch (e) {
      throw toApiError(e);
    }
  }, [qc]);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      resetCsrf();
      qc.clear();
      setUser(null);
    }
  }, [qc]);

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user]);

  const value = useMemo(() => ({ user, loading, login, logout, refresh, hasRole }), [user, loading, login, logout, refresh, hasRole]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export const CAN_OPERATE: Role[] = ['ADMINISTRATOR', 'DATA_OPERATOR'];
