import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { ClipboardList, FileUp, History, LayoutDashboard, Layers, LogOut, Map, Menu, ScrollText, ShieldAlert, Table2, UserCog, Users, X } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { cn } from '@/lib/cn';
import { ROLE_LABEL } from '@/lib/format';
import type { Role } from '@/types/api';

interface NavItem { to: string; label: string; icon: typeof Map; roles?: Role[] }

export const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/imports/new', label: 'Import datasets', icon: FileUp, roles: ['ADMINISTRATOR', 'DATA_OPERATOR'] },
  { to: '/imports', label: 'Import history', icon: History },
  { to: '/records', label: 'Land records', icon: Table2 },
  { to: '/map', label: 'Map view', icon: Map },
  { to: '/quality', label: 'Data quality', icon: ShieldAlert },
  { to: '/audit', label: 'Audit trail', icon: ScrollText, roles: ['ADMINISTRATOR', 'DATA_OPERATOR'] },
  { to: '/users', label: 'User management', icon: Users, roles: ['ADMINISTRATOR'] },
  { to: '/profile', label: 'Profile & settings', icon: UserCog },
];

export function visibleNav(role?: Role): NavItem[] {
  return NAV.filter((n) => !n.roles || (role && n.roles.includes(role)));
}

function NavList({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  return (
    <nav aria-label="Main" className="space-y-0.5 px-3">
      {visibleNav(user?.role).map((n) => (
        <NavLink key={n.to} to={n.to} end={n.to === '/' || n.to === '/imports'} onClick={onNavigate}
          className={({ isActive }) => cn('flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
            isActive ? 'bg-white/10 text-white' : 'text-brand-100 hover:bg-white/5 hover:text-white')}>
          <n.icon className="h-4 w-4 shrink-0" aria-hidden /> {n.label}
        </NavLink>
      ))}
    </nav>
  );
}

function Brand() {
  return (
    <div className="flex items-center gap-2 px-6 py-5 text-white">
      <Layers className="h-6 w-6 text-accent-300" aria-hidden />
      <div>
        <p className="text-base font-semibold leading-none">LandStack</p>
        <p className="mt-1 text-[0.65rem] uppercase tracking-wider text-brand-200">Land data governance</p>
      </div>
    </div>
  );
}

export function AppShell() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);
  const title = NAV.find((n) => n.to !== '/' && location.pathname.startsWith(n.to))?.label ?? 'Dashboard';

  return (
    <div className="min-h-screen bg-slate-50">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">Skip to content</a>
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col bg-brand-900 lg:flex">
        <Brand />
        <div className="flex-1 overflow-y-auto py-2"><NavList /></div>
        <p className="px-6 py-4 text-[0.68rem] leading-relaxed text-brand-300">Synthetic/authorised sample data only. Not an official land-record system.</p>
      </aside>

      {open && (
        <div className="fixed inset-0 z-[1050] lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-72 flex-col bg-brand-900">
            <div className="flex items-center justify-between pr-3"><Brand />
              <button aria-label="Close navigation" className="rounded p-2 text-brand-100 hover:bg-white/10" onClick={() => setOpen(false)}><X className="h-5 w-5" /></button>
            </div>
            <div className="flex-1 overflow-y-auto py-2"><NavList onNavigate={() => setOpen(false)} /></div>
          </aside>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-[1000] flex h-14 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button aria-label="Open navigation" className="rounded p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setOpen(true)}><Menu className="h-5 w-5" /></button>
          <div className="flex min-w-0 items-center gap-2 text-sm text-slate-500">
            <ClipboardList className="hidden h-4 w-4 sm:block" aria-hidden />
            <span className="truncate font-medium text-slate-800">{title}</span>
          </div>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium leading-tight text-slate-800">{user?.displayName}</p>
              <p className="text-xs text-slate-500">{user ? ROLE_LABEL[user.role] : ''}</p>
            </div>
            <button onClick={() => void logout()} className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50">
              <LogOut className="h-3.5 w-3.5" /> Sign out
            </button>
          </div>
        </header>
        <main id="main" className="mx-auto max-w-7xl px-4 py-6 sm:px-6"><Outlet /></main>
      </div>
    </div>
  );
}
