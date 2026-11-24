import { ChangeDetectorRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { HqSharedLibModule } from '../../hq-shared-lib.module';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `
    <hq-date-input id="d" [formControl]="date" [min]="min" [max]="max"></hq-date-input>
    <hq-time-input id="t" [formControl]="time" [minuteStep]="15"></hq-time-input>
    <hq-datetime-input id="dt" [formControl]="both"></hq-datetime-input>
  `,
})
class Host {
  date = new FormControl<string | null>(null);
  time = new FormControl<string | null>(null);
  both = new FormControl<string | null>(null);
  min: string | null = null;
  max: string | null = null;
}

function setup() {
  TestBed.configureTestingModule({ imports: [Host] });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const refresh = () => {
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
  };
  const box = (id: string) => el.querySelector(`#${id}`) as HTMLElement;
  const input = (id: string) => box(id).querySelector('input.hq-dt__control') as HTMLInputElement;
  const toggle = (id: string) => box(id).querySelector('button.hq-dt__toggle') as HTMLButtonElement;
  const type = (id: string, text: string) => {
    input(id).value = text;
    input(id).dispatchEvent(new Event('input'));
    refresh();
  };
  const panel = () => document.querySelector('.hq-dt-panel') as HTMLElement | null;
  const days = () => Array.from(document.querySelectorAll<HTMLButtonElement>('.hq-dt-panel .hq-dt-cal__day'));
  return { fixture, host: fixture.componentInstance, el, refresh, input, toggle, type, panel, days };
}

describe('HqDateTimeInput', () => {
  describe('the three tags', () => {
    it('shows each field with the placeholder of its kind', () => {
      const t = setup();

      expect(t.input('d').getAttribute('placeholder')).toBe('dd-mm-yyyy');
      expect(t.input('t').getAttribute('placeholder')).toBe('hh:mm');
      expect(t.input('dt').getAttribute('placeholder')).toBe('dd-mm-yyyy hh:mm');
    });
  });

  describe('a date field', () => {
    it('shows the form value as dd-mm-yyyy', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14');
      t.refresh();

      expect(t.input('d').value).toBe('14-11-2026');
    });

    it('gives the form the same text a native date input does', () => {
      const t = setup();

      t.type('d', '07-03-2026');

      expect(t.host.date.value).toBe('2026-03-07');
    });

    it('gives the form nothing while the typed day is incomplete, and marks the field', () => {
      const t = setup();
      t.type('d', '07-03-2026');
      expect(t.host.date.value).toBe('2026-03-07');

      t.type('d', '07-03-20');

      expect(t.host.date.value).toBeNull();
      expect(t.input('d').getAttribute('aria-invalid')).toBe('true');
    });

    it('gives the form nothing for a day that does not exist', () => {
      const t = setup();

      t.type('d', '30-02-2026');

      expect(t.host.date.value).toBeNull();
    });

    it('clears the form value when the text is emptied', () => {
      const t = setup();
      t.type('d', '07-03-2026');

      t.type('d', '');

      expect(t.host.date.value).toBeNull();
      expect(t.input('d').getAttribute('aria-invalid')).toBeNull();
    });

    it('opens a calendar on the month of the value, and picking a day sets it and closes the calendar', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14');
      t.refresh();

      t.toggle('d').click();
      t.refresh();

      expect(t.panel()).not.toBeNull();
      expect(t.days()).toHaveLength(42);
      expect(document.querySelector('.hq-dt-cal__title')?.textContent).toContain('2026');
      const picked = t.days().find((button) => button.dataset['iso'] === '2026-11-20')!;
      picked.click();
      t.refresh();

      expect(t.host.date.value).toBe('2026-11-20');
      expect(t.input('d').value).toBe('20-11-2026');
      expect(t.panel()).toBeNull();
    });

    it('marks the selected day, and does not let a day outside the range be picked', () => {
      const t = setup();
      t.host.min = '2026-11-10';
      t.host.max = '2026-11-20';
      t.host.date.setValue('2026-11-14');
      t.refresh();

      t.toggle('d').click();
      t.refresh();

      expect(t.days().find((b) => b.dataset['iso'] === '2026-11-14')!.classList.contains('is-selected')).toBe(true);
      const early = t.days().find((b) => b.dataset['iso'] === '2026-11-09')!;
      const late = t.days().find((b) => b.dataset['iso'] === '2026-11-21')!;
      expect(early.disabled).toBe(true);
      expect(late.disabled).toBe(true);
      expect(t.days().find((b) => b.dataset['iso'] === '2026-11-10')!.disabled).toBe(false);
    });

    it('moves the calendar by a month with the arrows', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14');
      t.refresh();
      t.toggle('d').click();
      t.refresh();
      const title = () => document.querySelector('.hq-dt-cal__title')?.textContent ?? '';
      const before = title();

      (document.querySelectorAll('.hq-dt-cal__nav')[1] as HTMLButtonElement).click();
      t.refresh();

      expect(title()).not.toBe(before);
      expect(t.days().some((b) => b.dataset['iso'] === '2026-12-15')).toBe(true);
    });

    it('clears the value from the calendar', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14');
      t.refresh();
      t.toggle('d').click();
      t.refresh();

      (document.querySelector('.hq-dt-panel__foot .hq-dt-panel__link') as HTMLButtonElement).click();
      t.refresh();

      expect(t.host.date.value).toBeNull();
      expect(t.input('d').value).toBe('');
      expect(t.panel()).toBeNull();
    });

    it('picks today from the calendar', () => {
      const t = setup();
      t.toggle('d').click();
      t.refresh();

      (document.querySelectorAll('.hq-dt-panel__foot .hq-dt-panel__link')[1] as HTMLButtonElement).click();
      t.refresh();

      const now = new Date();
      const iso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
      expect(t.host.date.value).toBe(iso);
    });

    it('closes the calendar with Escape in the box', () => {
      const t = setup();
      t.toggle('d').click();
      t.refresh();
      expect(t.panel()).not.toBeNull();

      t.input('d').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      t.refresh();

      expect(t.panel()).toBeNull();
    });

    it('moves the focused day with the arrow keys', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14');
      t.refresh();
      t.toggle('d').click();
      t.refresh();
      const grid = document.querySelector('.hq-dt-cal__grid') as HTMLElement;

      grid.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      t.refresh();

      const focusable = t.days().find((b) => b.getAttribute('tabindex') === '0')!;
      expect(focusable.dataset['iso']).toBe('2026-11-15');
    });
  });

  describe('a time field', () => {
    it('shows and reads a time', () => {
      const t = setup();
      t.host.time.setValue('09:05');
      t.refresh();
      expect(t.input('t').value).toBe('09:05');

      t.type('t', '18:30');

      expect(t.host.time.value).toBe('18:30');
    });

    it('reads a time typed without the colon, and nothing from a time that is not on the clock', () => {
      const t = setup();

      t.type('t', '0905');
      expect(t.host.time.value).toBe('09:05');

      t.type('t', '25:00');
      expect(t.host.time.value).toBeNull();
    });

    it('picks the hour and the minute from the clock, in the minute step', () => {
      const t = setup();
      t.toggle('t').click();
      t.refresh();
      const columns = document.querySelectorAll('.hq-dt-clock__column');
      const hours = Array.from(columns[0].querySelectorAll<HTMLButtonElement>('.hq-dt-clock__cell'));
      const minutes = Array.from(columns[1].querySelectorAll<HTMLButtonElement>('.hq-dt-clock__cell'));

      expect(hours).toHaveLength(24);
      expect(minutes.map((b) => b.textContent?.trim())).toEqual(['00', '15', '30', '45']);

      hours[14].click();
      t.refresh();
      minutes[2].click();
      t.refresh();

      expect(t.host.time.value).toBe('14:30');
      expect(t.input('t').value).toBe('14:30');
    });

    it('has no calendar, and a Now button', () => {
      const t = setup();
      t.toggle('t').click();
      t.refresh();

      expect(document.querySelector('.hq-dt-cal')).toBeNull();
      expect(document.querySelectorAll('.hq-dt-panel__foot .hq-dt-panel__link')[1].textContent).toContain('Now');
    });
  });

  describe('a date and time field', () => {
    it('shows and reads both', () => {
      const t = setup();
      t.host.both.setValue('2026-11-14T12:30');
      t.refresh();
      expect(t.input('dt').value).toBe('14-11-2026 12:30');

      t.type('dt', '01-02-2027 08:15');

      expect(t.host.both.value).toBe('2027-02-01T08:15');
    });

    it('gives the form nothing until the time is typed too', () => {
      const t = setup();

      t.type('dt', '01-02-2027');

      expect(t.host.both.value).toBeNull();
    });

    it('stays open after a day is picked so the time can be chosen, and gives a complete value at once', () => {
      const t = setup();
      t.toggle('dt').click();
      t.refresh();

      t.days().find((b) => b.dataset['iso'] === '2026-11-20')!.click();
      t.refresh();

      expect(t.panel()).not.toBeNull();
      expect(t.host.both.value).toBe('2026-11-20T00:00');
    });

    it('keeps the day when the time is changed, and closes on Done', () => {
      const t = setup();
      t.host.both.setValue('2026-11-20T00:00');
      t.refresh();
      t.toggle('dt').click();
      t.refresh();
      const hours = Array.from(document.querySelectorAll('.hq-dt-clock__column')[0].querySelectorAll<HTMLButtonElement>('.hq-dt-clock__cell'));

      hours[9].click();
      t.refresh();
      expect(t.host.both.value).toBe('2026-11-20T09:00');

      (document.querySelector('.hq-dt-panel__done') as HTMLButtonElement).click();
      t.refresh();
      expect(t.panel()).toBeNull();
    });
  });

  describe('as a form control', () => {
    it('is disabled with the form control, and opens no calendar', () => {
      const t = setup();
      t.host.date.disable();
      t.refresh();

      expect(t.input('d').disabled).toBe(true);
      expect(t.toggle('d').disabled).toBe(true);
    });

    it('marks the form control touched when the box loses focus', () => {
      const t = setup();
      expect(t.host.date.touched).toBe(false);

      t.input('d').dispatchEvent(new Event('blur'));

      expect(t.host.date.touched).toBe(true);
    });

    it('takes a value written by the form that has the other kind of text, for the day it holds', () => {
      const t = setup();
      t.host.date.setValue('2026-11-14T10:00');
      t.refresh();

      expect(t.input('d').value).toBe('14-11-2026');
    });

    it('shows an empty box for a value it cannot read', () => {
      const t = setup();
      t.host.date.setValue('not a date');
      t.refresh();

      expect(t.input('d').value).toBe('');
    });
  });
});
