import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { useAuth } from './AuthContext';
import { EmptyState, Spinner } from '@/components/ui';
import type { Role } from '@/types/api';

export function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="flex h-screen items-center justify-center"><Spinner label="Checking session" /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Usability guard only - the backend rejects unauthorized requests regardless. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) {
    return (
      <EmptyState icon={<Lock className="h-5 w-5" />} title="Permission denied">
        Your role does not have access to this page. Contact an administrator if you need access.
      </EmptyState>
    );
  }
  return <>{children}</>;
}
