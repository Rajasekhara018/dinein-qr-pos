import { Injectable, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { BehaviorSubject, Observable, distinctUntilChanged } from 'rxjs';
import {
  HQ_DEFAULT_DATE_FORMAT,
  HQ_FALLBACK_TIMEZONE,
  HQ_FALLBACK_TIMEZONES,
  HQ_USER_TIMEZONE_STORAGE_KEY,
} from './hq-timezone.constants';
import {
  HqLocaleKind,
  describeTimezone,
  detectBrowserTimezone,
  formatTemporal,
  formatTemporalLocale,
  isOffsetTimezone,
  isValidIanaTimezone,
  validTimezoneOrNull,
} from './hq-temporal.helpers';

/**
 * Decides which timezone dates are displayed in and formats them there.
 *
 * Effective timezone, in order: the user's saved timezone; the organization's timezone, only when the organization
 * opt-in flag is on (off by default); the browser's timezone; the platform fallback when the browser reports none.
 * An invalid value is ignored and the next one in the order is used. With no user timezone and the flag off, the
 * browser's timezone is used and every date reads exactly as Angular's DatePipe prints it.
 *
 * Display only: nothing here changes what is sent to or stored by an API.
 */
@Injectable({ providedIn: 'root' })
export class HqTimezoneService {
  private readonly locale = inject(LOCALE_ID);
  private readonly userZone = signal<string | null>(null);
  private readonly organizationZone = signal<string | null>(null);
  private readonly organizationFlag = signal(false);
  private readonly effectiveSubject: BehaviorSubject<string>;
  private zoneList: string[] | null = null;

  /** The browser's own timezone, read once; null when the browser reports none. */
  readonly browserTimezone: string | null = detectBrowserTimezone();

  /** The user's saved timezone, or null when they have none (or it was invalid). */
  readonly userTimezone = this.userZone.asReadonly();
  /** The organization's timezone, or null. Only used while the opt-in flag is on. */
  readonly organizationTimezone = this.organizationZone.asReadonly();
  /** Whether the organization timezone is used when the user has none. Off by default. */
  readonly useOrganizationTimezone = this.organizationFlag.asReadonly();

  /** The timezone dates are shown in. A signal, so templates and computed values follow changes. */
  readonly effectiveTimezone = computed(() => {
    return (
      this.userZone() ??
      (this.organizationFlag() ? this.organizationZone() : null) ??
      this.browserTimezone ??
      HQ_FALLBACK_TIMEZONE
    );
  });

  /** The effective timezone as a stream: emits the current value on subscribe, then only on change. */
  readonly effectiveTimezone$: Observable<string>;

  constructor() {
    this.effectiveSubject = new BehaviorSubject<string>(this.effectiveTimezone());
    this.effectiveTimezone$ = this.effectiveSubject.pipe(distinctUntilChanged());
    this.restoreStoredUserTimezone();
  }

  /** Sets the signed-in user's saved timezone; null, empty or an invalid name clears it. */
  setUserTimezone(zone: string | null | undefined): void {
    const valid = validTimezoneOrNull(zone);
    this.userZone.set(valid);
    this.storeUserTimezone(valid);
    this.publish();
  }

  /** Sets the organization's timezone; null, empty or an invalid name clears it. */
  setOrganizationTimezone(zone: string | null | undefined): void {
    this.organizationZone.set(validTimezoneOrNull(zone));
    this.publish();
  }

  /** Turns the organization-timezone opt-in on or off. */
  setUseOrganizationTimezone(enabled: boolean): void {
    this.organizationFlag.set(!!enabled);
    this.publish();
  }

  /** Forgets the user, the organization and the flag (sign-out). */
  reset(): void {
    this.userZone.set(null);
    this.organizationZone.set(null);
    this.organizationFlag.set(false);
    this.storeUserTimezone(null);
    this.publish();
  }

  /** True for a named IANA zone this browser knows (checked with Intl.DateTimeFormat). */
  isValidTimezone(zone: unknown): boolean {
    return isValidIanaTimezone(zone);
  }

  /**
   * The IANA zones to offer in a picker: the browser's list plus the curated list (browsers list canonical names, so
   * 'Asia/Kolkata' may be missing from theirs), sorted.
   */
  supportedTimezones(): string[] {
    if (!this.zoneList) {
      const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
      let names: string[] = [];
      try {
        names = intl.supportedValuesOf ? intl.supportedValuesOf('timeZone') : [];
      } catch {
        names = [];
      }
      const merged = new Set<string>([...names, ...HQ_FALLBACK_TIMEZONES].filter((name) => isValidIanaTimezone(name)));
      merged.add('UTC');
      merged.add(this.effectiveTimezone());
      this.zoneList = [...merged].sort((a, b) => a.localeCompare(b));
    }
    return this.zoneList;
  }

  /** The timezone that would be effective if the user's saved timezone were `userZone` (null: none). Lets a picker preview a choice before it is saved. */
  effectiveTimezoneFor(userZone: string | null | undefined): string {
    return (
      validTimezoneOrNull(userZone) ??
      (this.organizationFlag() ? this.organizationZone() : null) ??
      this.browserTimezone ??
      HQ_FALLBACK_TIMEZONE
    );
  }

  /** 'Asia/Kolkata (UTC+05:30)' for the effective timezone (or the one given) at an instant (now by default). */
  describe(zone?: string | null, at?: Date): string {
    return describeTimezone(zone || this.effectiveTimezone(), at);
  }

  /**
   * Formats a moment, a zone-less wall-clock value or a date-only value like Angular's DatePipe, in the effective
   * timezone (or `timezone`, an IANA name or a numeric offset). Returns '' for empty or invalid values. Named formats
   * ('short', 'medium', 'shortDate', ...) and custom patterns work as in DatePipe.
   */
  formatDateTime(value: unknown, format: string = HQ_DEFAULT_DATE_FORMAT, locale?: string, timezone?: string | null): string {
    return (
      formatTemporal(value, format, locale || this.locale, this.resolveZone(timezone), this.browserTimezone) ?? ''
    );
  }

  /**
   * Replacement for toLocaleString, toLocaleDateString and toLocaleTimeString: same locale and options, shown in the
   * effective timezone. Returns '' for empty or invalid values.
   */
  formatLocale(
    value: unknown,
    kind: HqLocaleKind,
    options?: Intl.DateTimeFormatOptions,
    locale?: string | string[],
  ): string {
    return formatTemporalLocale(value, kind, options, locale, this.effectiveTimezone(), this.browserTimezone);
  }

  /** The zone a call uses: an explicit override when it is a valid name or offset, otherwise the effective timezone. */
  resolveZone(override?: string | null): string {
    if (override) {
      const trimmed = override.trim();
      if (isOffsetTimezone(trimmed)) {
        return trimmed;
      }
      const named = validTimezoneOrNull(trimmed);
      if (named) {
        return named;
      }
    }
    return this.effectiveTimezone();
  }

  private publish(): void {
    this.effectiveSubject.next(this.effectiveTimezone());
  }

  private restoreStoredUserTimezone(): void {
    try {
      const stored = localStorage.getItem(HQ_USER_TIMEZONE_STORAGE_KEY);
      const valid = validTimezoneOrNull(stored);
      if (valid) {
        this.userZone.set(valid);
        this.effectiveSubject.next(this.effectiveTimezone());
      }
    } catch {
      /* storage unavailable: the profile load sets the value */
    }
  }

  private storeUserTimezone(zone: string | null): void {
    try {
      if (zone) {
        localStorage.setItem(HQ_USER_TIMEZONE_STORAGE_KEY, zone);
      } else {
        localStorage.removeItem(HQ_USER_TIMEZONE_STORAGE_KEY);
      }
    } catch {
      /* storage unavailable: nothing to persist */
    }
  }
}
