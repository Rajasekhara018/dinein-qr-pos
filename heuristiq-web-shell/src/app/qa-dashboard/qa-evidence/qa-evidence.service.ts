import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqDocumentService, HqOpenFile, saveBlob } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { NotificationService } from '../../shared/services/notification-service';
import { QA_EVIDENCE_DIALOG, QA_EVIDENCE_MESSAGE_KEYS } from './qa-evidence.constants';
import { isValidEvidenceId } from './qa-evidence.helpers';

/**
 * Opens a scenario's screenshot from the DMS in the shared file viewer (the same way the run PDF is opened): its
 * metadata first, for the real file name and type, then the bytes. Nothing is ever put into the page as HTML. A failure
 * is told to the user and answered with `false`.
 */
@Injectable({ providedIn: 'root' })
export class QaEvidenceService {
  private readonly documents = inject(HqDocumentService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  /** Opens the screenshot; answers `true` when it was opened (or saved, for a type the viewer cannot show). */
  open(documentId: string): Observable<boolean> {
    if (!isValidEvidenceId(documentId)) return of(false);
    return this.documents.getMetadata(documentId).pipe(
      switchMap((doc) => this.documents.fetchFile(doc).pipe(map((blob) => ({ blob, fileName: doc.originalFileName })))),
      map(({ blob, fileName }) => {
        if (blob.type === 'application/pdf' || blob.type.startsWith('image/')) {
          this.dialog.open(HqOpenFile, { data: { blob, fileName }, ...QA_EVIDENCE_DIALOG });
        } else {
          saveBlob(blob, fileName);
        }
        return true;
      }),
      catchError(() => {
        this.notify.error(this.translate.instant(QA_EVIDENCE_MESSAGE_KEYS.openFailed));
        return of(false);
      }),
    );
  }
}
