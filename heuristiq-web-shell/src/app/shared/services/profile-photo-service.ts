import { Injectable } from '@angular/core';
import { HqDocument, HqDocumentService } from 'hq-shared-lib';
import { BehaviorSubject, Observable, of, switchMap } from 'rxjs';
import { PROFILE_PHOTO_CATEGORY } from './profile-photo.constants';

/**
 * The signed-in user's profile photo (stored in DMS by the Profile page's <hq-doc-upload>, under
 * category "profile-photo" in the user's own folder), shared with every place that shows the
 * avatar -- the header, its dropdown, the sidebar footer and the Profile page itself. Loaded once
 * per user; the Profile page calls {@link refresh} after an upload or removal so they all update
 * together. Shows nothing (callers fall back to the user's initial) until/unless a photo exists.
 */
@Injectable({ providedIn: 'root' })
export class ProfilePhotoService {
  private readonly url = new BehaviorSubject<string | null>(null);
  private loadedFor: string | null = null;

  /** Object URL of the current photo, or null when the user has none. */
  readonly photoUrl$: Observable<string | null> = this.url.asObservable();

  constructor(private documents: HqDocumentService) {}

  /** Loads the photo for this user unless it is already loaded. */
  load(userId: string | null | undefined): void {
    if (!userId) {
      this.clear();
      return;
    }
    if (this.loadedFor === userId) return;
    this.fetch(userId);
  }

  /** Re-reads the photo -- call after the user uploads or removes it. */
  refresh(userId: string | null | undefined): void {
    if (userId) this.fetch(userId);
  }

  clear(): void {
    this.loadedFor = null;
    this.set(null);
  }

  private fetch(userId: string): void {
    if (this.loadedFor && this.loadedFor !== userId) {
      this.set(null); // a different account signed in -- never show the previous user's photo
    }
    this.loadedFor = userId;
    this.documents
      .list(PROFILE_PHOTO_CATEGORY, userId)
      .pipe(
        switchMap((docs: HqDocument[]) => {
          const photo = docs.find((doc) => doc.contentType?.startsWith('image/'));
          return photo ? this.documents.fetchFile(photo) : of(null);
        }),
      )
      .subscribe({
        next: (blob) => this.set(blob),
        error: () => this.set(null),
      });
  }

  private set(blob: Blob | null): void {
    const previous = this.url.value;
    this.url.next(blob ? URL.createObjectURL(blob) : null);
    if (previous) URL.revokeObjectURL(previous);
  }
}
