import { DatePipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HqDatePipe } from './hq-date.pipe';
import { HqTimezoneService } from './hq-timezone.service';

const LOCALE = 'en-US';

/** Values a screen can hand to the pipe. DatePipe throws for the invalid ones; the hqDate pipe answers null. */
const VALID_INPUTS: ReadonlyArray<[string, unknown]> = [
  ['ISO Z', '2026-10-18T10:00:00Z'],
  ['ISO Z with millis', '2026-03-08T07:30:00.123Z'],
  ['ISO Z around the NY fall-back', '2026-11-01T05:30:00Z'],
  ['ISO Z around the NY spring-forward', '2026-03-08T06:59:59Z'],
  ['ISO +05:30', '2026-10-18T10:00:00+05:30'],
  ['ISO -0400', '2026-10-18T10:00:00-0400'],
  ['compact ISO Z', '20261018T100000Z'],
  ['epoch ms', 1760781600000],
  ['epoch ms string', '1760781600000'],
  ['Date', new Date(1760781600000)],
  ['wall-clock', '2026-10-18T10:00:00'],
  ['wall-clock with millis', '2026-10-18T10:00:00.250'],
  ['wall-clock hours and minutes', '2026-10-18T23:59'],
  ['date-only', '2026-10-18'],
  ['year and month', '2026-10'],
  ['year', '2026'],
  ['first day', '2026-01-01'],
  ['last day', '2026-12-31'],
];

const EMPTY_INPUTS: ReadonlyArray<[string, unknown]> = [
  ['null', null],
  ['undefined', undefined],
  ['empty string', ''],
  ['NaN', NaN],
];

const FORMATS: ReadonlyArray<string | undefined> = [
  undefined,
  'short',
  'medium',
  'long',
  'full',
  'shortDate',
  'mediumDate',
  'longDate',
  'fullDate',
  'shortTime',
  'mediumTime',
  'longTime',
  'dd MMM yyyy, HH:mm',
  'MMM dd, yyyy',
  'dd MMM, HH:mm',
  'yyyy-MM-dd HH:mm:ss.SSS z',
  'EEEE h:mm a',
];

function createPipe(): HqDatePipe {
  return TestBed.runInInjectionContext(() => new HqDatePipe());
}

describe('HqDatePipe parity with DatePipe when no user timezone is saved', () => {
  const reference = new DatePipe(LOCALE);

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('runs in the timezone requested through the TZ environment variable', () => {
    // The matrix is meant to be run with TZ=Asia/Kolkata and TZ=America/New_York; this proves the runner honoured it.
    const july = new Date('2026-07-01T00:00:00Z').getTimezoneOffset();
    const expectedByZone: Record<string, number> = { 'Asia/Kolkata': -330, 'America/New_York': 240 };
    const nodeProcess = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process;
    const requested = nodeProcess?.env?.['TZ'];
    if (requested && requested in expectedByZone) {
      expect(july).toBe(expectedByZone[requested]);
    }
  });

  for (const [label, value] of VALID_INPUTS) {
    for (const format of FORMATS) {
      it(`${label} / ${format ?? 'default'}`, () => {
        const pipe = createPipe();
        expect(pipe.transform(value, format, undefined, LOCALE)).toBe(reference.transform(value as string, format));
      });
    }
  }

  for (const [label, value] of EMPTY_INPUTS) {
    it(`${label} gives null like DatePipe`, () => {
      const pipe = createPipe();
      expect(pipe.transform(value, 'medium', undefined, LOCALE)).toBeNull();
      expect(reference.transform(value as string, 'medium')).toBeNull();
    });
  }

  it('garbage: DatePipe throws, hqDate answers null', () => {
    const pipe = createPipe();
    expect(() => reference.transform('garbage', 'medium')).toThrow();
    expect(pipe.transform('garbage', 'medium', undefined, LOCALE)).toBeNull();
  });

  it('keeps the same output when the saved zone equals the browser zone', () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setUserTimezone(service.browserTimezone);
    const pipe = createPipe();
    for (const [, value] of VALID_INPUTS) {
      for (const format of FORMATS) {
        expect(pipe.transform(value, format, undefined, LOCALE)).toBe(reference.transform(value as string, format));
      }
    }
  });

  it('keeps the same output when only the organization timezone is set', () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setOrganizationTimezone('Pacific/Kiritimati');
    const pipe = createPipe();
    expect(pipe.transform('2026-10-18T10:00:00Z', 'medium', undefined, LOCALE)).toBe(reference.transform('2026-10-18T10:00:00Z', 'medium'));
  });
});

describe('HqDatePipe with a user timezone', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('converts moments and leaves wall-clock and date-only values alone', () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setUserTimezone('Asia/Kolkata');
    const pipe = createPipe();
    expect(pipe.transform('2026-10-18T10:00:00Z', 'yyyy-MM-dd HH:mm', undefined, LOCALE)).toBe('2026-10-18 15:30');
    expect(pipe.transform('2026-10-18T10:00:00', 'yyyy-MM-dd HH:mm', undefined, LOCALE)).toBe('2026-10-18 10:00');
    expect(pipe.transform('2026-10-18', 'yyyy-MM-dd', undefined, LOCALE)).toBe('2026-10-18');
  });

  it('follows a timezone change on the same pipe instance', () => {
    const service = TestBed.inject(HqTimezoneService);
    const pipe = createPipe();
    service.setUserTimezone('Asia/Kolkata');
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', undefined, LOCALE)).toBe('15:30');
    service.setUserTimezone('Asia/Dubai');
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', undefined, LOCALE)).toBe('14:00');
    service.setUserTimezone(null);
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', undefined, LOCALE)).not.toBeNull();
  });

  it('uses the per-call timezone override', () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setUserTimezone('Asia/Kolkata');
    const pipe = createPipe();
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', 'UTC', LOCALE)).toBe('10:00');
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', '+0100', LOCALE)).toBe('11:00');
    expect(pipe.transform('2026-10-18T10:00:00Z', 'HH:mm', 'Not/AZone', LOCALE)).toBe('15:30');
  });

  it('notices a mutated Date object', () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setUserTimezone('UTC');
    const pipe = createPipe();
    const date = new Date(Date.UTC(2026, 9, 18, 10));
    expect(pipe.transform(date, 'HH:mm', undefined, LOCALE)).toBe('10:00');
    date.setTime(Date.UTC(2026, 9, 18, 11));
    expect(pipe.transform(date, 'HH:mm', undefined, LOCALE)).toBe('11:00');
  });
});

@Component({
  selector: 'hq-test-host',
  standalone: true,
  imports: [HqDatePipe],
  template: `<span id="out">{{ value() | hqDate: 'HH:mm' }}</span>`,
})
class HostComponent {
  readonly value = signal('2026-10-18T10:00:00Z');
}

describe('HqDatePipe in a template', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('refreshes the rendered text when the timezone changes', async () => {
    const service = TestBed.inject(HqTimezoneService);
    service.setUserTimezone('Asia/Kolkata');
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#out').textContent).toBe('15:30');
    service.setUserTimezone('America/New_York');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#out').textContent).toBe('06:00');
  });
});
