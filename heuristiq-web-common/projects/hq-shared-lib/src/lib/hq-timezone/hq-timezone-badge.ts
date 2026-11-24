import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { HqTimezoneService } from './hq-timezone.service';

/**
 * Shows a timezone as 'Asia/Kolkata (UTC+05:30)'. Without an input it shows the effective timezone and follows changes.
 * The text is a zone name and a numeric offset, so nothing in it needs translating.
 */
@Component({
  selector: 'hq-timezone-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="hq-timezone-badge" [attr.title]="label()">{{ label() }}</span>`,
})
export class HqTimezoneBadge {
  private readonly timezones = inject(HqTimezoneService);

  /** A specific IANA zone to describe; the effective timezone when empty. */
  readonly timezone = input<string | null>(null);

  protected readonly label = computed(() => {
    const zone = this.timezone() || this.timezones.effectiveTimezone();
    return this.timezones.describe(zone);
  });
}
