import { contrastRatio, parseHex } from '../../../core/util/color';
import {
  KioskBrandingResponse,
  KioskDeviceResponse,
  UpdateKioskBrandingRequest,
} from '../../../core/api/models';

/** A paired kiosk that has not called home for longer than this is shown as offline. */
export const KIOSK_ONLINE_WINDOW_MS = 5 * 60_000;

/** WCAG AA for normal text. */
export const MIN_TEXT_CONTRAST = 4.5;

/** What the kiosk app itself shows when a branding field is `null` — the preview must fall back identically. */
export const KIOSK_DEFAULTS = {
  primaryColor: '#D9480F',
  secondaryColor: '#212529',
  headline: 'Hungry? Order here',
  subtext: 'Fresh, hot and made just for you',
  startButtonLabel: 'Touch to order',
} as const;

export const IDLE_TIMEOUT_MIN = 15;
export const IDLE_TIMEOUT_MAX = 600;

export const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

export function isHexColor(value: string | null | undefined): boolean {
  return !!value && HEX_COLOR.test(value.trim());
}

/** True when the kiosk has been seen within {@link KIOSK_ONLINE_WINDOW_MS}. Unknown/unparseable = offline. */
export function isKioskOnline(
  lastSeenAt: string | null | undefined,
  now: number = Date.now(),
): boolean {
  if (!lastSeenAt) return false;
  const seen = Date.parse(lastSeenAt);
  if (Number.isNaN(seen)) return false;
  return now - seen <= KIOSK_ONLINE_WINDOW_MS;
}

export type KioskTone = 'success' | 'warning' | 'neutral' | 'danger';

export interface KioskStatusView {
  label: string;
  tone: KioskTone;
  /** Extra "online"/"offline" hint, only for paired kiosks. */
  presence: 'online' | 'offline' | null;
}

export function kioskStatusView(
  device: Pick<KioskDeviceResponse, 'status' | 'lastSeenAt' | 'pairingExpiresAt'>,
  now: number = Date.now(),
): KioskStatusView {
  switch (device.status) {
    case 'ACTIVE':
      return {
        label: 'Active',
        tone: 'success',
        presence: isKioskOnline(device.lastSeenAt, now) ? 'online' : 'offline',
      };
    case 'REVOKED':
      return { label: 'Revoked', tone: 'neutral', presence: null };
    case 'PENDING_PAIRING': {
      const expires = device.pairingExpiresAt ? Date.parse(device.pairingExpiresAt) : NaN;
      const expired = !Number.isNaN(expires) && expires <= now;
      return {
        label: expired ? 'Code expired' : 'Waiting to pair',
        tone: expired ? 'danger' : 'warning',
        presence: null,
      };
    }
  }
}

/** Seconds left until `expiresAt` (never negative). */
export function secondsUntil(expiresAt: string, now: number = Date.now()): number {
  const t = Date.parse(expiresAt);
  if (Number.isNaN(t)) return 0;
  return Math.max(0, Math.ceil((t - now) / 1000));
}

/** `m:ss` */
export function formatCountdown(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** WCAG contrast of white text on `hex`; null for an invalid colour. */
export function whiteContrast(hex: string | null | undefined): number | null {
  if (!hex) return null;
  const rgb = parseHex(hex);
  return rgb ? contrastRatio(rgb, [255, 255, 255]) : null;
}

/** True when white text on `hex` would be below 4.5:1. Invalid colours are not "low contrast" (they are invalid). */
export function isLowWhiteContrast(hex: string | null | undefined): boolean {
  const ratio = whiteContrast(hex);
  return ratio !== null && ratio < MIN_TEXT_CONTRAST;
}

/** Image id from a relative image URL such as `/api/v1/images/42` or `/api/v1/images/42/thumb`. */
export function imageIdFromUrl(url: string | null | undefined): number | null {
  if (!url) return null;
  const match = /\/images\/(\d+)(?:\/|\?|$)/.exec(url);
  return match ? Number(match[1]) : null;
}

/** Form-level values of the appearance page, all strings so empty means "use the default". */
export interface BrandingFormValue {
  kioskEnabled: boolean;
  primaryColor: string;
  secondaryColor: string;
  headline: string;
  subtext: string;
  startButtonLabel: string;
  idleTimeoutSeconds: number | string | null;
  logoImageId: number | null;
  backgroundImageId: number | null;
}

export function brandingToForm(
  b: KioskBrandingResponse,
): BrandingFormValue & { idleTimeoutSeconds: number | null } {
  return {
    kioskEnabled: b.kioskEnabled,
    primaryColor: b.primaryColor ?? '',
    secondaryColor: b.secondaryColor ?? '',
    headline: b.headline ?? '',
    subtext: b.subtext ?? '',
    startButtonLabel: b.startButtonLabel ?? '',
    idleTimeoutSeconds: b.idleTimeoutSeconds,
    logoImageId: imageIdFromUrl(b.logoUrl),
    backgroundImageId: imageIdFromUrl(b.backgroundUrl),
  };
}

export function formToBrandingRequest(v: BrandingFormValue): UpdateKioskBrandingRequest {
  const text = (s: string) => s.trim() || null;
  const color = (s: string) =>
    s.trim()
      ? s
          .trim()
          .toUpperCase()
          .replace(/^(?!#)/, '#')
      : null;
  const timeoutRaw = v.idleTimeoutSeconds;
  const timeout = timeoutRaw === null || timeoutRaw === '' ? null : Number(timeoutRaw);
  return {
    kioskEnabled: v.kioskEnabled,
    primaryColor: color(v.primaryColor),
    secondaryColor: color(v.secondaryColor),
    headline: text(v.headline),
    subtext: text(v.subtext),
    startButtonLabel: text(v.startButtonLabel),
    idleTimeoutSeconds: timeout !== null && Number.isFinite(timeout) ? timeout : null,
    logoImageId: v.logoImageId,
    backgroundImageId: v.backgroundImageId,
  };
}

export interface WelcomePreview {
  primaryColor: string;
  secondaryColor: string;
  headline: string;
  subtext: string;
  startButtonLabel: string;
}

/** Effective welcome-screen values: blank or invalid entries fall back to the app defaults. */
export function welcomePreview(
  v: Pick<
    BrandingFormValue,
    'primaryColor' | 'secondaryColor' | 'headline' | 'subtext' | 'startButtonLabel'
  >,
): WelcomePreview {
  const colour = (value: string, fallback: string) => (isHexColor(value) ? value.trim() : fallback);
  return {
    primaryColor: colour(v.primaryColor, KIOSK_DEFAULTS.primaryColor),
    secondaryColor: colour(v.secondaryColor, KIOSK_DEFAULTS.secondaryColor),
    headline: v.headline.trim() || KIOSK_DEFAULTS.headline,
    subtext: v.subtext.trim() || KIOSK_DEFAULTS.subtext,
    startButtonLabel: v.startButtonLabel.trim() || KIOSK_DEFAULTS.startButtonLabel,
  };
}
