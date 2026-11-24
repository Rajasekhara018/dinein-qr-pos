import { ChangeDetectorRef, Component, HostListener, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { HqTimezoneService } from 'hq-shared-lib';
import { NotificationService } from '../../../shared/services/notification-service';
import { LifecycleFilesService } from '../lifecycle-files.service';
import { DocumentVersion, PreviewKind, previewKind } from '../lifecycle-versions.helpers';
import { formatBytes, formatDateTime } from '../lifecycle.helpers';

/** What the preview dialog is opened with: the versions of a document and the one to show first. */
export interface LifecyclePreviewData {
  versions: DocumentVersion[];
  index: number;
  files: LifecycleFilesService;
}

/**
 * One document's versions in a dialog: the chosen file large (an image, or a PDF in the browser's viewer), previous / next
 * (also the arrow keys), a strip of every version, and each version's date, size and scan result.
 */
@Component({
  selector: 'app-lifecycle-preview-dialog',
  standalone: false,
  templateUrl: './lifecycle-preview-dialog.html',
  styleUrl: './lifecycle-preview-dialog.scss',
})
export class LifecyclePreviewDialog implements OnInit {
  readonly data: LifecyclePreviewData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<LifecyclePreviewDialog>);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly translate = inject(TranslateService);
  private readonly timezone = inject(HqTimezoneService);
  private readonly notify = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  index = 0;
  /** Object URLs read so far, by document id (thumbnails use them too). */
  readonly urls: Record<string, string> = {};
  /** The trusted URL of the shown PDF (an object URL this page created from bytes it fetched itself). */
  pdfUrl: SafeResourceUrl | null = null;
  loading = false;
  failed = false;

  ngOnInit(): void {
    this.index = Math.min(Math.max(this.data.index, 0), Math.max(this.data.versions.length - 1, 0));
    this.data.versions.forEach((v) => {
      if (previewKind(v.doc.contentType) === 'image') this.read(v, false);
    });
    this.show(this.index);
  }

  get current(): DocumentVersion | null {
    return this.data.versions[this.index] ?? null;
  }

  get kind(): PreviewKind {
    return previewKind(this.current?.doc.contentType);
  }

  kindOf(version: DocumentVersion): PreviewKind {
    return previewKind(version.doc.contentType);
  }

  show(index: number): void {
    if (index < 0 || index >= this.data.versions.length) return;
    this.index = index;
    this.pdfUrl = null;
    this.failed = false;
    const version = this.data.versions[index];
    if (this.kind === 'other') return;
    this.read(version, true);
  }

  previous(): void {
    this.show(this.index - 1);
  }

  next(): void {
    this.show(this.index + 1);
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowLeft') this.previous();
    if (event.key === 'ArrowRight') this.next();
  }

  download(): void {
    const version = this.current;
    if (!version) return;
    this.data.files.download(version.doc).subscribe({
      error: () => this.notify.error(this.translate.instant('documentManagement.lifecycle.preview.failed')),
    });
  }

  close(): void {
    this.ref.close();
  }

  size(bytes: number | null | undefined): string {
    return formatBytes(bytes);
  }

  when(iso: string | null | undefined): string {
    return iso ? formatDateTime(iso, this.translate.currentLang || 'en', this.timezone.effectiveTimezone()) : '—';
  }

  trackVersion(_: number, version: DocumentVersion): string {
    return version.doc.id;
  }

  /** Reads a file; `shown` marks the one in the big view (its failure is reported, a thumbnail's is not). */
  private read(version: DocumentVersion, shown: boolean): void {
    const id = version.doc.id;
    if (this.urls[id]) {
      if (shown) this.applyShown(version, this.urls[id]);
      return;
    }
    if (shown) this.loading = true;
    this.data.files.url(version.doc).subscribe({
      next: (url) => {
        this.urls[id] = url;
        if (shown && this.current?.doc.id === id) this.applyShown(version, url);
        this.cdr.markForCheck();
      },
      error: () => {
        if (shown && this.current?.doc.id === id) {
          this.loading = false;
          this.failed = true;
        }
        this.cdr.markForCheck();
      },
    });
  }

  private applyShown(version: DocumentVersion, url: string): void {
    this.loading = false;
    this.pdfUrl = previewKind(version.doc.contentType) === 'pdf' ? this.sanitizer.bypassSecurityTrustResourceUrl(url) : null;
  }
}
