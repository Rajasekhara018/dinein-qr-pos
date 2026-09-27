import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminSettingsApi } from '../../../core/api/admin.api';
import { DeviceResponse, StaffResponse } from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { errorMessage } from '../../../shared/util/form-errors';
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

  /** Client-side paging: both lists are fetched in full (there's no filtering to page around), so only the
   *  on-screen slice changes per page, not the request. */
  protected readonly staffPage = signal(1);
  protected readonly devicesPage = signal(1);
  private readonly pageSize = 10;

  protected readonly staffPageCount = computed(() => Math.max(1, Math.ceil(this.staff().length / this.pageSize)));
  protected readonly pagedStaff = computed(() => {
    const start = (this.staffPage() - 1) * this.pageSize;
    return this.staff().slice(start, start + this.pageSize);
  });

  protected readonly devicesPageCount = computed(() =>
    Math.max(1, Math.ceil(this.visibleDevices().length / this.pageSize)),
  );
  /** Clamped so toggling "show revoked" (which can shrink the list) never points past the last page. */
  protected readonly devicesPageView = computed(() => Math.min(this.devicesPage(), this.devicesPageCount()));
  protected readonly pagedDevices = computed(() => {
    const start = (this.devicesPageView() - 1) * this.pageSize;
    return this.visibleDevices().slice(start, start + this.pageSize);
  });

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
      this.staffPage.set(1);
    } else {
      this.error.set(staff.reason);
    }
    if (devices.status === 'fulfilled') {
      this.devices.set(devices.value);
      this.devicesError.set(null);
      this.devicesPage.set(1);
    } else {
      this.devicesError.set(devices.reason);
    }
    this.loading.set(false);
  }

  protected goToStaffPage(page: number): void {
    this.staffPage.set(Math.min(Math.max(1, page), this.staffPageCount()));
  }

  protected goToDevicesPage(page: number): void {
    this.devicesPage.set(Math.min(Math.max(1, page), this.devicesPageCount()));
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
