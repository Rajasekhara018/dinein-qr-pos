import { DOCUMENT, Injectable, OnDestroy, inject } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ACCESS_TOKEN_STORAGE_KEY,
  SCREEN_KEYS_BY_ROUTE,
  SCREEN_KEY_PATTERN,
  SCREEN_VIEWS_PATH,
  SCREEN_VIEW_APP_CHANNEL,
  SCREEN_VIEW_AUDIT_ENABLED,
  SCREEN_VIEW_BATCH_SIZE,
  SCREEN_VIEW_EXCLUDED_PREFIXES,
  SCREEN_VIEW_FLUSH_INTERVAL_MS,
  SCREEN_VIEW_MAX_BUFFERED,
  SCREEN_VIEW_MAX_KEY_LENGTH,
  SCREEN_VIEW_RATE_LIMIT_PAUSE_MS,
  SCREEN_VIEW_UNLOAD_BATCH_SIZE,
} from './screen-view.constants';

/** One opened screen, as accepted by the audit service (ScreenViewInput). */
export interface ScreenViewEvent {
  eventId: string;
  screenKey: string;
  occurredAt: string;
  appChannel: string;
  feature: string;
  details: { platform: string; previousScreenKey?: string };
}

/** Turns an URL path into a key matching the server pattern: `/Foo/bar/123456` -> `foo.bar.id`. */
export function sanitizePathToKey(path: string): string {
  const clean = path.split(/[?#]/)[0].toLowerCase();
  const segments = clean
    .split('/')
    .filter((s) => s.length > 0)
    .map((s) => s.replace(/[^a-z0-9_-]/g, '_'))
    .map((s) => (/^[0-9]+$/.test(s) || /^(?=.*[0-9])[0-9a-f-]{6,}$/.test(s) ? 'id' : s));
  let key = segments.length ? segments.join('.') : 'root';
  if (!/^[a-z]/.test(key)) key = `screen.${key}`;
  key = key.slice(0, SCREEN_VIEW_MAX_KEY_LENGTH).replace(/\.+$/, '');
  return key;
}

/** Resolves the screen key for an activated route chain (see screen-view.constants.ts). */
export function resolveScreenKey(snapshotRoot: ActivatedRouteSnapshot | null, url: string): string {
  const parts: string[] = [];
  let leaf: ActivatedRouteSnapshot | null = snapshotRoot;
  let explicit: unknown;
  while (leaf) {
    const path = leaf.routeConfig?.path;
    if (path) parts.push(path);
    const data = leaf.routeConfig?.data;
    // The deepest route that declares a key wins; a deeper route without one (e.g. the '' leaf of a lazy module
    // whose mounting route carries the key) must not erase it.
    if (data && typeof data['screenKey'] === 'string') explicit = data['screenKey'];
    leaf = leaf.firstChild;
  }
  if (typeof explicit === 'string' && SCREEN_KEY_PATTERN.test(explicit)) return explicit;
  const mapped = SCREEN_KEYS_BY_ROUTE[parts.join('/')];
  if (mapped) return mapped;
  return sanitizePathToKey(url);
}

/**
 * Reports each screen the signed-in user opens to the central audit trail
 * (POST {gateway}/v1/audit/screen-views). Events are buffered in memory (capped), sent in
 * batches on an interval, and flushed with fetch keepalive when the tab is hidden or unloaded.
 * Everything is best effort: no method throws, failures just drop the batch, and nothing is sent
 * while signed out or offline. The user and role come from the bearer token, never the body.
 */
@Injectable({ providedIn: 'root' })
export class ScreenViewService implements OnDestroy {
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private buffer: ScreenViewEvent[] = [];
  private subscription: Subscription | null = null;
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private started = false;
  private flushing = false;
  private pausedUntil = 0;
  private lastUrl: string | null = null;
  private lastKey: string | null = null;
  private seq = 0;

  private readonly onVisibilityChange = (): void => {
    if (this.document.visibilityState === 'hidden') this.flush(true);
  };
  private readonly onPageHide = (): void => this.flush(true);

  /** Starts listening to navigations. Idempotent; a no-op when reporting is switched off. */
  start(): void {
    if (this.started || !SCREEN_VIEW_AUDIT_ENABLED) return;
    this.started = true;
    try {
      this.subscription = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) this.record(event.urlAfterRedirects || event.url);
      });
      this.intervalId = setInterval(() => this.flush(false), SCREEN_VIEW_FLUSH_INTERVAL_MS);
      this.document.addEventListener('visibilitychange', this.onVisibilityChange);
      this.document.defaultView?.addEventListener('pagehide', this.onPageHide);
    } catch {
      // never affect the app
    }
  }

  /** Buffers one screen view for [url]. Exposed for tests; normally driven by router events. */
  record(url: string): void {
    try {
      if (!SCREEN_VIEW_AUDIT_ENABLED || url === this.lastUrl) return;
      this.lastUrl = url;
      const path = url.split(/[?#]/)[0];
      if (SCREEN_VIEW_EXCLUDED_PREFIXES.some((p) => path === p || path.startsWith(`${p}/`))) return;
      if (!this.readToken()) {
        // Pre-login screens carry no identity; don't let them leak into the next user's trail.
        this.buffer = [];
        this.lastKey = null;
        return;
      }
      const screenKey = resolveScreenKey(this.router.routerState?.snapshot?.root ?? null, path);
      const dot = screenKey.indexOf('.');
      const event: ScreenViewEvent = {
        eventId: this.newEventId(),
        screenKey,
        occurredAt: new Date().toISOString(),
        appChannel: SCREEN_VIEW_APP_CHANNEL,
        feature: dot > 0 ? screenKey.slice(0, dot) : screenKey,
        details: { platform: 'web', ...(this.lastKey ? { previousScreenKey: this.lastKey } : {}) },
      };
      this.lastKey = screenKey;
      this.buffer.push(event);
      if (this.buffer.length > SCREEN_VIEW_MAX_BUFFERED) {
        this.buffer.splice(0, this.buffer.length - SCREEN_VIEW_MAX_BUFFERED);
      }
      if (this.buffer.length >= SCREEN_VIEW_BATCH_SIZE) this.flush(false);
    } catch {
      // never affect the app
    }
  }

  /** Number of events waiting to be sent. */
  get pendingCount(): number {
    return this.buffer.length;
  }

  /**
   * Sends buffered events. With [unloading] true (tab hidden/closing) every remaining batch is
   * fired immediately with keepalive; otherwise batches go out one after another.
   */
  flush(unloading = false): void {
    try {
      if (this.buffer.length === 0 || (this.flushing && !unloading)) return;
      const token = this.readToken();
      if (!token) {
        this.buffer = [];
        return;
      }
      if (Date.now() < this.pausedUntil) return;
      if (typeof navigator !== 'undefined' && navigator.onLine === false) return; // keep, capped
      const size = unloading ? SCREEN_VIEW_UNLOAD_BATCH_SIZE : SCREEN_VIEW_BATCH_SIZE;
      const batches: ScreenViewEvent[][] = [];
      while (this.buffer.length > 0 && (unloading || batches.length === 0)) {
        batches.push(this.buffer.splice(0, size));
      }
      this.flushing = true;
      void this.sendAll(batches, token).finally(() => {
        this.flushing = false;
        if (!unloading && this.buffer.length >= SCREEN_VIEW_BATCH_SIZE) this.flush(false);
      });
    } catch {
      this.flushing = false;
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    if (this.intervalId !== null) clearInterval(this.intervalId);
    this.intervalId = null;
    this.document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.document.defaultView?.removeEventListener('pagehide', this.onPageHide);
    this.buffer = [];
    this.started = false;
  }

  private async sendAll(batches: ScreenViewEvent[][], token: string): Promise<void> {
    await Promise.all(batches.map((batch) => this.send(batch, token)));
  }

  private async send(events: ScreenViewEvent[], token: string): Promise<void> {
    try {
      // fetch (not HttpClient): bypasses the auth interceptor's refresh/logout handling, and
      // keepalive lets the request outlive a closing tab. sendBeacon can't carry Authorization.
      const response = await fetch(`${environment.gatewayUrl}${SCREEN_VIEWS_PATH}`, {
        method: 'POST',
        keepalive: true,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ events }),
      });
      if (response.status === 429) this.pausedUntil = Date.now() + SCREEN_VIEW_RATE_LIMIT_PAUSE_MS;
    } catch {
      // offline / network error: the batch is dropped
    }
  }

  private readToken(): string | null {
    try {
      return localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private newEventId(): string {
    try {
      return crypto.randomUUID();
    } catch {
      return `${Date.now().toString(36)}-${(this.seq++).toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    }
  }
}
