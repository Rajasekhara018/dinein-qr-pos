import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminSettingsApi } from '../../../core/api/admin.api';
import { DeviceResponse, StaffResponse } from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ConfirmService } from '../shared/confirm.service';
import { errorMessage } from '../shared/form-errors';
import { ROLE_OPTIONS, StaffDialog, StaffDialogData } from './staff-dialog';

/** OWNER: staff users and kitchen devices. */
@Component({
  selector: 'app-admin-staff-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-page.html',
})
export class StaffPage {
  private readonly api = inject(AdminSettingsApi);
  private readonly sheets = inject(SheetService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthStore);

  protected readonly staff = signal<StaffResponse[]>([]);
  protected readonly devices = signal<DeviceResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  protected readonly devicesError = signal<unknown>(null);
  protected readonly revoking = signal<number | null>(null);
  protected readonly showRevoked = signal(false);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    const [staff, devices] = await Promise.allSettled([
      firstValueFrom(this.api.staff()),
      firstValueFrom(this.api.devices()),
    ]);
    if (staff.status === 'fulfilled') {
      this.staff.set([...staff.value].sort((a, b) => a.username.localeCompare(b.username)));
      this.error.set(null);
    } else {
      this.error.set(staff.reason);
    }
    if (devices.status === 'fulfilled') {
      this.devices.set(devices.value);
      this.devicesError.set(null);
    } else {
      this.devicesError.set(devices.reason);
    }
    this.loading.set(false);
  }

  protected roleLabel(role: string): string {
    return ROLE_OPTIONS.find((r) => r.value === role)?.label ?? role;
  }

  protected isLocked(user: StaffResponse): boolean {
    return !!user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now();
  }

  protected visibleDevices(): DeviceResponse[] {
    return this.showRevoked() ? this.devices() : this.devices().filter((d) => d.active);
  }

  protected deviceState(device: DeviceResponse): string {
    if (device.revokedAt) return 'Revoked';
    if (!device.active) return 'Expired';
    return 'Active';
  }

  protected open(user: StaffResponse | null): void {
    const ref = this.sheets.open<StaffResponse, StaffDialogData, StaffDialog>(StaffDialog, {
      data: { staff: user },
      maxWidth: '40rem',
    });
    ref.closed.subscribe((saved) => {
      if (!saved) return;
      this.toasts.success(user ? `${saved.username} updated.` : `${saved.username} created. Share the temporary password securely.`);
      void this.load();
    });
  }

  protected async revoke(device: DeviceResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: `Revoke ${device.deviceName || 'this device'}?`,
      message: `The kitchen screen signed in as ${device.username} is signed out immediately and must log in again.`,
      confirmLabel: 'Revoke device',
    });
    if (!confirmed) return;
    this.revoking.set(device.id);
    try {
      await firstValueFrom(this.api.revokeDevice(device.id));
      this.toasts.success('Device revoked.');
      this.devices.set(await firstValueFrom(this.api.devices()));
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not revoke the device.'));
    } finally {
      this.revoking.set(null);
    }
  }
}
