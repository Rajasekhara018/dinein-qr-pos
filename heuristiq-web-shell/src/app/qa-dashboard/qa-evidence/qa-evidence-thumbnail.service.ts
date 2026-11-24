import { Injectable, OnDestroy, inject } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { HqDocumentService } from 'hq-shared-lib';
import { Observable, defer, of } from 'rxjs';
import { finalize, map, shareReplay, switchMap, tap } from 'rxjs/operators';

import { QA_THUMBNAIL } from './qa-evidence.constants';
import { isValidEvidenceId } from './qa-evidence.helpers';

/** One picture kept for the page: its Blob object URL, the same URL marked as safe, and how many thumbnails show it now. */
interface ThumbnailEntry {
  url: string;
  safe: SafeUrl;
  uses: number;
}

/**
 * Loads the screenshots shown as previews on one page. It is provided by the screen that shows them (not by the root), so
 * its limits and its cache belong to that page:
 * <ul>
 *   <li>at most {@link QA_THUMBNAIL.maxConcurrent} downloads run at the same time, the rest wait their turn;</li>
 *   <li>a screenshot is downloaded once and cached by its document id; a second ask while it is coming shares the download;</li>
 *   <li>a file larger than {@link QA_THUMBNAIL.maxBytes}, or that is not an image, is not loaded: the answer is {@code null};</li>
 *   <li>the picture is handed out only as a Blob object URL created here, and every one is revoked when the page goes;</li>
 *   <li>memory stays bounded on a long list (a run with hundreds of passed rows): at most {@link QA_THUMBNAIL.maxObjectUrls}
 *       object URLs are alive at once. A thumbnail that has scrolled far away gives its picture back with {@link release};
 *       when more than the cap are alive, the least recently used ones that nobody shows ({@code uses === 0}) are revoked
 *       and downloaded again if they come back. A picture that is on screen is never revoked, so the cap can only be
 *       passed by pictures in use, which is a handful (the ones near the viewport).</li>
 * </ul>
 * This is the only place that marks a URL as safe, and only for a {@code blob:} URL that this class has just created from
 * downloaded bytes; nothing from the server is ever turned into a URL or into HTML.
 */
@Injectable()
export class QaEvidenceThumbnailService implements OnDestroy {
  private readonly documents = inject(HqDocumentService);
  private readonly sanitizer = inject(DomSanitizer);

  /** Insertion order is use order: the first entry is the least recently used. */
  private readonly entries = new Map<string, ThumbnailEntry>();
  private readonly inflight = new Map<string, Observable<SafeUrl | null>>();
  private readonly waiting: Array<() => void> = [];
  private active = 0;
  private destroyed = false;

  /**
   * The preview of a screenshot as a URL for an image, or {@code null} when it cannot or should not be shown inline.
   * Every URL handed out counts as in use until the same thumbnail calls {@link release} for it.
   */
  load(documentId: string): Observable<SafeUrl | null> {
    if (this.destroyed || !isValidEvidenceId(documentId)) return of(null);
    return this.source(documentId).pipe(
      tap((safe) => {
        if (safe) {
          this.retain(documentId);
          this.evict();
        }
      }),
    );
  }

  /** The thumbnail stopped showing this picture (it scrolled far away or went); it may now be evicted when memory is needed. */
  release(documentId: string): void {
    const entry = this.entries.get(documentId);
    if (!entry) return;
    entry.uses = Math.max(0, entry.uses - 1);
    this.evict();
  }

  /** How many object URLs are alive now (for the cap's tests and for diagnostics). */
  get aliveCount(): number {
    return this.entries.size;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.entries.forEach((entry) => URL.revokeObjectURL(entry.url));
    this.entries.clear();
    this.inflight.clear();
    this.waiting.length = 0;
  }

  private source(documentId: string): Observable<SafeUrl | null> {
    const cached = this.entries.get(documentId);
    if (cached) return of(cached.safe);
    let pending = this.inflight.get(documentId);
    if (!pending) {
      pending = this.download(documentId).pipe(
        finalize(() => this.inflight.delete(documentId)),
        shareReplay({ bufferSize: 1, refCount: true }),
      );
      this.inflight.set(documentId, pending);
    }
    return pending;
  }

  /** One more thumbnail shows this picture; it becomes the most recently used. */
  private retain(documentId: string): void {
    const entry = this.entries.get(documentId);
    if (!entry) return;
    entry.uses++;
    this.entries.delete(documentId);
    this.entries.set(documentId, entry);
  }

  /** Revokes the least recently used pictures that nobody shows while more than the cap are alive. */
  private evict(): void {
    if (this.entries.size <= QA_THUMBNAIL.maxObjectUrls) return;
    for (const [id, entry] of this.entries) {
      if (this.entries.size <= QA_THUMBNAIL.maxObjectUrls) return;
      if (entry.uses === 0) {
        URL.revokeObjectURL(entry.url);
        this.entries.delete(id);
      }
    }
  }

  /** Waits for a free slot, reads the metadata (type and size), then the bytes; the slot is given back however it ends. */
  private download(documentId: string): Observable<SafeUrl | null> {
    return defer(() => {
      let held = false;
      return this.acquire().pipe(
        tap(() => (held = true)),
        switchMap(() => this.documents.getMetadata(documentId)),
        switchMap((doc) =>
          doc.sizeBytes > QA_THUMBNAIL.maxBytes || !(doc.contentType ?? '').toLowerCase().startsWith('image/')
            ? of(null)
            : this.documents.fetchFile(doc).pipe(map((blob) => this.toSafeUrl(documentId, blob))),
        ),
        finalize(() => {
          if (held) this.freePlace();
        }),
      );
    });
  }

  private toSafeUrl(documentId: string, blob: Blob): SafeUrl | null {
    if (this.destroyed || !blob.type.toLowerCase().startsWith('image/')) return null;
    const url = URL.createObjectURL(blob);
    if (!url.startsWith('blob:')) {
      URL.revokeObjectURL(url);
      return null;
    }
    const safe = this.sanitizer.bypassSecurityTrustUrl(url);
    this.entries.set(documentId, { url, safe, uses: 0 });
    return safe;
  }

  private acquire(): Observable<void> {
    return new Observable<void>((subscriber) => {
      const grant = () => {
        this.active++;
        subscriber.next();
        subscriber.complete();
      };
      if (this.active < QA_THUMBNAIL.maxConcurrent) {
        grant();
        return undefined;
      }
      this.waiting.push(grant);
      return () => {
        const at = this.waiting.indexOf(grant);
        if (at >= 0) this.waiting.splice(at, 1);
      };
    });
  }

  /** Gives a download place back and lets the next waiting one start. */
  private freePlace(): void {
    this.active = Math.max(0, this.active - 1);
    this.waiting.shift()?.();
  }
}
