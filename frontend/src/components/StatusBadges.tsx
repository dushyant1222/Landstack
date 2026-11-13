import { Badge } from './ui';
import type { ImportStatus, IssueStatus, RowState, Severity } from '@/types/api';
import { humanize } from '@/lib/format';

const IMPORT_TONE: Record<ImportStatus, Parameters<typeof Badge>[0]['tone']> = {
  RECEIVED: 'slate', VALIDATING: 'blue', PARSING: 'blue', AWAITING_SHEET_SELECTION: 'amber', AWAITING_MAPPING: 'amber',
  PREVIEW_READY: 'violet', CONFIRMING: 'blue', COMPLETED: 'green', COMPLETED_WITH_WARNINGS: 'teal', FAILED: 'red',
  EXPIRED: 'slate', CANCELLED: 'slate', REMOVED: 'slate',
};

export function ImportStatusBadge({ status }: { status: ImportStatus }) {
  return <Badge tone={IMPORT_TONE[status]}>{humanize(status)}</Badge>;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <Badge tone={severity === 'HIGH' ? 'red' : severity === 'WARNING' ? 'amber' : 'slate'}>{humanize(severity)}</Badge>;
}

export function IssueStatusBadge({ status }: { status: IssueStatus }) {
  return <Badge tone={status === 'OPEN' ? 'amber' : status === 'RESOLVED' ? 'green' : status === 'DISMISSED' ? 'slate' : 'blue'}>{humanize(status)}</Badge>;
}

export function RowStateBadge({ state }: { state: RowState }) {
  const tone = state === 'VALID' ? 'green' : state === 'WARNING' ? 'amber' : state === 'BLOCKED' ? 'red' : 'slate';
  const label = state === 'BLOCKED' ? 'Blocked' : state === 'EXCLUDED' ? 'Excluded' : humanize(state);
  return <Badge tone={tone}>{label}</Badge>;
}

export function MappableBadge({ mappable }: { mappable: boolean }) {
  return mappable ? <Badge tone="teal">Mappable</Badge> : <Badge tone="slate" title="No valid source coordinates; not shown on the map">Not mappable</Badge>;
}

export function SourceTag() {
  return <Badge tone="slate" title="Value as supplied in the source file">Source</Badge>;
}

export function SystemTag() {
  return <Badge tone="blue" title="Value produced by LandStack normalization">Normalized</Badge>;
}
