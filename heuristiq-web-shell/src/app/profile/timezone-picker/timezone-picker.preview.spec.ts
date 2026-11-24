import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TIMEZONE_PICKER_EXAMPLE, TIMEZONE_PICKER_PREVIEW_REFRESH_MS } from './timezone-picker.constants';
import { TimezonePicker } from './timezone-picker';

describe('TimezonePicker preview and lifecycle', () => {
  beforeEach(async () => {
    localStorage.clear();
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      declarations: [TimezonePicker],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
    }).compileComponents();
    TestBed.inject(TranslateService).setTranslation('en', {
      timezone: { picker: { preview: 'Now {{time}} in {{zone}}', invalid: 'Use e.g. {{example}}' } },
    });
    TestBed.inject(TranslateService).use('en');
  });
  afterEach(() => vi.useRealTimers());

  function create(value = '') {
    const fixture = TestBed.createComponent(TimezonePicker);
    fixture.componentInstance.value = value;
    fixture.detectChanges();
    return fixture;
  }

  it('words the preview with the time in the chosen zone', () => {
    vi.setSystemTime(new Date('2026-03-10T20:30:00Z'));
    const fixture = create('Asia/Kolkata');
    fixture.componentInstance.now = new Date('2026-03-10T20:30:00Z');
    expect(fixture.componentInstance.previewText).toBe('Now 11 Mar 2026, 02:00 in Asia/Kolkata');
  });

  it('falls back to the device zone in the preview while the text is not a zone', () => {
    const fixture = create('');
    const picker = fixture.componentInstance;
    picker.onType('Mars/Phobos');
    expect(picker.invalid).toBe(true);
    expect(picker.previewZone).toBe(TestBed.inject(HqTimezoneService).effectiveTimezoneFor(null));
    expect(picker.invalidText).toBe(`Use e.g. ${TIMEZONE_PICKER_EXAMPLE}`);
  });

  it('opens the list while typing and closes it shortly after losing focus', () => {
    const picker = create('').componentInstance;
    picker.onType('ko');
    expect(picker.open).toBe(true);
    picker.closeSoon();
    expect(picker.open).toBe(true);
    vi.advanceTimersByTime(150);
    expect(picker.open).toBe(false);
  });

  it('refreshes the preview clock on the timer, and stops after destroy', () => {
    const fixture = create('');
    const picker = fixture.componentInstance;
    picker.now = new Date(0);
    vi.advanceTimersByTime(TIMEZONE_PICKER_PREVIEW_REFRESH_MS);
    expect(picker.now.getTime()).toBeGreaterThan(0);
    fixture.destroy();
    picker.now = new Date(0);
    vi.advanceTimersByTime(TIMEZONE_PICKER_PREVIEW_REFRESH_MS * 2);
    expect(picker.now.getTime()).toBe(0);
  });

  it('follows an outside change of the value but keeps what is being typed when it matches', () => {
    const fixture = create('');
    const picker = fixture.componentInstance;
    picker.value = 'Asia/Dubai';
    picker.ngOnChanges();
    expect(picker.query).toBe('Asia/Dubai');
    expect(picker.options.some((z) => z.includes('Dubai'))).toBe(true);
    picker.query = ' Asia/Dubai ';
    picker.ngOnChanges();
    expect(picker.query).toBe(' Asia/Dubai ');
  });

  it('tracks options by zone name', () => {
    expect(create('').componentInstance.trackZone(0, 'Asia/Dubai')).toBe('Asia/Dubai');
  });
});
