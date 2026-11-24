import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { LifecycleActionVisual, LifecycleDocumentRow } from './lifecycle.models';

/** Stable screen keys for the audit trail (lower-case, never renamed once released). */
export const LIFECYCLE_SCREEN_KEYS = {
  lifecycle: 'document_management.lifecycle',
  lifecycleDetail: 'document_management.lifecycle_detail',
} as const;

export const LIFECYCLE_PATH = '/v1/dms/lifecycle';
export const LIFECYCLE_ROUTE = '/document-management/lifecycle';
/** Gateway path (below environment.gatewayUrl) of the central audit trail's event search. */
export const AUDIT_EVENTS_PATH = '/v1/audit/events';

/** Where the failed uploads are looked at: the Audit logs screen (Rider_AdminWeb remote), Documents tab, failures only. */
export const AUDIT_FAILED_UPLOADS_ROUTE = '/admin/audit';
export const AUDIT_FAILED_UPLOADS_QUERY: Readonly<Record<string, string>> = { tab: 'DOCUMENTS', outcome: 'FAILURE' };
export const LIFECYCLE_PERMISSION = 'view_document_lifecycle';

export const LIFECYCLE_PRIVILEGES = {
  read: 'DMS_DOCUMENT_LIFECYCLE_READ',
  /** Needed to read the history (and to open the Audit logs screen) from the central audit trail. */
  audit: 'AUDIT_EVENT_LIST',
  /** Needed to open a document (preview) through a short-lived download link. */
  download: 'DMS_DOCUMENT_DOWNLOAD_URL',
  /** Needed to list the other uploads of the same owner and category (the versions). */
  listVersions: 'DMS_DOCUMENT_METADATA_LIST',
} as const;

/** At most this many versions are shown (newest first); older ones stay reachable through the list screen. */
export const LIFECYCLE_MAX_VERSIONS = 24;

/** Width of the preview dialog. */
export const LIFECYCLE_PREVIEW_DIALOG_WIDTH = '1000px';

/** A file the preview can show inline: an image, or a PDF in the browser's viewer. */
export const LIFECYCLE_IMAGE_PREFIX = 'image/';
export const LIFECYCLE_PDF_TYPE = 'application/pdf';

export const LIFECYCLE_DEFAULT_PAGE_SIZE = 20;
/** The API caps a page at 100. */
export const LIFECYCLE_MAX_PAGE_SIZE = 100;
export const LIFECYCLE_PAGE_SIZE_OPTIONS: number[] = [10, 20, 50, 100];
export const LIFECYCLE_EVENTS_PAGE_SIZE = 20;

const ACTION_PREFIX = 'documentManagement.lifecycle.actions.';

/** How each known action looks on the timeline. */
export const LIFECYCLE_ACTION_VISUALS: Readonly<Record<string, LifecycleActionVisual>> = {
  UPLOAD: { icon: 'bi-cloud-arrow-up', tone: 'success', labelKey: ACTION_PREFIX + 'UPLOAD' },
  UPLOAD_REJECTED: { icon: 'bi-x-octagon', tone: 'danger', labelKey: ACTION_PREFIX + 'UPLOAD_REJECTED' },
  SCAN_RESULT: { icon: 'bi-shield-check', tone: 'info', labelKey: ACTION_PREFIX + 'SCAN_RESULT' },
  DOWNLOAD: { icon: 'bi-cloud-arrow-down', tone: 'neutral', labelKey: ACTION_PREFIX + 'DOWNLOAD' },
  DOWNLOAD_URL_ISSUED: { icon: 'bi-link-45deg', tone: 'neutral', labelKey: ACTION_PREFIX + 'DOWNLOAD_URL_ISSUED' },
  DELETE: { icon: 'bi-trash3', tone: 'warning', labelKey: ACTION_PREFIX + 'DELETE' },
  LEGAL_HOLD: { icon: 'bi-lock', tone: 'warning', labelKey: ACTION_PREFIX + 'LEGAL_HOLD' },
  PURGE: { icon: 'bi-eraser', tone: 'danger', labelKey: ACTION_PREFIX + 'PURGE' },
};

export const LIFECYCLE_UNKNOWN_ACTION_VISUAL: LifecycleActionVisual = { icon: 'bi-circle', tone: 'neutral', labelKey: null };

/** Status words (uppercase) by how they should be coloured. */
export const LIFECYCLE_SUCCESS_STATUSES: ReadonlySet<string> = new Set(['SUCCESS', 'SUCCEEDED', 'OK', 'CLEAN', 'ACTIVE', 'ACCEPTED', 'AVAILABLE', 'UPLOADED']);
export const LIFECYCLE_DANGER_STATUSES: ReadonlySet<string> = new Set(['FAILED', 'FAILURE', 'ERROR', 'REJECTED', 'INFECTED', 'DENIED', 'QUARANTINED']);
export const LIFECYCLE_WARNING_STATUSES: ReadonlySet<string> = new Set(['PENDING', 'SKIPPED', 'DELETED', 'UNAVAILABLE', 'EXPIRED']);

/** Document status values offered in the filter (labels fall back to the raw code). */
export const LIFECYCLE_DOCUMENT_STATUSES: readonly string[] = ['ACTIVE', 'PENDING', 'QUARANTINED', 'REJECTED', 'DELETED'];
export const LIFECYCLE_SCAN_STATUSES: readonly string[] = ['PENDING', 'CLEAN', 'INFECTED', 'FAILED', 'SKIPPED'];

/** Translated labels of status / scan status codes. A code not listed is shown as it is. */
export const LIFECYCLE_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  ACTIVE: 'documentManagement.lifecycle.statuses.ACTIVE',
  PENDING: 'documentManagement.lifecycle.statuses.PENDING',
  QUARANTINED: 'documentManagement.lifecycle.statuses.QUARANTINED',
  REJECTED: 'documentManagement.lifecycle.statuses.REJECTED',
  DELETED: 'documentManagement.lifecycle.statuses.DELETED',
  CLEAN: 'documentManagement.lifecycle.statuses.CLEAN',
  INFECTED: 'documentManagement.lifecycle.statuses.INFECTED',
  FAILED: 'documentManagement.lifecycle.statuses.FAILED',
  SKIPPED: 'documentManagement.lifecycle.statuses.SKIPPED',
  SUCCESS: 'documentManagement.lifecycle.statuses.SUCCESS',
  FAILURE: 'documentManagement.lifecycle.statuses.FAILURE',
};

/** Plain-language explanation of rejection reason codes. A code not listed is shown as it is. */
export const LIFECYCLE_REASON_LABEL_KEYS: Readonly<Record<string, string>> = {
  FILE_TOO_LARGE: 'documentManagement.lifecycle.reasons.FILE_TOO_LARGE',
  FORMAT_NOT_ALLOWED: 'documentManagement.lifecycle.reasons.FORMAT_NOT_ALLOWED',
  EXTENSION_MISMATCH: 'documentManagement.lifecycle.reasons.EXTENSION_MISMATCH',
  EMPTY_FILE: 'documentManagement.lifecycle.reasons.EMPTY_FILE',
  UNKNOWN_DOCUMENT_TYPE: 'documentManagement.lifecycle.reasons.UNKNOWN_DOCUMENT_TYPE',
  DOCUMENT_TYPE_INACTIVE: 'documentManagement.lifecycle.reasons.DOCUMENT_TYPE_INACTIVE',
  VIRUS_DETECTED: 'documentManagement.lifecycle.reasons.VIRUS_DETECTED',
  SCAN_UNAVAILABLE: 'documentManagement.lifecycle.reasons.SCAN_UNAVAILABLE',
  STORAGE_ERROR: 'documentManagement.lifecycle.reasons.STORAGE_ERROR',
};

export const BYTE_UNITS: readonly string[] = ['B', 'KB', 'MB', 'GB', 'TB'];

export const MS_PER_SECOND = 1000;
export const SECONDS_PER_MINUTE = 60;
export const MINUTES_PER_HOUR = 60;
export const HOURS_PER_DAY = 24;
/** Beyond this many days a relative time is replaced by the plain date. */
export const RELATIVE_TIME_MAX_DAYS = 30;

/** Columns of the document lifecycle list: translation keys of the headings; cells are drawn by the screen. */
export const LIFECYCLE_DOCUMENT_COLUMNS: readonly TableColumnSpec<LifecycleDocumentRow>[] = [
  { key: 'file', labelKey: 'documentManagement.lifecycle.columns.file' },
  { key: 'documentType', labelKey: 'documentManagement.lifecycle.columns.documentType' },
  { key: 'ownerId', labelKey: 'documentManagement.lifecycle.columns.owner' },
  { key: 'size', labelKey: 'documentManagement.lifecycle.columns.size' },
  { key: 'status', labelKey: 'documentManagement.lifecycle.columns.status' },
  { key: 'scan', labelKey: 'documentManagement.lifecycle.columns.scan' },
  { key: 'uploaded', labelKey: 'documentManagement.lifecycle.columns.uploaded' },
];
