/** A fresh `Idempotency-Key` (UUID v4, 36 chars — the backend accepts up to 64). */
export function newIdempotencyKey(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  // Fallback for old browsers / insecure contexts: RFC 4122 v4 from getRandomValues.
  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Keeps ONE idempotency key per checkout attempt. The key is reused while the attempt's payload fingerprint is
 * unchanged (double taps, network retries, "Pay" again after dismissing the payment modal — the server then replays
 * the same order), and rotated as soon as the payload changes or {@link reset} is called (e.g. after payment).
 */
export class IdempotencyKeyHolder {
  private key: string | null = null;
  private fingerprint: string | null = null;

  constructor(private readonly generate: () => string = newIdempotencyKey) {}

  keyFor(fingerprint: string): string {
    if (!this.key || this.fingerprint !== fingerprint) {
      this.key = this.generate();
      this.fingerprint = fingerprint;
    }
    return this.key;
  }

  get current(): string | null {
    return this.key;
  }

  reset(): void {
    this.key = null;
    this.fingerprint = null;
  }
}
