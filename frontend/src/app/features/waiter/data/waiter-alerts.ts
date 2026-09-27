import { computed, inject, Injectable, signal } from '@angular/core';
import { playChime, unlockAudio, vibrate } from '../../../core/util/alerts';
import { SafeStorage } from '../../../core/util/storage';

export const WAITER_SOUND_KEY = 'dinein.waiter.sound.v1';

/** `null` = never asked (show the "Enable sound" prompt). */
export type WaiterSoundPreference = 'on' | 'off' | null;

/** Audio/vibration seam (WebAudio + `navigator.vibrate` from core/util/alerts) so tests can fake it. */
@Injectable({ providedIn: 'root' })
export class WaiterAudio {
  readonly supported =
    typeof globalThis !== 'undefined' &&
    !!(
      globalThis.AudioContext ??
      (globalThis as unknown as { webkitAudioContext?: unknown }).webkitAudioContext
    );

  /** Never hangs: `resume()` can stay pending without a user gesture. */
  unlock(timeoutMs = 600): Promise<boolean> {
    if (!this.supported) return Promise.resolve(false);
    return Promise.race([
      unlockAudio(),
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), timeoutMs)),
    ]);
  }

  chime(): Promise<boolean> {
    if (!this.supported) return Promise.resolve(false);
    // Rising "ding-dong" that differs from the kitchen's new-order chime.
    return Promise.race([
      playChime({ tones: [1047, 1319, 1568], durationMs: 200, volume: 0.4 }),
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 600)),
    ]);
  }

  vibrate(): boolean {
    return vibrate([200, 100, 200, 100, 300]);
  }
}

/**
 * "Order ready" alert on the waiter's phone: vibration (if supported) plus a chime. Browsers block audio until a user
 * gesture, so the Ready tab shows an "Enable sound" prompt (like the kitchen) until the waiter opts in (persisted per
 * device), and again when the browser suspended audio (e.g. after a reload).
 */
@Injectable({ providedIn: 'root' })
export class WaiterAlerts {
  private readonly storage = inject(SafeStorage);
  private readonly audio = inject(WaiterAudio);

  readonly supported = this.audio.supported;
  private readonly _preference = signal<WaiterSoundPreference>(this.read());
  readonly preference = this._preference.asReadonly();
  private readonly _unlocked = signal(false);

  readonly enabled = computed(() => this.supported && this._preference() === 'on');
  readonly needsPrompt = computed(
    () =>
      this.supported &&
      (this._preference() === null || (this._preference() === 'on' && !this._unlocked())),
  );

  /** Call from a click handler. */
  async enable(): Promise<boolean> {
    this.setPreference('on');
    const ok = await this.audio.unlock();
    this._unlocked.set(ok);
    if (ok) void this.audio.chime();
    return ok;
  }

  disable(): void {
    this.setPreference('off');
  }

  toggle(): void {
    if (this.enabled()) this.disable();
    else void this.enable();
  }

  /** Any tap: silently re-unlock audio when the waiter has opted in. */
  async resumeFromGesture(): Promise<void> {
    if (!this.enabled() || this._unlocked()) return;
    this._unlocked.set(await this.audio.unlock());
  }

  /** Checks (without a gesture) whether audio already runs. */
  async probe(): Promise<void> {
    if (!this.enabled()) return;
    this._unlocked.set(await this.audio.unlock(300));
  }

  /** New READY order: vibrate, and chime when sound is on. */
  async orderReady(): Promise<void> {
    this.audio.vibrate();
    if (!this.enabled()) return;
    this._unlocked.set(await this.audio.chime());
  }

  private setPreference(value: 'on' | 'off'): void {
    this._preference.set(value);
    this.storage.setItem(WAITER_SOUND_KEY, value);
  }

  private read(): WaiterSoundPreference {
    const value = this.storage.getItem(WAITER_SOUND_KEY);
    return value === 'on' || value === 'off' ? value : null;
  }
}
