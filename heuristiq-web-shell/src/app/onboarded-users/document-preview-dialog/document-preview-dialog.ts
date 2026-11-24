import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, Subject, takeUntil } from 'rxjs';

export interface DocumentPreviewData {
  /** Heading, e.g. the document field's label ("Aadhaar Card"). */
  title: string;
  /** Original file name, shown under the heading and used for the download. */
  fileName: string | null;
  /** The file's bytes, fetched lazily so the dialog can show its own loading state. */
  blob$: Observable<Blob>;
}

type PreviewKind = 'image' | 'pdf' | 'other';

const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'webp', 'gif'];

/** Modal viewer for a stored onboarding document: image preview, embedded PDF, or a download fallback. */
@Component({
  selector: 'app-document-preview-dialog',
  standalone: false,
  templateUrl: './document-preview-dialog.html',
  styleUrl: './document-preview-dialog.scss',
})
export class DocumentPreviewDialog implements OnInit, OnDestroy {
  loading = true;
  failed = false;
  /** The server's own explanation, shown under the generic message to make failures diagnosable. */
  errorDetail: string | null = null;
  kind: PreviewKind = 'other';
  objectUrl: string | null = null;
  pdfUrl: SafeResourceUrl | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DocumentPreviewData,
    private dialogRef: MatDialogRef<DocumentPreviewDialog>,
    private sanitizer: DomSanitizer,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.data.blob$.pipe(takeUntil(this.destroy$)).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.kind = this.kindOf(blob);
        if (this.kind === 'pdf') {
          this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl);
        }
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err: { error?: { message?: string } }) => {
        this.failed = true;
        this.errorDetail = err?.error?.message ?? null;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
  }

  close(): void {
    this.dialogRef.close();
  }

  /** Content type first (set from the stored file's metadata), file extension as a fallback. */
  private kindOf(blob: Blob): PreviewKind {
    const type = blob.type.toLowerCase();
    const extension = (this.data.fileName ?? '').split('.').pop()?.toLowerCase() ?? '';
    if (type.startsWith('image/') || IMAGE_EXTENSIONS.includes(extension)) return 'image';
    if (type === 'application/pdf' || extension === 'pdf') return 'pdf';
    return 'other';
  }
}
