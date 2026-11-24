import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Where the browser says it is, in the shape UAM's PUT /identity/me/devices/current/location takes. */
export interface DeviceLocation {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
}

const GEOLOCATION_TIMEOUT_MS = 10_000;
/** A position this recent is as good as a new one: avoids a fresh GPS fix for every sign-in. */
const GEOLOCATION_MAX_AGE_MS = 5 * 60_000;
const SESSION_FLAG_PREFIX = 'rw_device_location_';

/**
 * Tells UAM where the signed-in user's device is, so Active devices can show it.
 *
 * Asks the browser (which shows its own permission prompt) at most once per tab session per user, so
 * a refusal is never nagged about again until the next sign-in. Everything is best effort: a refused
 * prompt, a timeout or a failed request is ignored and never affects the app.
 */
@Injectable({ providedIn: 'root' })
export class DeviceLocationService {
  private readonly url = `${environment.uamApiUrl}/identity/me/devices/current/location`;

  constructor(private http: HttpClient) {}

  /** Call when a user session is active; does nothing after the first call for that user in this tab. */
  async captureOnce(userId: string | null | undefined): Promise<void> {
    if (!userId || !('geolocation' in navigator) || this.alreadyAsked(userId)) return;
    this.markAsked(userId);

    if (await this.isDenied()) return;
    const location = await this.currentLocation();
    if (!location) return;

    this.http.put(this.url, location).subscribe({ error: () => {} });
  }

  private currentLocation(): Promise<DeviceLocation | null> {
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (position) => resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: position.coords.accuracy,
        }),
        () => resolve(null),
        { enableHighAccuracy: false, timeout: GEOLOCATION_TIMEOUT_MS, maximumAge: GEOLOCATION_MAX_AGE_MS },
      );
    });
  }

  /** True when the user has already blocked location for this site (so there is no prompt to show). */
  private async isDenied(): Promise<boolean> {
    try {
      const status = await navigator.permissions?.query({ name: 'geolocation' as PermissionName });
      return status?.state === 'denied';
    } catch {
      return false;
    }
  }

  private alreadyAsked(userId: string): boolean {
    try {
      return sessionStorage.getItem(SESSION_FLAG_PREFIX + userId) === '1';
    } catch {
      return false;
    }
  }

  private markAsked(userId: string): void {
    try {
      sessionStorage.setItem(SESSION_FLAG_PREFIX + userId, '1');
    } catch {
      // storage unavailable: the worst case is asking again on the next page load
    }
  }
}
