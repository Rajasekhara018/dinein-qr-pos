import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SheetService } from '../../../core/ui/sheet.service';
import {
  ConfirmDialog,
  ConfirmDialogData,
  ConfirmDialogResult,
} from './confirm-dialog/confirm-dialog';

/** Promise-based confirmation (dialog on desktop, bottom sheet on phones). Resolves null when dismissed. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly sheets = inject(SheetService);

  async confirm(data: ConfirmDialogData): Promise<ConfirmDialogResult | null> {
    const ref = this.sheets.open<ConfirmDialogResult, ConfirmDialogData, ConfirmDialog>(
      ConfirmDialog,
      { data, maxWidth: '28rem' },
    );
    return (await firstValueFrom(ref.closed)) ?? null;
  }
}
