import { computed, inject, Injectable, signal } from '@angular/core';
import { playChime, unlockAudio } from '../../../core/util/alerts';
import { KitchenPrefs } from './kitchen-prefs';

/** Audio seam (WebAudio helpers from core/util/alerts) so tests can fake it. */
@Injectable({ providedIn: 'root' })
export class KitchenAudio {
  readonly supported =
    typeof globalThis !== 'undefined' &&
    !!(
      globalThis.AudioContext ??
      (globalThis as unknown as { webkitAudioContext?: unknown }).webkitAudioContext
    );

  /** Must be called from a user gesture to actually unlock. Never hangs (resume() can stay pending without one). */
  unlock(timeoutMs = 600): Promise<boolean> {
    if (!this.supported) return Promise.resolve(false);
    return Promise.race([
      unlockAudio(),
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), timeoutMs)),
    ]);
  }

  chime(): Promise<boolean> {
    if (!this.supported) return Promise.resolve(false);
    // Three bright tones, loud enough for a noisy kitchen.
    return Promise.race([
      playChime({ tones: [988, 1319, 1760], durationMs: 220, volume: 0.45 }),
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 600)),
    ]);
  }
}

/**
 * New-order chime. Browsers block audio until a user gesture, so the board shows an "Enable sound" prompt until the
 * user opts in (persisted), and again whenever the AudioContext turns out to be suspended (e.g. after a reload).
 */
@Injectable({ providedIn: 'root' })
export class KitchenSound {
  private readonly prefs = inject(KitchenPrefs);
  private readonly audio = inject(KitchenAudio);

  readonly supported = this.audio.supported;
  readonly preference = this.prefs.sound;
  /** True once audio was confirmed running in this page (reset when a chime fails). */
  private readonly _unlocked = signal(false);
  readonly unlocked = this._unlocked.asReadonly();

  readonly enabled = computed(() => this.supported && this.preference() === 'on');
  /** Show the "Enable sound" button: never asked, or opted in but the browser has suspended audio. */
  readonly needsPrompt = computed(
    () =>
      this.supported &&
      (this.preference() === null || (this.preference() === 'on' && !this._unlocked())),
  );

  /** Call from a click handler. */
  async enable(): Promise<boolean> {
    this.prefs.setSound('on');
    const ok = await this.audio.unlock();
    this._unlocked.set(ok);
    if (ok) void this.audio.chime();
    return ok;
  }

  disable(): void {
    this.prefs.setSound('off');
  }

  /** Any user gesture on the board: silently re-unlock audio if the user has opted in. */
  async resumeFromGesture(): Promise<void> {
    if (!this.enabled() || this._unlocked()) return;
    this._unlocked.set(await this.audio.unlock());
  }

  /** Checks (without a gesture) whether audio already runs — e.g. the browser remembered the permission. */
  async probe(): Promise<void> {
    if (!this.enabled()) return;
    this._unlocked.set(await this.audio.unlock(300));
  }

  async newOrder(): Promise<void> {
    if (!this.enabled()) return;
    const ok = await this.audio.chime();
    this._unlocked.set(ok);
  }
}
