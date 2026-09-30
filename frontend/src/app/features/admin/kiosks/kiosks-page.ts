import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminKioskApi } from '../../../core/api/admin.api';
import { KioskDeviceResponse, KioskPairingResponse } from '../../../core/api/models';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { errorMessage } from '../../../shared/util/form-errors';
import { KioskPairingDialog, KioskPairingDialogData } from './kiosk-pairing-dialog';
import { KioskStatusView, kioskStatusView } from './kiosk-logic';

/** Owner/manager: self-order kiosk devices — add, re-pair (new code) and revoke. */
@Component({
  selector: 'app-admin-kiosks-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kiosks-page.html',
})
export class KiosksPage {
  private readonly api = inject(AdminKioskApi);
  private readonly sheets = inject(SheetService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  protected readonly devices = signal<KioskDeviceResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  /** Id of the device with an action in flight. */
  protected readonly busy = signal<number | null>(null);
  /** Re-evaluated every 30 s so the online/offline hint ages without a reload. */
  protected readonly now = signal(Date.now());

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.devices.set(await firstValueFrom(this.api.devices()));
      this.error.set(null);
    } catch (error) {
      this.error.set(error);
    } finally {
      this.loading.set(false);
      this.now.set(Date.now());
    }
  }

  protected status(device: KioskDeviceResponse): KioskStatusView {
    return kioskStatusView(device, this.now());
  }

  protected add(): void {
    this.openPairing(null, 'Kiosk added.');
  }

  protected async newCode(device: KioskDeviceResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: `New pairing code for ${device.name}?`,
      message:
        'The tablet is signed out right away and stays offline until it is paired again with the new code.',
      confirmLabel: 'Generate code',
      tone: 'primary',
    });
    if (!confirmed) return;
    this.busy.set(device.id);
    try {
      const issued = await firstValueFrom(this.api.newPairingCode(device.id));
      this.openPairing(issued);
      void this.load();
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not generate a pairing code.'));
    } finally {
      this.busy.set(null);
    }
  }

  protected async revoke(device: KioskDeviceResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: `Revoke ${device.name}?`,
      message:
        'The kiosk is signed out immediately and cannot take orders. You can pair a tablet again later with a new code.',
      confirmLabel: 'Revoke kiosk',
    });
    if (!confirmed) return;
    this.busy.set(device.id);
    try {
      await firstValueFrom(this.api.revokeDevice(device.id));
      this.toasts.success(`${device.name} revoked.`);
      await this.load();
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not revoke the kiosk.'));
    } finally {
      this.busy.set(null);
    }
  }

  private openPairing(issued: KioskPairingResponse | null, successMessage?: string): void {
    const ref = this.sheets.open<boolean, KioskPairingDialogData, KioskPairingDialog>(
      KioskPairingDialog,
      {
        data: { issued },
        maxWidth: '32rem',
        disableClose: true,
      },
    );
    ref.closed.subscribe((changed) => {
      if (!changed) return;
      if (successMessage) this.toasts.success(successMessage);
      void this.load();
    });
  }
}
