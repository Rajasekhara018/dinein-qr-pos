import { DATE_PIPE_DEFAULT_OPTIONS, DatePipeConfig } from '@angular/common';
import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { HQ_DEFAULT_DATE_FORMAT } from './hq-timezone.constants';
import { HqTimezoneService } from './hq-timezone.service';

/**
 * Drop-in replacement for Angular's `date` pipe that shows moments in the user's effective timezone
 * (see HqTimezoneService). Same arguments: `value | hqDate: format : timezoneOverride? : locale?`.
 *
 * Wall-clock strings (no offset) and date-only strings are shown as written, never converted. Empty and invalid values
 * give null (an empty text) instead of throwing. With no user timezone saved it prints exactly what `date` prints.
 *
 * Change detection: the pipe is impure on purpose. A pure pipe is only re-run when its arguments change, so a timezone
 * switch would leave every already-rendered date stale. The impure pipe runs on each check but returns at once from a
 * one-entry cache when the value, format, locale, override and effective timezone are all unchanged, so the cost per
 * cell is a handful of comparisons. Reading the timezone signal inside transform also marks OnPush views dirty when the
 * timezone changes, so those views refresh without any extra wiring.
 */
@Pipe({ name: 'hqDate', standalone: true, pure: false })
export class HqDatePipe implements PipeTransform {
  private readonly timezones = inject(HqTimezoneService);
  private readonly locale = inject(LOCALE_ID);
  private readonly defaults: DatePipeConfig | null = inject(DATE_PIPE_DEFAULT_OPTIONS, { optional: true });

  private lastValue: unknown;
  private lastTime = NaN;
  private lastFormat: string | undefined;
  private lastOverride: string | null | undefined;
  private lastLocale: string | undefined;
  private lastZone = '';
  private lastResult: string | null = null;
  private hasResult = false;

  transform(
    value: unknown,
    format?: string,
    timezone?: string | null,
    locale?: string,
  ): string | null {
    const zone = this.timezones.effectiveTimezone();
    const time = value instanceof Date ? value.getTime() : NaN;
    if (
      this.hasResult &&
      value === this.lastValue &&
      (time === this.lastTime || (time !== time && this.lastTime !== this.lastTime)) &&
      format === this.lastFormat &&
      timezone === this.lastOverride &&
      locale === this.lastLocale &&
      zone === this.lastZone
    ) {
      return this.lastResult;
    }
    const pattern = format ?? this.defaults?.dateFormat ?? HQ_DEFAULT_DATE_FORMAT;
    const text = this.timezones.formatDateTime(value, pattern, locale || this.locale, timezone);
    this.lastValue = value;
    this.lastTime = time;
    this.lastFormat = format;
    this.lastOverride = timezone;
    this.lastLocale = locale;
    this.lastZone = zone;
    this.lastResult = text === '' ? null : text;
    this.hasResult = true;
    return this.lastResult;
  }
}
