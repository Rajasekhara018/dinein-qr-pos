import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { HqDocument, HqDocumentService } from 'hq-shared-lib';

/**
 * Opens documents for the lifecycle preview: the other uploads of the same owner and category (the versions), and each file as a
 * browser object URL read once per document and released when the page closes (one instance per page: provided by the page).
 */
@Injectable()
export class LifecycleFilesService implements OnDestroy {
  private readonly documents = inject(HqDocumentService);
  private readonly urls = new Map<string, Observable<string>>();
  private readonly created: string[] = [];

  versions(category: string, ownerId: string): Observable<HqDocument[]> {
    if (!category || !ownerId) return of([]);
    return this.documents.list(category, ownerId);
  }

  /** An object URL of the file (read once and remembered for this page). */
  url(doc: HqDocument): Observable<string> {
    let cached = this.urls.get(doc.id);
    if (!cached) {
      cached = this.documents.fetchFile(doc).pipe(
        map((blob) => {
          const url = URL.createObjectURL(blob);
          this.created.push(url);
          return url;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.urls.set(doc.id, cached);
    }
    return cached;
  }

  /** Saves the file under its original name. */
  download(doc: HqDocument): Observable<void> {
    return this.documents.download(doc);
  }

  ngOnDestroy(): void {
    for (const url of this.created) URL.revokeObjectURL(url);
    this.created.length = 0;
    this.urls.clear();
  }
}
