import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqTimezoneService } from 'hq-shared-lib';
import { Subscription, timer } from 'rxjs';
import {
  TIMEZONE_PICKER_EXAMPLE,
  TIMEZONE_PICKER_KEYS,
  TIMEZONE_PICKER_MAX_OPTIONS,
  TIMEZONE_PICKER_PREVIEW_FORMAT,
  TIMEZONE_PICKER_PREVIEW_REFRESH_MS,
} from './timezone-picker.constants';
import { filterTimezones } from './timezone-picker.helpers';

/**
 * Searchable timezone choice for the profile form. `value` is an IANA name, or '' to follow the device timezone.
 * Typing emits the text as it stands (the parent validates on save and the picker shows the error as soon as the text
 * is not a zone); choosing from the list or the device button emits a clean value. Display only: nothing is saved here.
 */
@Component({
  selector: 'app-timezone-picker',
  standalone: false,
  templateUrl: './timezone-picker.html',
  styleUrl: './timezone-picker.scss',
})
export class TimezonePicker implements OnInit, OnChanges, OnDestroy {
  private readonly timezones = inject(HqTimezoneService);
  private readonly translate = inject(TranslateService);
  private ticker?: Subscription;

  /** The chosen IANA zone; '' means "use my device timezone". */
  @Input() value = '';
  @Output() readonly valueChange = new EventEmitter<string>();

  readonly example = TIMEZONE_PICKER_EXAMPLE;
  readonly maxOptions = TIMEZONE_PICKER_MAX_OPTIONS;
  query = '';
  open = false;
  now = new Date();
  options: string[] = [];

  private allZones: string[] = [];

  ngOnInit(): void {
    this.allZones = this.timezones.supportedTimezones();
    this.query = this.value;
    this.refreshOptions();
    this.ticker = timer(TIMEZONE_PICKER_PREVIEW_REFRESH_MS, TIMEZONE_PICKER_PREVIEW_REFRESH_MS).subscribe(() => (this.now = new Date()));
  }

  ngOnChanges(): void {
    if (this.value !== this.query.trim()) {
      this.query = this.value;
      this.refreshOptions();
    }
  }

  ngOnDestroy(): void {
    this.ticker?.unsubscribe();
  }

  /** True when the box holds text that is not a known zone. Empty is fine: it means the device timezone. */
  get invalid(): boolean {
    const text = this.query.trim();
    return !!text && !this.timezones.isValidTimezone(text);
  }

  /** The zone dates will be shown in if this choice is saved. */
  get previewZone(): string {
    return this.timezones.effectiveTimezoneFor(this.invalid ? null : this.query.trim());
  }

  get usingDevice(): boolean {
    return !this.query.trim();
  }

  get previewText(): string {
    const zone = this.previewZone;
    const time = this.timezones.formatDateTime(this.now, TIMEZONE_PICKER_PREVIEW_FORMAT, 'en-US', zone);
    return this.translate.instant(TIMEZONE_PICKER_KEYS.preview, { time, zone });
  }

  get invalidText(): string {
    return this.translate.instant(TIMEZONE_PICKER_KEYS.invalid, { example: this.example });
  }

  onType(text: string): void {
    this.query = text;
    this.open = true;
    this.refreshOptions();
    this.valueChange.emit(text.trim());
  }

  choose(zone: string): void {
    this.query = zone;
    this.open = false;
    this.refreshOptions();
    this.now = new Date();
    this.valueChange.emit(zone);
  }

  useDevice(): void {
    this.query = '';
    this.open = false;
    this.refreshOptions();
    this.now = new Date();
    this.valueChange.emit('');
  }

  closeSoon(): void {
    // Let a click on an option land before the list disappears.
    setTimeout(() => (this.open = false), 150);
  }

  trackZone(_: number, zone: string): string {
    return zone;
  }

  private refreshOptions(): void {
    this.options = filterTimezones(this.allZones, this.query, this.maxOptions);
  }
}
