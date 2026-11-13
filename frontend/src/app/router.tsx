import { createBrowserRouter, Link } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { RequireAuth, RequireRole } from '@/features/auth/RequireAuth';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProfilePage } from '@/features/auth/ProfilePage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { UploadPage } from '@/features/imports/UploadPage';
import { ImportHistoryPage } from '@/features/imports/ImportHistoryPage';
import { ImportDetailPage } from '@/features/imports/ImportDetailPage';
import { LandRecordsPage } from '@/features/land-records/LandRecordsPage';
import { RecordDetailPage } from '@/features/land-records/RecordDetailPage';
import { MapPage } from '@/features/maps/MapPage';
import { QualityPage } from '@/features/quality-issues/QualityPage';
import { UsersPage } from '@/features/users/UsersPage';
import { AuditPage } from '@/features/audit/AuditPage';
import { EmptyState } from '@/components/ui';

function NotFound() {
  return <EmptyState title="Page not found">The page does not exist. <Link className="text-brand-700 underline" to="/">Go to dashboard</Link></EmptyState>;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [{
      element: <AppShell />,
      children: [
        { index: true, element: <DashboardPage /> },
        { path: 'imports/new', element: <RequireRole roles={['ADMINISTRATOR', 'DATA_OPERATOR']}><UploadPage /></RequireRole> },
        { path: 'imports', element: <ImportHistoryPage /> },
        { path: 'imports/:id', element: <ImportDetailPage /> },
        { path: 'records', element: <LandRecordsPage /> },
        { path: 'records/:id', element: <RecordDetailPage /> },
        { path: 'map', element: <MapPage /> },
        { path: 'quality', element: <QualityPage /> },
        { path: 'audit', element: <RequireRole roles={['ADMINISTRATOR', 'DATA_OPERATOR']}><AuditPage /></RequireRole> },
        { path: 'users', element: <RequireRole roles={['ADMINISTRATOR']}><UsersPage /></RequireRole> },
        { path: 'profile', element: <ProfilePage /> },
        { path: '*', element: <NotFound /> },
      ],
    }],
  },
]);
