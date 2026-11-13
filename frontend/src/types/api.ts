// TypeScript mirrors of the backend DTOs (edu.iitp.landstack.*Dtos). Keep in sync with docs/api-contract.md.

export type Role = 'ADMINISTRATOR' | 'DATA_OPERATOR' | 'READ_ONLY_REVIEWER';

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  active: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
  traceId?: string;
}

export type ImportStatus =
  | 'RECEIVED' | 'VALIDATING' | 'PARSING' | 'AWAITING_SHEET_SELECTION' | 'AWAITING_MAPPING' | 'PREVIEW_READY'
  | 'CONFIRMING' | 'COMPLETED' | 'COMPLETED_WITH_WARNINGS' | 'FAILED' | 'EXPIRED' | 'CANCELLED' | 'REMOVED';

export type FileType = 'CSV' | 'XLSX' | 'PDF';

export interface ImportJob {
  id: string;
  status: ImportStatus;
  statusCode?: string;
  statusMessage?: string;
  retryable: boolean;
  sourceFileId: string;
  originalFilename: string;
  fileType: FileType;
  sizeBytes: number;
  sha256: string;
  createdBy: string;
  createdByName?: string;
  createdAt: string;
  updatedAt: string;
  duplicateOfJobId?: string;
  sheetNames?: string[];
  selectedSheet?: string;
  headerRow?: number;
  sourceColumns?: string[];
  totalRows?: number;
  validRows?: number;
  warningRows?: number;
  rejectedRows?: number;
  importedRows?: number;
  confirmedAt?: string;
  resultSummary?: {
    rejectedRows?: { row: number; state: string; rules: string[] }[];
    rejectedRowsTruncated?: boolean;
    normalizationVersion?: string;
    rulesVersion?: string;
    qualityIssuesCreated?: number;
  };
  version: number;
}

export interface TimelineEntry {
  at: string;
  kind: 'AUDIT' | 'PROCESSING';
  label: string;
  detail?: string;
  actorName?: string;
}

export interface DiagnosticMessage {
  level: 'INFO' | 'WARNING' | 'ERROR';
  code: string;
  message: string;
  row?: number;
  column?: string;
}

export interface DiagnosticsAttempt {
  attemptId: string;
  stage: string;
  processor: string;
  processorVersion?: string;
  outcome: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'UNAVAILABLE';
  startedAt: string;
  finishedAt?: string;
  details: { messages?: DiagnosticMessage[]; headerRow?: number; headerCertain?: boolean; [k: string]: unknown };
}

export type RowState = 'PENDING' | 'VALID' | 'WARNING' | 'BLOCKED' | 'EXCLUDED';

export interface FieldTrace {
  column?: string;
  raw?: string | null;
  value?: string | null;
  transformation: string;
}

export interface RowIssue {
  ruleId: string;
  field?: string;
  severity: Severity;
  blocking: boolean;
  message: string;
  observed?: string;
}

export interface StagedRow {
  id: string;
  rowNumber: number;
  rawValues: Record<string, string>;
  correctedValues?: Record<string, string>;
  normalized?: Record<string, FieldTrace>;
  validation?: RowIssue[];
  state: RowState;
  excluded: boolean;
  page?: number;
  extractionMethod?: string;
  confidence?: number;
}

export type FieldKind = 'IDENTIFIER' | 'TEXT' | 'DECIMAL' | 'UNIT' | 'CATEGORY' | 'DATE' | 'LATITUDE' | 'LONGITUDE' | 'GEOMETRY';

export interface FieldSuggestion {
  field: string;
  label: string;
  kind: FieldKind;
  status: 'SUGGESTED' | 'AMBIGUOUS' | 'NONE';
  suggestedColumn?: string;
  candidates: string[];
}

export interface MappingSuggestions {
  sourceColumns: string[];
  sampleValues: Record<string, string[]>;
  fields: FieldSuggestion[];
  current?: {
    mapping: Record<string, string>;
    options: Record<string, unknown>;
    normalizationVersion: string;
    templateId?: string;
    templateVersion?: number;
    updatedAt: string;
  };
}

export interface MappingRequest {
  mapping: Record<string, string>;
  dayFirst: boolean;
  declaredAreaUnit?: string;
  declaredState?: string;
  requireIdentifier: boolean;
  requireDistrict: boolean;
  templateId?: string;
}

export interface PreviewSummary {
  totalRows: number;
  validRows: number;
  warningRows: number;
  blockedRows: number;
  excludedRows: number;
  issueCounts: Record<string, number>;
}

export interface MappingTemplate {
  id: string;
  name: string;
  description?: string;
  version: number;
  mapping: Record<string, string>;
  options: Record<string, unknown>;
  createdAt: string;
}

export interface ConfirmResult {
  importJobId: string;
  status: ImportStatus;
  importedRows: number;
  rejectedRows: number;
  warningRows: number;
  qualityIssuesCreated: number;
  replayed: boolean;
}

export interface LandRecord {
  id: string;
  parcelId?: string;
  surveyKhasraNo?: string;
  ownerName?: string;
  villageLocality?: string;
  tehsil?: string;
  district?: string;
  state?: string;
  landAreaValue?: number;
  landAreaUnitRaw?: string;
  landAreaUnit?: string;
  landAreaSqm?: number;
  landTypeRaw?: string;
  landTypeCategory?: string;
  registrationRef?: string;
  registrationDate?: string;
  latitude?: number;
  longitude?: number;
  mappable: boolean;
  recordStatus: 'CONFIRMED' | 'ARCHIVED';
  importJobId: string;
  sourceFileId: string;
  openIssues: number;
  createdAt: string;
}

export interface SourceValue {
  field: string;
  sourceColumn?: string;
  rawValue?: string;
  normalizedValue?: string;
  transformation: string;
  changedByName?: string;
  at: string;
}

export interface LandRecordDetail {
  record: LandRecord;
  geometry?: GeoJSONGeometry;
  archivedReason?: string;
  version: number;
  sourceFile: { id: string; originalFilename: string; type: FileType; uploadedAt: string; sha256: string };
  importJob: { id: string; status: ImportStatus; confirmedAt?: string; confirmedByName?: string };
  provenance: {
    normalizationVersion?: string;
    templateId?: string;
    templateVersion?: number;
    mapping: Record<string, string>;
    options: Record<string, unknown>;
    sourceRowNumber: number;
    sourcePage?: number;
  };
  sourceValues: SourceValue[];
  issues: { id: string; ruleId: string; severity: Severity; status: IssueStatus; field?: string; reason: string }[];
  ownerMasked: boolean;
}

export interface GeoJSONGeometry {
  type: 'Point' | 'Polygon' | 'MultiPolygon';
  coordinates: unknown;
}

export interface Facets {
  districts: string[];
  states: string[];
  landTypes: string[];
}

export interface MapPoint {
  id: string;
  lat: number;
  lon: number;
  parcelId?: string;
  surveyKhasraNo?: string;
  village?: string;
  district?: string;
  landTypeCategory?: string;
  landTypeRaw?: string;
  geometry?: GeoJSONGeometry;
}

export interface MapResponse {
  mappableCount: number;
  notMappableCount: number;
  returned: number;
  truncated: boolean;
  points: MapPoint[];
}

export type Severity = 'INFO' | 'WARNING' | 'HIGH';
export type IssueStatus = 'OPEN' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED';

export interface RecordSummary {
  id: string;
  parcelId?: string;
  surveyKhasraNo?: string;
  ownerName?: string;
  villageLocality?: string;
  district?: string;
  state?: string;
  landAreaValue?: number;
  landAreaUnitRaw?: string;
  landAreaSqm?: number;
  landTypeRaw?: string;
  mappable: boolean;
  recordStatus: string;
  importJobId: string;
}

export interface QualityIssue {
  id: string;
  landRecordId?: string;
  importJobId?: string;
  ruleId: string;
  ruleVersion: string;
  source: 'RULE' | 'STATISTICAL';
  category: string;
  field?: string;
  severity: Severity;
  observedValue?: string;
  reason: string;
  relatedRecordIds: string[];
  status: IssueStatus;
  reviewerName?: string;
  reviewerNote?: string;
  reviewedAt?: string;
  createdAt: string;
  record?: RecordSummary;
}

export interface QualityRule {
  ruleId: string;
  category: string;
  severity: string;
  stage: string;
  description: string;
}

export interface Count { key: string; count: number }

export interface DashboardSummary {
  confirmedRecords: number;
  archivedRecords: number;
  mappableRecords: number;
  notMappableRecords: number;
  totalImports: number;
  importsByStatus: Count[];
  openIssues: number;
  openIssuesBySeverity: Count[];
  openIssuesByCategory: Count[];
  issuesByStatus: Count[];
  recordsByLandType: Count[];
  recordsByDistrict: Count[];
  recordsAddedPerDay: { day: string; count: number }[];
  recentImports: { id: string; filename: string; status: ImportStatus; importedRows?: number; createdAt: string }[];
  recentReviews: { issueId: string; action: string; actorName?: string; at: string }[];
  reviewActivityVisible: boolean;
  from: string;
  to: string;
  district?: string;
  generatedAt: string;
}

export interface AuditEvent {
  id: string;
  actorId?: string;
  actorName?: string;
  actorRole?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  summary: Record<string, unknown>;
  occurredAt: string;
}
