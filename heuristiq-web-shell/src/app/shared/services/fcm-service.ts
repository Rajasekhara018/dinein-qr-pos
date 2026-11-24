import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BrandingService } from './branding-service';
import {
  FCM_APP_ID,
  FCM_REGISTRATION_KEY,
  FCM_REREGISTER_AFTER_MS,
  FCM_TOKEN_KEY,
} from './fcm.constants';

/**
 * Firebase Cloud Messaging service.
 *
 * Lifecycle:
 *  - Call setup() after the user logs in (subscribes to push & registers the FCM token with the backend).
 *  - Call cleanup() before/after the user logs out (unregisters the token so the backend stops sending pushes).
 *
 * Registers/unregisters the FCM token with NMS's device-token endpoint
 * (2026_RailCab_NMS DeviceTokenController, proxied through the gateway).
 */
@Injectable({ providedIn: 'root' })
export class FcmService {
  // Read lazily (not snapshotted) -- as a root singleton this service could in principle be
  // constructed before BrandingService's app initializer has resolved.
  private readonly brandingService = inject(BrandingService);
  private swRegistration: ServiceWorkerRegistration | null = null;
  private foregroundUnsubscribe: (() => void) | null = null;
  private setupInFlight: Promise<void> | null = null;
  private readonly tokenKey = FCM_TOKEN_KEY;

  constructor(private http: HttpClient) {}

  /**
   * Request notification permission, register the service worker, obtain an FCM token,
   * register it with the backend (once per token), and listen for foreground messages.
   * Safe to call multiple times — skips work if the token is unchanged.
   */
  setup(): Promise<void> {
    // auth.user$ can emit several times in a session; overlapping runs used to stack work (and
    // foreground listeners). One run at a time, and callers share its result.
    if (!this.setupInFlight) {
      this.setupInFlight = this.doSetup().finally(() => (this.setupInFlight = null));
    }
    return this.setupInFlight;
  }

  private async doSetup(): Promise<void> {
    if (!environment.firebase?.apiKey) {
      console.warn('FCM setup skipped: no Firebase configuration for this environment');
      return;
    }
    if (!('Notification' in window) || !('serviceWorker' in navigator)) return;

    try {
      const { initializeApp, getApps } = await import('firebase/app');
      const { getMessaging, getToken, onMessage } = await import('firebase/messaging');

      if (getApps().length === 0) {
        initializeApp(environment.firebase);
      }

      const permission = await Notification.requestPermission();
      if (permission !== 'granted') return;

      this.swRegistration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
      await navigator.serviceWorker.ready;

      const messaging = getMessaging();
      const token = await getToken(messaging, {
        vapidKey: environment.firebase.vapidKey,
        serviceWorkerRegistration: this.swRegistration,
      });

      if (!token) return;

      if (this.needsRegistration(token)) {
        await this.registerWithBackend(token);
        localStorage.setItem(this.tokenKey, token);
        localStorage.setItem(FCM_REGISTRATION_KEY, JSON.stringify({ userId: this.currentUserId(), at: Date.now() }));
      }

      // Show foreground messages as native notifications via the SW registration. Replace any
      // listener from an earlier setup() instead of adding another -- otherwise one push shows N times.
      this.foregroundUnsubscribe?.();
      this.foregroundUnsubscribe = onMessage(messaging, (payload) => {
        const title = payload.notification?.title ?? this.brandingService.branding.appName;
        const body = payload.notification?.body ?? '';
        if (this.swRegistration && Notification.permission === 'granted') {
          this.swRegistration.showNotification(title, {
            body,
            icon: '/favicon.ico',
            // Mirrors firebase-messaging-sw.js's own background handler -- without this, a push
            // that arrives while the tab is open loses its notificationclick target (data.url).
            data: payload.data ?? {},
          });
        }
      });
    } catch (err) {
      console.warn('FCM setup skipped:', err);
    }
  }

  /**
   * Called by AuthService.logout() BEFORE it clears the session. The backend call that removes this
   * browser's registration needs the user's login token; once logout() has dropped it (and the
   * header's later cleanup() runs) the request goes out unauthenticated and is rejected, which left
   * the browser registered -- still receiving the signed-out user's pushes. The request is created
   * synchronously here, so the auth interceptor reads the token before it is removed.
   */
  unregisterBeforeLogout(): void {
    const token = localStorage.getItem(this.tokenKey);
    if (!token) return;
    this.http
      .delete(`${environment.apiUrl}/v1/nms/devices/fcm-token`, { body: { token } })
      .subscribe({ error: () => {} });
    localStorage.removeItem(FCM_REGISTRATION_KEY);
    localStorage.removeItem(this.tokenKey);
    void this.deleteFirebaseToken();
  }

  /**
   * Local teardown when the session ends: stop the foreground listener, delete the token from
   * Firebase and forget it. If logout() already unregistered it (above) there is nothing left to
   * do; otherwise (session expiry, another tab logged out) the backend call is still attempted.
   */
  async cleanup(): Promise<void> {
    this.foregroundUnsubscribe?.();
    this.foregroundUnsubscribe = null;
    const token = localStorage.getItem(this.tokenKey);
    if (!token) return;
    try {
      await this.http
        .delete(`${environment.apiUrl}/v1/nms/devices/fcm-token`, { body: { token } })
        .toPromise();
    } catch (_) {
      // Best-effort; ignore errors during logout cleanup
    } finally {
      // Forget the token locally whether or not the backend call succeeded -- a failure used to
      // skip this, leaving a stale token that made the next login think it was already registered.
      localStorage.removeItem(this.tokenKey);
      localStorage.removeItem(FCM_REGISTRATION_KEY);
      await this.deleteFirebaseToken();
    }
  }

  private async deleteFirebaseToken(): Promise<void> {
    try {
      if (!environment.firebase?.apiKey) return;
      const { initializeApp, getApps } = await import('firebase/app');
      const { getMessaging, deleteToken } = await import('firebase/messaging');
      if (getApps().length === 0) initializeApp(environment.firebase);
      await deleteToken(getMessaging());
    } catch (_) {
      // Best-effort
    }
  }

  /**
   * Register when the token is new, when a DIFFERENT user is now signed in on this browser (the
   * token is per-browser, so an unchanged token says nothing about who owns it -- skipping here
   * meant a second user on the same browser never received pushes), or when the last registration
   * is old enough that NMS's last-seen time should be refreshed.
   */
  private needsRegistration(token: string): boolean {
    if (localStorage.getItem(this.tokenKey) !== token) return true;
    try {
      const last = JSON.parse(localStorage.getItem(FCM_REGISTRATION_KEY) || 'null') as { userId?: string | null; at?: number } | null;
      if (!last || last.userId !== this.currentUserId()) return true;
      return Date.now() - (last.at ?? 0) > FCM_REREGISTER_AFTER_MS;
    } catch {
      return true;
    }
  }

  private currentUserId(): string | null {
    try {
      return JSON.parse(localStorage.getItem('user_data') || 'null')?.id ?? null;
    } catch {
      return null;
    }
  }

  private async registerWithBackend(token: string): Promise<void> {
    await this.http
      .post(`${environment.apiUrl}/v1/nms/devices/fcm-token`, { token, ...this.deviceDetails() })
      .toPromise();
  }

  /**
   * What this browser can tell NMS about itself. NMS derives the user and user type from the login
   * token, so only device/app details go in the body. Everything comes from standard browser APIs.
   */
  private deviceDetails(): Record<string, string> {
    const uaData = (navigator as Navigator & { userAgentData?: { platform?: string; brands?: { brand: string; version: string }[] } }).userAgentData;
    const brand = uaData?.brands?.find((b) => !/not.?a.?brand/i.test(b.brand));
    return {
      platform: 'web',
      app: FCM_APP_ID,
      deviceModel: brand ? `${brand.brand} ${brand.version}` : this.browserFromUserAgent(),
      osVersion: uaData?.platform || navigator.platform || '',
      locale: navigator.language || '',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
    };
  }

  private browserFromUserAgent(): string {
    const ua = navigator.userAgent;
    const match =
      /(Edg|OPR|Firefox|Chrome|Safari)\/([\d.]+)/.exec(ua);
    if (!match) return 'Browser';
    const names: Record<string, string> = { Edg: 'Edge', OPR: 'Opera' };
    return `${names[match[1]] ?? match[1]} ${match[2]}`;
  }
}
