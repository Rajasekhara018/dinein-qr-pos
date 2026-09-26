import { DestroyRef, DOCUMENT, inject, Injectable, signal } from '@angular/core';

interface WakeLockSentinelLike {
  released: boolean;
  release(): Promise<void>;
  addEventListener(type: 'release', listener: () => void): void;
}

interface WakeLockLike {
  request(type: 'screen'): Promise<WakeLockSentinelLike>;
}

/**
 * Keeps the screen on while the board is open (`navigator.wakeLock`). The lock is dropped by the browser whenever the
 * tab is hidden, so it is re-acquired on `visibilitychange`. Silently does nothing where unsupported (older Safari,
 * insecure origins). Provided by the board component: released on destroy.
 */
@Injectable()
export class ScreenWakeLock {
  private readonly document = inject(DOCUMENT);
  private sentinel: WakeLockSentinelLike | null = null;
  private wanted = false;
  private requesting = false;

  readonly supported = !!this.wakeLock;
  private readonly _active = signal(false);
  readonly active = this._active.asReadonly();

  private readonly onVisibility = () => {
    if (this.wanted && this.document.visibilityState === 'visible') void this.acquire();
  };

  constructor() {
    this.document.addEventListener('visibilitychange', this.onVisibility);
    inject(DestroyRef).onDestroy(() => {
      this.document.removeEventListener('visibilitychange', this.onVisibility);
      this.release();
    });
  }

  private get wakeLock(): WakeLockLike | null {
    const nav = this.document.defaultView?.navigator as
      (Navigator & { wakeLock?: WakeLockLike }) | undefined;
    return nav?.wakeLock ?? null;
  }

  async acquire(): Promise<void> {
    this.wanted = true;
    const api = this.wakeLock;
    if (!api || this.requesting || (this.sentinel && !this.sentinel.released)) return;
    if (this.document.visibilityState !== 'visible') return;
    this.requesting = true;
    try {
      const sentinel = await api.request('screen');
      if (!this.wanted) {
        void sentinel.release();
        return;
      }
      this.sentinel = sentinel;
      this._active.set(true);
      sentinel.addEventListener('release', () => {
        if (this.sentinel === sentinel) {
          this.sentinel = null;
          this._active.set(false);
        }
      });
    } catch {
      // Denied (battery saver, not visible, permissions policy): degrade gracefully.
      this._active.set(false);
    } finally {
      this.requesting = false;
    }
  }

  release(): void {
    this.wanted = false;
    const sentinel = this.sentinel;
    this.sentinel = null;
    this._active.set(false);
    if (sentinel && !sentinel.released) void sentinel.release().catch(() => undefined);
  }
}

/** Fullscreen API wrapper (TVs / wall tablets). */
@Injectable()
export class FullscreenControl {
  private readonly document = inject(DOCUMENT);

  readonly supported = !!this.document.fullscreenEnabled;
  private readonly _active = signal(!!this.document.fullscreenElement);
  readonly active = this._active.asReadonly();

  private readonly onChange = () => this._active.set(!!this.document.fullscreenElement);

  constructor() {
    this.document.addEventListener('fullscreenchange', this.onChange);
    inject(DestroyRef).onDestroy(() =>
      this.document.removeEventListener('fullscreenchange', this.onChange),
    );
  }

  async toggle(): Promise<void> {
    try {
      if (this.document.fullscreenElement) {
        await this.document.exitFullscreen();
      } else {
        await this.document.documentElement.requestFullscreen({ navigationUI: 'hide' });
      }
    } catch {
      // Refused (no user gesture / not allowed): ignore.
    } finally {
      this.onChange();
    }
  }

  exit(): void {
    if (this.document.fullscreenElement) void this.document.exitFullscreen().catch(() => undefined);
  }
}
