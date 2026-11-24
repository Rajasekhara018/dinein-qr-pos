import { Inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, switchMap } from 'rxjs';
import { HQ_CONFIG, HqSharedLibConfig, hqUrls } from './hq-common.config';
import { HqCommonResponse, HqDocument, HqDownloadUrl } from './hq-common.models';
import { saveBlob } from './hq-file.util';

/**
 * Rider_DMS client for the hq-* upload components (tou's TouPostService.postFile/doGetFile).
 * Every DMS path -- file bytes included -- requires the user's Bearer token at the gateway, so
 * files are always fetched through HttpClient (the app's auth interceptor adds the token) as a
 * Blob; plain <img src>/<a href> to a DMS URL would 401.
 */
@Injectable({ providedIn: 'root' })
export class HqDocumentService {
  private readonly base: string;

  constructor(private http: HttpClient, @Inject(HQ_CONFIG) config: HqSharedLibConfig) {
    this.base = hqUrls(config).dms;
  }

  /**
   * POST multipart {file, category, ownerId, checksum, documentType}. DMS takes one file per call. With a `documentType` (a
   * document type code of the organization) DMS checks the file against that type's rules: formats, size, scan, retention.
   */
  upload(file: File, category: string, ownerId: string, checksum?: string, documentType?: string | null): Observable<HqDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    form.append('ownerId', ownerId);
    if (checksum) form.append('checksum', checksum);
    if (documentType) form.append('documentType', documentType);
    return this.http.post<HqCommonResponse<HqDocument>>(this.base, form).pipe(map((r) => this.unwrap(r)));
  }

  /** Every live document filed under category+owner, newest first. */
  list(category: string, ownerId: string): Observable<HqDocument[]> {
    const params = new HttpParams().set('category', category).set('ownerId', ownerId);
    return this.http.get<HqCommonResponse<HqDocument[]>>(`${this.base}/metadata`, { params })
      .pipe(map((r) => this.unwrap(r) ?? []));
  }

  getMetadata(id: string): Observable<HqDocument> {
    return this.http.get<HqCommonResponse<HqDocument>>(`${this.base}/metadata/${id}`).pipe(map((r) => this.unwrap(r)));
  }

  /** Soft-deletes the document and its stored object (204, no body). */
  delete(id: string): Observable<void> {
    return this.http.delete(`${this.base}/metadata/${id}`).pipe(map(() => undefined));
  }

  /** The file bytes, re-typed with the document's real content type (DMS always answers
   *  application/octet-stream, which browsers won't render as an image or PDF). */
  fetchFile(doc: HqDocument): Observable<Blob> {
    return this.http.post<HqCommonResponse<HqDownloadUrl>>(`${this.base}/metadata/${doc.id}/download-url`, {})
      .pipe(
        switchMap((r) => this.http.get(this.resolveUrl(this.unwrap(r).url), { responseType: 'blob' })),
        map((blob) => new Blob([blob], { type: doc.contentType || blob.type })),
      );
  }

  /** Browser download of the document under its original file name. */
  download(doc: HqDocument): Observable<void> {
    return this.fetchFile(doc).pipe(map((blob) => saveBlob(blob, doc.originalFileName)));
  }

  /** SHA-256 hex of the file -- sent as DMS's optional `checksum`, which DMS verifies. */
  async sha256(file: File): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
    return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  /** DMS returns backend-relative paths (/api/v1/documents/...) for local storage; map them onto
   *  the gateway's dms route. Absolute (S3/MinIO presigned) URLs are used as-is. */
  private resolveUrl(url: string): string {
    if (/^https?:\/\//i.test(url)) return url;
    const backendPrefix = '/api/v1/documents';
    if (url.startsWith(backendPrefix)) return this.base + url.substring(backendPrefix.length);
    return url;
  }

  private unwrap<T>(res: HqCommonResponse<T>): T {
    if (!res?.success) {
      throw { error: { message: res?.message || 'Document request failed' } };
    }
    return res.responseObject as T;
  }
}
