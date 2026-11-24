import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { saveBlob } from '../hq-file.util';
import { HqOpenFile, HqOpenFileData } from './hq-open-file';

/**
 * Opens a file the screen has already read (a Blob) the way the upload components do: images and PDFs in the shared viewer
 * (hq-open-file), anything else saved by the browser under its name. For read-only places that show or link a file (a photo in a
 * review queue, a chat attachment) so that opening a file looks the same everywhere.
 */
@Injectable({ providedIn: 'root' })
export class HqFileViewerService {
  private readonly dialog = inject(MatDialog);

  /** Whether the viewer can show the file (otherwise {@link show} saves it). */
  canPreview(blob: Blob): boolean {
    return blob.type.startsWith('image/') || blob.type === 'application/pdf';
  }

  show(blob: Blob, fileName: string): void {
    if (this.canPreview(blob)) {
      const data: HqOpenFileData = { blob, fileName };
      this.dialog.open(HqOpenFile, { data, maxWidth: '95vw', autoFocus: false });
    } else {
      saveBlob(blob, fileName);
    }
  }
}
