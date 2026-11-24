import { HqDocument } from './hq-common.models';

/** True when the file matches an accept list like '.pdf,.png,image/*,application/pdf'. */
export function isAcceptedFile(file: File, accept: string): boolean {
  const tokens = (accept || '').split(',').map((t) => t.trim().toLowerCase()).filter(Boolean);
  if (!tokens.length) return true;
  const name = file.name.toLowerCase();
  const type = (file.type || '').toLowerCase();
  return tokens.some((token) => {
    if (token.startsWith('.')) return name.endsWith(token);
    if (token.endsWith('/*')) return type.startsWith(token.slice(0, -1));
    return type === token;
  });
}

/** Triggers a browser download of a Blob under the given name. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName || 'document';
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function formatSize(bytes: number | null | undefined): string {
  if (!bytes) return '';
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/**
 * A document record for a file an application stored itself (not in DMS), for hq-doc-upload's uploader when the endpoint answers
 * only an id: the name, type and size come from the file, the rest stays empty.
 */
export function hqDocumentFromFile(file: File, id: string, category = '', ownerId = ''): HqDocument {
  return {
    id, category, ownerId, originalFileName: file.name, storedFileName: file.name, publicUrl: null, provider: 'APPLICATION',
    versionId: null, checksumSha256: null, contentType: file.type || 'application/octet-stream', sizeBytes: file.size,
    status: 'ACTIVE', scanStatus: 'NOT_SCANNED', scanMessage: null, retentionUntil: null, legalHold: false,
    createdAt: new Date().toISOString(),
  };
}

/**
 * A document record for a file an application stores itself (not in DMS) and lists by id, for the upload components' value: the
 * screen knows the id and gives the name to show, the type (so a picture shows as one) and the size when it has it (0 when not).
 */
export function hqStoredDocument(id: string, name: string, contentType: string, sizeBytes = 0): HqDocument {
  return {
    id, category: '', ownerId: '', originalFileName: name, storedFileName: name, publicUrl: null, provider: 'APPLICATION',
    versionId: null, checksumSha256: null, contentType, sizeBytes, status: 'ACTIVE', scanStatus: 'NOT_SCANNED', scanMessage: null,
    retentionUntil: null, legalHold: false, createdAt: '',
  };
}
