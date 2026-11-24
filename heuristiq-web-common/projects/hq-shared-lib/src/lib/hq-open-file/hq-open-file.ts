import { Component, Inject, OnDestroy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

export interface HqOpenFileData {
  blob: Blob;
  fileName: string;
}

/** Full-size preview of an image or PDF (tou-open-file). Owns and revokes its object URL. */
@Component({
  selector: 'hq-open-file',
  standalone: false,
  templateUrl: './hq-open-file.html',
  styleUrl: './hq-open-file.scss',
})
export class HqOpenFile implements OnDestroy {
  readonly isImage: boolean;
  readonly isPdf: boolean;
  readonly objectUrl: string;
  readonly safeUrl: SafeResourceUrl;

  constructor(
    public dialogRef: MatDialogRef<HqOpenFile>,
    @Inject(MAT_DIALOG_DATA) public data: HqOpenFileData,
    sanitizer: DomSanitizer,
  ) {
    this.isImage = data.blob.type.startsWith('image/');
    this.isPdf = data.blob.type === 'application/pdf';
    this.objectUrl = URL.createObjectURL(data.blob);
    // An object URL this component just created from bytes it fetched itself.
    this.safeUrl = sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl);
  }

  ngOnDestroy(): void {
    URL.revokeObjectURL(this.objectUrl);
  }

  close(): void {
    this.dialogRef.close();
  }
}
