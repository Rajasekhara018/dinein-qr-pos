import { HqDocument } from 'hq-shared-lib';
import { LIFECYCLE_IMAGE_PREFIX, LIFECYCLE_MAX_VERSIONS, LIFECYCLE_PDF_TYPE } from './lifecycle.constants';
import { LifecycleDocumentDetail } from './lifecycle.models';

/** One version of a document as the preview shows it: the stored document and whether it is the one this page is about. */
export interface DocumentVersion {
  doc: HqDocument;
  current: boolean;
}

/** How a file can be shown inline. */
export type PreviewKind = 'image' | 'pdf' | 'other';

export function previewKind(contentType: string | null | undefined): PreviewKind {
  const type = (contentType ?? '').toLowerCase();
  if (type.startsWith(LIFECYCLE_IMAGE_PREFIX)) return 'image';
  return type === LIFECYCLE_PDF_TYPE ? 'pdf' : 'other';
}

/** The page's document in the shape DMS's file calls take (only id, name and content type are needed to open it). */
export function asDocument(detail: LifecycleDocumentDetail): HqDocument {
  return {
    id: detail.id, category: detail.category ?? '', ownerId: detail.ownerId ?? '', originalFileName: detail.originalFileName ?? detail.id,
    storedFileName: detail.storedFileName ?? '', publicUrl: null, provider: detail.provider ?? '', versionId: detail.versionId ?? null,
    checksumSha256: detail.checksumSha256 ?? null, contentType: detail.contentType ?? '', sizeBytes: detail.sizeBytes ?? 0,
    status: detail.status ?? '', scanStatus: detail.scanStatus ?? '', scanMessage: detail.scanMessage ?? null,
    retentionUntil: detail.retentionUntil ?? null, legalHold: !!detail.legalHold, createdAt: detail.createdAt ?? '',
  };
}

/**
 * The versions of a document: every upload of the same owner and category, newest first, with the page's document marked (and
 * added when the list does not include it, e.g. it was deleted), capped at LIFECYCLE_MAX_VERSIONS.
 */
export function versionsOf(detail: LifecycleDocumentDetail, uploads: readonly HqDocument[]): DocumentVersion[] {
  const all = uploads.some((d) => d.id === detail.id) ? [...uploads] : [asDocument(detail), ...uploads];
  return all
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
    .slice(0, LIFECYCLE_MAX_VERSIONS)
    .map((doc) => ({ doc, current: doc.id === detail.id }));
}
