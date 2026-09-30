import { describe, expect, it } from 'vitest';
import {
  brandingToForm,
  formatCountdown,
  formToBrandingRequest,
  isHexColor,
  isKioskOnline,
  isLowWhiteContrast,
  KIOSK_DEFAULTS,
  kioskStatusView,
  secondsUntil,
  welcomePreview,
  whiteContrast,
} from './kiosk-logic';

const NOW = Date.parse('2026-09-30T10:00:00Z');
const ago = (ms: number) => new Date(NOW - ms).toISOString();

describe('kiosk online/offline hint', () => {
  it('is online within 5 minutes of the last heartbeat', () => {
    expect(isKioskOnline(ago(30_000), NOW)).toBe(true);
    expect(isKioskOnline(ago(5 * 60_000), NOW)).toBe(true);
  });

  it('is offline after 5 minutes, or when never seen / unparseable', () => {
    expect(isKioskOnline(ago(5 * 60_000 + 1000), NOW)).toBe(false);
    expect(isKioskOnline(null, NOW)).toBe(false);
    expect(isKioskOnline(undefined, NOW)).toBe(false);
    expect(isKioskOnline('not-a-date', NOW)).toBe(false);
  });
});

describe('kioskStatusView', () => {
  it('shows active kiosks with a presence hint', () => {
    expect(kioskStatusView({ status: 'ACTIVE', lastSeenAt: ago(1000) }, NOW)).toEqual({
      label: 'Active',
      tone: 'success',
      presence: 'online',
    });
    expect(kioskStatusView({ status: 'ACTIVE', lastSeenAt: ago(3_600_000) }, NOW).presence).toBe(
      'offline',
    );
  });

  it('shows revoked kiosks without presence', () => {
    expect(kioskStatusView({ status: 'REVOKED', lastSeenAt: ago(1000) }, NOW)).toEqual({
      label: 'Revoked',
      tone: 'neutral',
      presence: null,
    });
  });

  it('distinguishes a live pairing code from an expired one', () => {
    const live = new Date(NOW + 60_000).toISOString();
    const dead = new Date(NOW - 60_000).toISOString();
    expect(
      kioskStatusView({ status: 'PENDING_PAIRING', pairingExpiresAt: live }, NOW),
    ).toMatchObject({
      label: 'Waiting to pair',
      tone: 'warning',
      presence: null,
    });
    expect(
      kioskStatusView({ status: 'PENDING_PAIRING', pairingExpiresAt: dead }, NOW),
    ).toMatchObject({
      label: 'Code expired',
      tone: 'danger',
    });
  });
});

describe('pairing countdown', () => {
  it('counts down and never goes negative', () => {
    expect(secondsUntil(new Date(NOW + 90_000).toISOString(), NOW)).toBe(90);
    expect(secondsUntil(new Date(NOW - 5000).toISOString(), NOW)).toBe(0);
    expect(secondsUntil('nope', NOW)).toBe(0);
  });

  it('formats as m:ss', () => {
    expect(formatCountdown(90)).toBe('1:30');
    expect(formatCountdown(605)).toBe('10:05');
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(-4)).toBe('0:00');
  });
});

describe('colour validation and contrast', () => {
  it('accepts only #RRGGBB', () => {
    expect(isHexColor('#D9480F')).toBe(true);
    expect(isHexColor('#d9480f')).toBe(true);
    expect(isHexColor('D9480F')).toBe(false);
    expect(isHexColor('#FFF')).toBe(false);
    expect(isHexColor('#GG0000')).toBe(false);
    expect(isHexColor('')).toBe(false);
    expect(isHexColor(null)).toBe(false);
  });

  it('computes white-on-colour contrast', () => {
    expect(whiteContrast('#000000')).toBeCloseTo(21, 1);
    expect(whiteContrast('#FFFFFF')).toBeCloseTo(1, 1);
    expect(whiteContrast('bogus')).toBeNull();
  });

  it('warns below 4.5:1 only', () => {
    expect(isLowWhiteContrast('#FFD43B')).toBe(true); // yellow
    expect(isLowWhiteContrast('#FFFFFF')).toBe(true);
    expect(isLowWhiteContrast('#212529')).toBe(false);
    expect(isLowWhiteContrast('#0B5ED7')).toBe(false);
    expect(isLowWhiteContrast('bogus')).toBe(false);
    expect(isLowWhiteContrast(null)).toBe(false);
  });

  it('flags the default primary honestly (it is just under 4.5:1 with white)', () => {
    const ratio = whiteContrast(KIOSK_DEFAULTS.primaryColor) as number;
    expect(isLowWhiteContrast(KIOSK_DEFAULTS.primaryColor)).toBe(ratio < 4.5);
  });
});

describe('welcomePreview fallbacks', () => {
  it('uses the app defaults for blank or invalid values', () => {
    expect(
      welcomePreview({
        primaryColor: '',
        secondaryColor: 'red',
        headline: '  ',
        subtext: '',
        startButtonLabel: '',
      }),
    ).toEqual({
      primaryColor: '#D9480F',
      secondaryColor: '#212529',
      headline: 'Hungry? Order here',
      subtext: 'Fresh, hot and made just for you',
      startButtonLabel: 'Touch to order',
    });
  });

  it('uses entered values', () => {
    expect(
      welcomePreview({
        primaryColor: '#123456',
        secondaryColor: '#abcdef',
        headline: 'Hi',
        subtext: 'There',
        startButtonLabel: 'Go',
      }),
    ).toEqual({
      primaryColor: '#123456',
      secondaryColor: '#abcdef',
      headline: 'Hi',
      subtext: 'There',
      startButtonLabel: 'Go',
    });
  });
});

describe('branding form mapping', () => {
  it('round-trips a server response, keeping existing images by id', () => {
    const form = brandingToForm({
      restaurantName: 'Cafe',
      kioskEnabled: true,
      primaryColor: '#112233',
      secondaryColor: null,
      headline: null,
      subtext: 'Sub',
      startButtonLabel: null,
      idleTimeoutSeconds: 90,
      logoUrl: '/api/v1/images/7',
      backgroundUrl: null,
      logoImageId: 7,
      backgroundImageId: null,
    });
    expect(formToBrandingRequest(form)).toEqual({
      kioskEnabled: true,
      primaryColor: '#112233',
      secondaryColor: null,
      headline: null,
      subtext: 'Sub',
      startButtonLabel: null,
      idleTimeoutSeconds: 90,
      logoImageId: 7,
      backgroundImageId: null,
    });
  });

  it('turns blank fields into nulls, normalises colours and coerces the timeout', () => {
    expect(
      formToBrandingRequest({
        kioskEnabled: false,
        primaryColor: ' #abcdef ',
        secondaryColor: '',
        headline: '  Welcome  ',
        subtext: '',
        startButtonLabel: ' ',
        idleTimeoutSeconds: '120',
        logoImageId: null,
        backgroundImageId: 9,
      }),
    ).toEqual({
      kioskEnabled: false,
      primaryColor: '#ABCDEF',
      secondaryColor: null,
      headline: 'Welcome',
      subtext: null,
      startButtonLabel: null,
      idleTimeoutSeconds: 120,
      logoImageId: null,
      backgroundImageId: 9,
    });
    expect(
      formToBrandingRequest({ ...brandingToForm({} as never), idleTimeoutSeconds: null })
        .idleTimeoutSeconds,
    ).toBeNull();
  });
});
