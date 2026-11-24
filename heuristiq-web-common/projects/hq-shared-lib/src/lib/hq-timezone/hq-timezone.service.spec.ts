import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HQ_USER_TIMEZONE_STORAGE_KEY } from './hq-timezone.constants';
import { HqTimezoneService } from './hq-timezone.service';

const MOMENT = '2026-10-18T10:00:00Z';
const FORMAT = 'yyyy-MM-dd HH:mm';

function createService(): HqTimezoneService {
  TestBed.resetTestingModule();
  return TestBed.inject(HqTimezoneService);
}

describe('HqTimezoneService', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('defaults to the browser timezone', () => {
    const service = createService();
    expect(service.effectiveTimezone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    expect(service.userTimezone()).toBeNull();
    expect(service.useOrganizationTimezone()).toBe(false);
  });

  describe('precedence', () => {
    it('user timezone wins over the organization and the browser', () => {
      const service = createService();
      service.setOrganizationTimezone('Asia/Dubai');
      service.setUseOrganizationTimezone(true);
      service.setUserTimezone('America/New_York');
      expect(service.effectiveTimezone()).toBe('America/New_York');
    });

    it('ignores the organization timezone while the flag is off', () => {
      const service = createService();
      service.setOrganizationTimezone('Asia/Dubai');
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });

    it('uses the organization timezone when the flag is on and the user has none', () => {
      const service = createService();
      service.setOrganizationTimezone('Asia/Dubai');
      service.setUseOrganizationTimezone(true);
      expect(service.effectiveTimezone()).toBe('Asia/Dubai');
      service.setUseOrganizationTimezone(false);
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });

    it('falls through when the flag is on but the organization has no timezone', () => {
      const service = createService();
      service.setUseOrganizationTimezone(true);
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
      service.setOrganizationTimezone(null);
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });

    it('ignores invalid values and falls through to the next one', () => {
      const service = createService();
      service.setOrganizationTimezone('Asia/Dubai');
      service.setUseOrganizationTimezone(true);
      service.setUserTimezone('Not/AZone');
      expect(service.userTimezone()).toBeNull();
      expect(service.effectiveTimezone()).toBe('Asia/Dubai');
      service.setOrganizationTimezone('also invalid');
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });

    it('clearing the user timezone returns to the default', () => {
      const service = createService();
      service.setUserTimezone('Asia/Kolkata');
      service.setUserTimezone(null);
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
      service.setUserTimezone('Asia/Kolkata');
      service.setUserTimezone('');
      expect(service.userTimezone()).toBeNull();
    });

    it('reset forgets the user, the organization and the flag', () => {
      const service = createService();
      service.setUserTimezone('Asia/Kolkata');
      service.setOrganizationTimezone('Asia/Dubai');
      service.setUseOrganizationTimezone(true);
      service.reset();
      expect(service.userTimezone()).toBeNull();
      expect(service.organizationTimezone()).toBeNull();
      expect(service.useOrganizationTimezone()).toBe(false);
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });
  });

  it('streams the effective timezone and only emits changes', async () => {
    const service = createService();
    const seen: string[] = [];
    const subscription = service.effectiveTimezone$.subscribe((zone) => seen.push(zone));
    service.setUserTimezone('Asia/Kolkata');
    service.setUserTimezone('Asia/Kolkata');
    service.setUserTimezone('Asia/Dubai');
    subscription.unsubscribe();
    expect(seen.at(-1)).toBe('Asia/Dubai');
    expect(seen.filter((zone) => zone === 'Asia/Kolkata').length).toBeLessThanOrEqual(1);
    expect(await firstValueFrom(service.effectiveTimezone$)).toBe('Asia/Dubai');
  });

  describe('validation and listing', () => {
    it('validates zone names', () => {
      const service = createService();
      expect(service.isValidTimezone('Europe/London')).toBe(true);
      expect(service.isValidTimezone('Europe/Nowhere')).toBe(false);
      expect(service.isValidTimezone(undefined)).toBe(false);
    });

    it('lists zones including UTC and the effective zone, sorted', () => {
      const service = createService();
      const zones = service.supportedTimezones();
      expect(zones).toContain('Asia/Kolkata');
      expect(zones).toContain('UTC');
      expect(zones).toContain(service.effectiveTimezone());
      expect(zones).toEqual([...zones].sort((a, b) => a.localeCompare(b)));
    });

    it('previews the effective timezone for a candidate user timezone', () => {
      const service = createService();
      service.setOrganizationTimezone('Asia/Dubai');
      expect(service.effectiveTimezoneFor('Asia/Kolkata')).toBe('Asia/Kolkata');
      expect(service.effectiveTimezoneFor('')).toBe(service.browserTimezone);
      service.setUseOrganizationTimezone(true);
      expect(service.effectiveTimezoneFor(null)).toBe('Asia/Dubai');
      expect(service.effectiveTimezoneFor('Not/AZone')).toBe('Asia/Dubai');
    });

    it('describes a zone with its offset', () => {
      const service = createService();
      expect(service.describe('Asia/Kolkata', new Date(MOMENT))).toBe('Asia/Kolkata (UTC+05:30)');
    });
  });

  describe('formatting', () => {
    it('formats in the effective timezone', () => {
      const service = createService();
      service.setUserTimezone('Asia/Kolkata');
      expect(service.formatDateTime(MOMENT, FORMAT)).toBe('2026-10-18 15:30');
      service.setUserTimezone('America/New_York');
      expect(service.formatDateTime(MOMENT, FORMAT)).toBe('2026-10-18 06:00');
    });

    it('honours an explicit timezone override and ignores an invalid one', () => {
      const service = createService();
      service.setUserTimezone('Asia/Kolkata');
      expect(service.formatDateTime(MOMENT, FORMAT, undefined, 'Asia/Dubai')).toBe('2026-10-18 14:00');
      expect(service.formatDateTime(MOMENT, FORMAT, undefined, '+0100')).toBe('2026-10-18 11:00');
      expect(service.formatDateTime(MOMENT, FORMAT, undefined, 'Not/AZone')).toBe('2026-10-18 15:30');
    });

    it('returns an empty text for empty and invalid values', () => {
      const service = createService();
      expect(service.formatDateTime(null)).toBe('');
      expect(service.formatDateTime('garbage')).toBe('');
    });

    it('does not convert wall-clock values and never shifts date-only values', () => {
      const service = createService();
      service.setUserTimezone('Pacific/Kiritimati');
      expect(service.formatDateTime('2026-10-18T10:00:00', FORMAT)).toBe('2026-10-18 10:00');
      expect(service.formatDateTime('2026-10-18', 'yyyy-MM-dd')).toBe('2026-10-18');
      service.setUserTimezone('Pacific/Pago_Pago');
      expect(service.formatDateTime('2026-10-18', 'yyyy-MM-dd')).toBe('2026-10-18');
    });

    it('formats toLocale-style output in the effective timezone', () => {
      const service = createService();
      service.setUserTimezone('Asia/Kolkata');
      const options: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit', hour12: false };
      expect(service.formatLocale(MOMENT, 'time', options, 'en-GB')).toBe('15:30');
      expect(service.formatLocale(null, 'time')).toBe('');
    });
  });

  describe('persistence', () => {
    it('stores the last user timezone and restores it in a new instance', () => {
      const first = createService();
      first.setUserTimezone('Asia/Dubai');
      expect(localStorage.getItem(HQ_USER_TIMEZONE_STORAGE_KEY)).toBe('Asia/Dubai');
      const second = createService();
      expect(second.userTimezone()).toBe('Asia/Dubai');
      expect(second.effectiveTimezone()).toBe('Asia/Dubai');
    });

    it('removes the stored value when the user timezone is cleared', () => {
      const service = createService();
      service.setUserTimezone('Asia/Dubai');
      service.setUserTimezone(null);
      expect(localStorage.getItem(HQ_USER_TIMEZONE_STORAGE_KEY)).toBeNull();
    });

    it('ignores an invalid stored value', () => {
      localStorage.setItem(HQ_USER_TIMEZONE_STORAGE_KEY, 'garbage');
      const service = createService();
      expect(service.userTimezone()).toBeNull();
      expect(service.effectiveTimezone()).toBe(service.browserTimezone);
    });
  });
});
