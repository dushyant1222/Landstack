import type { ReactNode } from 'react';
import { Lock, RefreshCw } from 'lucide-react';
import { Alert, Button, EmptyState, Skeleton } from './ui';
import { toApiError } from '@/lib/api';

/**
 * Standard loading / error / permission-denied / empty handling for API-backed content.
 * Children render only when data is present.
 */
export function QueryState<T>({ query, isEmpty, empty, skeleton, children }: {
  query: { isPending: boolean; isError: boolean; error: unknown; data: T | undefined; refetch: () => unknown };
  isEmpty?: (d: T) => boolean;
  empty?: ReactNode;
  skeleton?: ReactNode;
  children: (data: T) => ReactNode;
}) {
  if (query.isPending) {
    return <>{skeleton ?? <div className="space-y-2 p-4"><Skeleton className="h-4 w-1/3" /><Skeleton className="h-4 w-full" /><Skeleton className="h-4 w-5/6" /></div>}</>;
  }
  if (query.isError) {
    const err = toApiError(query.error);
    if (err.status === 403) {
      return <EmptyState icon={<Lock className="h-5 w-5" />} title="Permission denied">{err.message}</EmptyState>;
    }
    return (
      <div className="p-4">
        <Alert tone="error" title="Could not load data" action={<Button size="sm" variant="secondary" icon={<RefreshCw className="h-3.5 w-3.5" />} onClick={() => query.refetch()}>Retry</Button>}>
          {err.message}
        </Alert>
      </div>
    );
  }
  const data = query.data as T;
  if (isEmpty?.(data)) return <>{empty ?? <EmptyState title="Nothing to show yet" />}</>;
  return <>{children(data)}</>;
}
