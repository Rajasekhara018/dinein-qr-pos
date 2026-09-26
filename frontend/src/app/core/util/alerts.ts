/**
 * Attention helpers (no audio assets needed). Browsers only allow audio after a user gesture; callers should treat
 * failures as "silently unavailable".
 */

let audioContext: AudioContext | null = null;

function context(): AudioContext | null {
  try {
    const Ctor =
      globalThis.AudioContext ??
      (globalThis as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    audioContext ??= new Ctor();
    return audioContext;
  } catch {
    return null;
  }
}

/**
 * Call from a user gesture (tap) to unlock audio for later programmatic beeps (autoplay policies).
 * Returns whether audio is usable.
 */
export async function unlockAudio(): Promise<boolean> {
  const ctx = context();
  if (!ctx) return false;
  try {
    if (ctx.state === 'suspended') await ctx.resume();
    return ctx.state === 'running';
  } catch {
    return false;
  }
}

/** Plays a short two-tone chime with WebAudio. Resolves false if audio is not permitted/available. */
export async function playChime(options: { tones?: number[]; durationMs?: number; volume?: number } = {}): Promise<boolean> {
  const ctx = context();
  if (!ctx) return false;
  try {
    if (ctx.state === 'suspended') await ctx.resume();
    if (ctx.state !== 'running') return false;
    const tones = options.tones ?? [880, 1320];
    const duration = (options.durationMs ?? 180) / 1000;
    const volume = options.volume ?? 0.25;
    let t = ctx.currentTime;
    for (const freq of tones) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, t);
      gain.gain.exponentialRampToValueAtTime(volume, t + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + duration);
      osc.connect(gain).connect(ctx.destination);
      osc.start(t);
      osc.stop(t + duration + 0.02);
      t += duration * 0.9;
    }
    return true;
  } catch {
    return false;
  }
}

/** `navigator.vibrate` when supported and permitted. */
export function vibrate(pattern: number | number[] = [200, 100, 200]): boolean {
  try {
    return typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function'
      ? navigator.vibrate(pattern)
      : false;
  } catch {
    return false;
  }
}

export function prefersReducedMotion(): boolean {
  try {
    return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  } catch {
    return false;
  }
}
