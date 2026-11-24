/** Typed models for the read-only Document lifecycle screens (Rider_DMS /v1/dms/lifecycle and the central audit trail). Nullable fields may also be missing. */

export interface LifecycleDocumentRow {
  id: string;
  originalFileName?: string | null;
  category?: string | null;
  documentType?: string | null;
  ownerId?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  status?: string | null;
  scanStatus?: string | null;
  legalHold?: boolean | null;
  deleted?: boolean | null;
  retentionUntil?: string | null;
  createdAt?: string | null;
}

export interface LifecycleDocumentDetail extends LifecycleDocumentRow {
  storedFileName?: string | null;
  objectKey?: string | null;
  provider?: string | null;
  versionId?: string | null;
  checksumSha256?: string | null;
  encryption?: string | null;
  scanMessage?: string | null;
  uploadedBy?: string | null;
  deletedAt?: string | null;
  deletedBy?: string | null;
  legalHoldChangedAt?: string | null;
  legalHoldChangedBy?: string | null;
  tenantId?: string | null;
}

/** One step of a document's history, as the timeline shows it (built from a central audit event). */
export interface LifecycleEvent {
  id: string;
  occurredAt?: string | null;
  /** UPLOAD, UPLOAD_REJECTED, SCAN_RESULT ... Unknown future actions are shown as the raw code. */
  action: string;
  status?: string | null;
  reasonCode?: string | null;
  actorId?: string | null;
  actorRole?: string | null;
  correlationId?: string | null;
  storageProvider?: string | null;
  scanVerdict?: string | null;
  sizeBytes?: number | null;
  durationMs?: number | null;
}

export interface LifecyclePage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** CommonResponseObject envelope of Rider_AuditService. */
export interface AuditEnvelope<T> {
  success?: boolean;
  message?: string | null;
  responseObject?: T | null;
}

/** An audit event as Rider_AuditService returns it (only the fields this screen reads). */
export interface AuditEventRaw {
  id: number | string;
  eventId?: string | null;
  eventType?: string | null;
  occurredAt?: string | null;
  userId?: string | null;
  role?: string | null;
  outcome?: string | null;
  durationMs?: number | null;
  correlationId?: string | null;
  reasonCode?: string | null;
  subjectType?: string | null;
  subjectId?: string | null;
  details?: Record<string, string> | null;
}

/** Spring-style page of the audit service. */
export interface AuditPageRaw<T> {
  content?: T[] | null;
  totalElements?: number | null;
  totalPages?: number | null;
  size?: number | null;
  number?: number | null;
}

/** Why the timeline could not be shown: no audit privilege (note, not an error) or a real failure. */
export type TimelineError = 'forbidden' | 'failed';

/** Filters of the documents search (dates are yyyy-MM-dd as the date inputs give them). */
export interface LifecycleDocumentFilter {
  query: string;
  ownerId: string;
  category: string;
  documentType: string;
  status: string;
  scanStatus: string;
  includeDeleted: boolean;
  from: string;
  to: string;
}

export type LifecycleTone = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

export interface LifecycleActionVisual {
  icon: string;
  tone: LifecycleTone;
  /** i18n key of the label; null for an unknown action (the raw code is shown). */
  labelKey: string | null;
}
