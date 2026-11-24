import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, inject, signal } from '@angular/core';
import { SafeUrl } from '@angular/platform-browser';
import { HqSharedLibModule } from 'hq-shared-lib';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { QA_THUMBNAIL, QA_THUMBNAIL_KEYS } from './qa-evidence.constants';
import { evidenceLabelKey } from './qa-evidence.helpers';
import { QaEvidenceThumbnailService } from './qa-evidence-thumbnail.service';

/** Where a preview is in its life: waiting to scroll into view, loading, showing the picture, or giving up (the old link). */
export type QaThumbnailState = 'waiting' | 'loading' | 'ready' | 'fallback';

/**
 * A screenshot shown as a small picture right in the row. It downloads the file only when it comes within
 * {@link QA_THUMBNAIL.rootMargin} of the viewport (at once when the browser has no IntersectionObserver), through
 * {@link QaEvidenceThumbnailService} (three downloads at a time, cached, revoked when the page goes). On a long list the
 * picture is given back when the row has scrolled further than {@link QA_THUMBNAIL.unloadMargin} away, so a run with
 * hundreds of rows keeps only the pictures near the viewport in memory; it is shown again (from the cache or by a new
 * download) when the row returns. The picture is a real button that opens the full viewer; when the file is too big, is
 * not an image, or cannot be loaded, it shows the same "Screenshot" button the page always had, so nothing ever shows as a
 * broken image or an error toast. The screen that uses it must provide {@link QaEvidenceThumbnailService}.
 */
@Component({
  selector: 'app-qa-evidence-thumbnail',
  standalone: true,
  imports: [TranslateModule, HqSharedLibModule],
  templateUrl: './qa-evidence-thumbnail.html',
  styleUrl: './qa-evidence-thumbnail.scss',
})
export class QaEvidenceThumbnail implements AfterViewInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly thumbnails = inject(QaEvidenceThumbnailService);
  private readonly destroy$ = new Subject<void>();
  private loading$ = new Subject<void>();
  private nearObserver: IntersectionObserver | null = null;
  private farObserver: IntersectionObserver | null = null;

  /** The DMS document id of the screenshot. */
  @Input({ required: true }) documentId = '';
  /** `SCREEN` or `FAILURE`: only picks the wording. */
  @Input() kind = 'SCREEN';
  /** True while another screenshot is being opened in the viewer. */
  @Input() disabled = false;
  /** The user asked for the full picture. */
  @Output() readonly opened = new EventEmitter<void>();

  readonly state = signal<QaThumbnailState>('waiting');
  readonly src = signal<SafeUrl | null>(null);
  readonly size = QA_THUMBNAIL;
  readonly keys = QA_THUMBNAIL_KEYS;

  get kindKey(): string {
    return evidenceLabelKey(this.kind);
  }

  ngAfterViewInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.start();
      return;
    }
    // Near the viewport: load (again, after a return). Far from it: give the picture back.
    this.nearObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) this.start();
      },
      { rootMargin: QA_THUMBNAIL.rootMargin },
    );
    this.farObserver = new IntersectionObserver(
      (entries) => {
        if (entries.length > 0 && entries.every((entry) => !entry.isIntersecting)) this.unload();
      },
      { rootMargin: QA_THUMBNAIL.unloadMargin },
    );
    this.nearObserver.observe(this.host.nativeElement);
    this.farObserver.observe(this.host.nativeElement);
  }

  ngOnDestroy(): void {
    this.nearObserver?.disconnect();
    this.farObserver?.disconnect();
    this.nearObserver = null;
    this.farObserver = null;
    this.giveBack();
    this.destroy$.next();
    this.destroy$.complete();
  }

  open(): void {
    if (!this.disabled) this.opened.emit();
  }

  private start(): void {
    if (this.state() !== 'waiting') return;
    this.state.set('loading');
    this.loading$ = new Subject<void>();
    this.thumbnails
      .load(this.documentId)
      .pipe(takeUntil(this.destroy$), takeUntil(this.loading$))
      .subscribe({
        next: (url) => {
          this.src.set(url);
          this.state.set(url ? 'ready' : 'fallback');
        },
        error: () => this.state.set('fallback'),
      });
  }

  /** The row scrolled far away: stop a download in progress or give the picture back, and wait for the row to return. */
  private unload(): void {
    const state = this.state();
    if (state !== 'ready' && state !== 'loading') return;
    this.loading$.next();
    this.giveBack();
    this.src.set(null);
    this.state.set('waiting');
  }

  /** Tells the service this thumbnail no longer shows its picture (only when it was showing one). */
  private giveBack(): void {
    if (this.state() === 'ready') this.thumbnails.release(this.documentId);
  }
}
