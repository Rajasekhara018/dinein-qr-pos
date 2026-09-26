import { DestroyRef, inject, Injectable, signal } from '@angular/core';

/**
 * One shared 1-second ticker for the whole board (elapsed times, clock, auto-hide, "new" flash). Provided by the
 * board component, so the interval is cleared when the board is destroyed (sign-out, revoked device, navigation).
 */
@Injectable()
export class KitchenClock {
  private readonly _now = signal(Date.now());
  /** Epoch millis, updated every second. */
  readonly now = this._now.asReadonly();

  constructor() {
    const timer = setInterval(() => this._now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}

const IST_CLOCK = new Intl.DateTimeFormat('en-IN', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: 'Asia/Kolkata',
});

/** `7:05 pm` in IST. */
export function formatIstClock(epochMs: number): string {
  return IST_CLOCK.format(new Date(epochMs));
}
