import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { StaffInfo } from '../api/models';
import { SafeStorage, STORAGE_KEYS } from '../util/storage';

export interface DeviceSession {
  deviceToken: string;
  expiresAt: string;
  user: StaffInfo;
  deviceName?: string;
}

export interface RegisterDeviceInput {
  username: string;
  /** Provide either password or pin (4–6 digits). */
  password?: string;
  pin?: string;
  deviceName?: string;
}

/**
 * Kitchen device session: a long-lived (30 day) revocable `dvc_…` token, persisted in localStorage so the tablet
 * "remembers" the device. Sent as `Authorization: Bearer` by `authInterceptor` for `/api/v1/kitchen/**`, and should be
 * supplied to `RealtimeService.setAuthProvider()` for the kitchen STOMP connection.
 */
@Injectable({ providedIn: 'root' })
export class DeviceAuthStore {
  private readonly api = inject(AuthApi);
  private readonly storage = inject(SafeStorage);

  private readonly _session = signal<DeviceSession | null>(this.load());

  readonly session = this._session.asReadonly();
  readonly token = computed(() => this._session()?.deviceToken ?? null);
  readonly user = computed(() => this._session()?.user ?? null);
  readonly isRegistered = computed(() => {
    const s = this._session();
    return !!s && new Date(s.expiresAt).getTime() > Date.now();
  });

  async registerDevice(input: RegisterDeviceInput): Promise<DeviceSession> {
    const response = await firstValueFrom(
      this.api.registerKitchenDevice({
        username: input.username,
        password: input.password || undefined,
        pin: input.pin || undefined,
        deviceName: input.deviceName || undefined,
      }),
    );
    const session: DeviceSession = {
      deviceToken: response.deviceToken,
      expiresAt: response.expiresAt,
      user: response.user,
      deviceName: input.deviceName,
    };
    this._session.set(session);
    this.storage.setJson(STORAGE_KEYS.kitchenDevice, session);
    return session;
  }

  /** Forgets the device locally (the owner revokes it server-side from the admin panel). */
  clear(): void {
    this._session.set(null);
    this.storage.removeItem(STORAGE_KEYS.kitchenDevice);
  }

  private load(): DeviceSession | null {
    const stored = this.storage.getJson<DeviceSession>(STORAGE_KEYS.kitchenDevice);
    if (!stored || typeof stored.deviceToken !== 'string' || !stored.user) return null;
    if (new Date(stored.expiresAt).getTime() <= Date.now()) {
      this.storage.removeItem(STORAGE_KEYS.kitchenDevice);
      return null;
    }
    return stored;
  }
}
