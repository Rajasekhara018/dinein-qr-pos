import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminTablesApi } from '../../../core/api/admin.api';
import { TableResponse } from '../../../core/api/models';
import { ToastService } from '../../../core/ui/toast.service';
import { copyText } from '../shared/browser';

export interface QrPreviewData {
  table: TableResponse;
}

/**
 * QR preview. The PNG endpoint needs the bearer token, so it is fetched as a Blob and shown via an object URL
 * (revoked when the dialog closes).
 */
@Component({
  selector: 'app-qr-preview-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './qr-preview-dialog.html',
})
export class QrPreviewDialog {
  private readonly api = inject(AdminTablesApi);
  private readonly toasts = inject(ToastService);
  protected readonly ref = inject<DialogRef<void, QrPreviewDialog>>(DialogRef);
  protected readonly data = inject<QrPreviewData>(DIALOG_DATA);

  protected readonly src = signal<string | null>(null);
  protected readonly error = signal(false);

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      const url = this.src();
      if (url) URL.revokeObjectURL(url);
    });
    void this.load();
  }

  protected async load(): Promise<void> {
    this.error.set(false);
    try {
      const blob = await firstValueFrom(this.api.qrPng(this.data.table.qrImageUrl));
      const previous = this.src();
      if (previous) URL.revokeObjectURL(previous);
      this.src.set(URL.createObjectURL(blob));
    } catch {
      this.error.set(true);
    }
  }

  protected async copy(): Promise<void> {
    const ok = await copyText(this.data.table.qrUrl);
    if (ok) this.toasts.success('Menu link copied.');
    else this.toasts.error('Could not copy. Select the link and copy it manually.');
  }

  protected fileName(): string {
    return `qr-${this.data.table.label.replace(/[^A-Za-z0-9_-]+/g, '-')}.png`;
  }
}
