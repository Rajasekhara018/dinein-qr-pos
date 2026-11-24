import { ChangeDetectorRef, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqSearchableSelect } from './hq-searchable-select';
import { HQ_SEARCHABLE_SELECT_TEXTS, HqSearchableSelectTexts } from './hq-searchable-select.texts';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-searchable-select [formControl]="ctrl" [options]="options" inputId="lang" [hasError]="false"></hq-searchable-select>`,
})
class FormHost {
  ctrl = new FormControl<string | null>(null, Validators.required);
  options = ['Telugu', 'Tamil', 'Hindi', 'Kannada'];
}

function key(k: string): KeyboardEvent {
  return new KeyboardEvent('keydown', { key: k, cancelable: true, bubbles: true });
}

describe('HqSearchableSelect', () => {
  let fixture: ComponentFixture<HqSearchableSelect>;
  let comp: HqSearchableSelect;
  let el: HTMLElement;
  const changes: string[] = [];

  beforeAll(() => {
    if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {};
  });

  function create(inputs: Record<string, unknown> = {}, providers: unknown[] = []): void {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule], providers: providers as never[] });
    fixture = TestBed.createComponent(HqSearchableSelect);
    comp = fixture.componentInstance;
    el = fixture.nativeElement;
    changes.length = 0;
    comp.valueChange.subscribe((v) => changes.push(v));
    fixture.componentRef.setInput('options', ['Telugu', 'Tamil', 'Hindi', 'Kannada']);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  }

  const refresh = () => {
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
  };
  const control = () => el.querySelector<HTMLInputElement>('.hq-ss__control')!;
  const panel = () => document.querySelector<HTMLElement>('.hq-ss-panel');
  const optionTexts = () => Array.from(document.querySelectorAll('.hq-ss-panel__option')).map((n) => n.textContent?.trim());
  const type = (text: string) => {
    control().value = text;
    control().dispatchEvent(new Event('input'));
    refresh();
  };

  afterEach(() => {
    vi.useRealTimers();
    document.querySelectorAll('.cdk-overlay-container').forEach((n) => (n.innerHTML = ''));
  });

  it('opens the WHOLE list on focus, with "Other / not listed" last, and shows a chevron', () => {
    create();
    control().dispatchEvent(new Event('focus'));
    refresh();

    expect(optionTexts()).toEqual(['Telugu', 'Tamil', 'Hindi', 'Kannada', 'Other / not listed']);
    expect(el.querySelector('.hq-ss__chevron')).toBeTruthy();
    expect(control().getAttribute('role')).toBe('combobox');
    expect(control().getAttribute('aria-expanded')).toBe('true');
    expect(panel()?.getAttribute('role')).toBe('listbox');
  });

  it('opens on click as well and filters as you type (case-insensitive contains)', () => {
    create();
    control().click();
    refresh();
    expect(optionTexts()).toHaveLength(5);

    type('TA');
    expect(optionTexts()).toEqual(['Tamil', 'Other / not listed']);
  });

  it('says nothing matches and still offers Other', () => {
    create();
    control().click();
    type('zzz');

    expect(document.querySelector('.hq-ss-panel__empty')?.textContent).toContain('No match — press Enter to use “zzz”');
    expect(optionTexts()).toEqual(['Other / not listed']);
  });

  it('Enter on a typed text that matches nothing uses that text, and a click on the empty-state line does too', () => {
    create();
    control().click();
    type('Tulu');
    control().dispatchEvent(key('Enter'));
    refresh();

    expect(changes).toEqual(['Tulu']);
    expect(comp.value).toBe('Tulu');
    expect(comp.otherMode).toBe(true);
    expect(panel()).toBeNull();

    comp.clear();
    refresh();
    control().click();
    type('Konkani');
    (document.querySelector('.hq-ss-panel__empty') as HTMLElement).click();
    refresh();
    expect(comp.value).toBe('Konkani');
  });

  it('useTyped with nothing typed only closes the list', () => {
    create();
    control().click();
    comp.useTyped();
    expect(changes).toEqual([]);
    expect(comp.open).toBe(false);
  });

  it('highlights the matched text of each row and marks the selected row with a check', () => {
    create();
    comp.writeValue('Tamil');
    refresh();
    control().click();
    refresh();
    expect(document.querySelectorAll('.hq-ss-panel__check').length).toBe(1);
    expect(document.querySelector('.is-selected')?.textContent).toContain('Tamil');

    type('mi');
    const marks = Array.from(document.querySelectorAll('.hq-ss-panel .is-match')).map((m) => m.textContent);
    expect(marks).toEqual(['mi']);
  });

  it('keeps "Other / not listed" as the last row of the list', () => {
    create();
    control().click();
    refresh();
    const last = document.querySelector('.hq-ss-panel')!.lastElementChild!;
    expect(last.classList.contains('is-other')).toBe(true);
  });

  it('on a phone the list is a near full-width sheet', () => {
    const original = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 360 });
    try {
      create();
      control().click();
      expect(comp.panelWidth).toBe(344);
    } finally {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: original });
    }
  });

  it('shows a spinner and a read-only "Loading…" control while the list is on its way, with a loading row in the list', () => {
    create({ loading: true, options: [] });
    type('x');

    expect(control().readOnly).toBe(true);
    expect(control().disabled).toBe(false);
    expect(control().placeholder).toBe('Loading…');
    expect(el.querySelector('.hq-ss__spinner')).toBeTruthy();
    expect(el.querySelector('.hq-ss__chevron')).toBeNull();
    expect(el.querySelector('.hq-ss__notice')).toBeNull();

    control().click();
    refresh();
    expect(document.querySelector('.hq-ss-panel__loading')?.textContent).toContain('Loading…');
    expect(optionTexts()).toEqual([]);
    control().dispatchEvent(key('Enter'));
    expect(changes).toEqual([]);

    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('options', ['Hindi']);
    refresh();
    expect(control().readOnly).toBe(false);
    expect(el.querySelector('.hq-ss__chevron')).toBeTruthy();
  });

  it('opens the list at least as wide as the control, and re-measures when the list is drawn', () => {
    create();
    const field = el.querySelector<HTMLElement>('.hq-ss__field')!;
    vi.spyOn(field, 'getBoundingClientRect').mockReturnValue({ width: 515 } as DOMRect);

    control().click();
    refresh();
    expect(comp.panelWidth).toBe(515);
    expect(document.querySelector<HTMLElement>('.hq-ss-panel')?.style.width).toBe('515px');

    vi.spyOn(field, 'getBoundingClientRect').mockReturnValue({ width: 300 } as DOMRect);
    comp.onPanelAttached(field);
    expect(comp.panelWidth).toBe(300);

    vi.spyOn(field, 'getBoundingClientRect').mockReturnValue({ width: 100 } as DOMRect);
    comp.onPanelAttached(field);
    expect(comp.panelWidth).toBe(220);
  });

  it('a failed list is a free-text box with a notice and a Retry that asks again', () => {
    create({ options: [], loadFailed: true });
    let retries = 0;
    comp.retry.subscribe(() => retries++);

    expect(el.querySelector('.hq-ss__notice')?.textContent).toContain('Could not load the list — type a value.');
    el.querySelector<HTMLButtonElement>('.hq-ss__notice .hq-ss__link')!.click();
    expect(retries).toBe(1);
    type('Anything');
    expect(changes).toEqual(['Anything']);
  });

  it('shows the notice without Retry when the list is empty but did not fail', () => {
    create({ options: [] });
    expect(el.querySelector('.hq-ss__notice')).toBeTruthy();
    expect(el.querySelector('.hq-ss__notice .hq-ss__link')).toBeNull();
  });

  it('picks an option with a click and reports the value', () => {
    create();
    control().click();
    refresh();
    (document.querySelectorAll('.hq-ss-panel__option')[1] as HTMLElement).click();
    refresh();

    expect(comp.value).toBe('Tamil');
    expect(changes).toEqual(['Tamil']);
    expect(control().value).toBe('Tamil');
    expect(panel()).toBeNull();
  });

  it('moves with the arrow keys, picks with Enter and closes with Escape', () => {
    create();
    control().dispatchEvent(key('ArrowDown'));
    refresh();
    expect(comp.open).toBe(true);

    control().dispatchEvent(key('ArrowDown'));
    control().dispatchEvent(key('ArrowDown'));
    refresh();
    expect(control().getAttribute('aria-activedescendant')).toBe(comp.optionId(1));
    control().dispatchEvent(key('ArrowUp'));
    control().dispatchEvent(key('Enter'));
    refresh();
    expect(changes).toEqual(['Telugu']);

    control().dispatchEvent(key('ArrowDown'));
    refresh();
    control().dispatchEvent(key('Escape'));
    refresh();
    expect(comp.open).toBe(false);
  });

  it('Enter with no row, Escape when closed and an unknown key do nothing', () => {
    create();
    control().dispatchEvent(key('Enter'));
    control().dispatchEvent(key('Escape'));
    control().dispatchEvent(key('x'));
    expect(changes).toEqual([]);
    expect(comp.open).toBe(false);
  });

  it('closes on Tab and on blur and marks the control touched', () => {
    create();
    let touched = 0;
    comp.registerOnTouched(() => touched++);
    control().click();
    control().dispatchEvent(key('Tab'));
    expect(comp.open).toBe(false);

    control().click();
    control().dispatchEvent(new Event('blur'));
    expect(comp.open).toBe(false);
    expect(touched).toBe(1);
  });

  it('closes on a click outside, not on a click inside the control', () => {
    create();
    control().click();
    comp.onOutsideClick({ target: control() } as unknown as MouseEvent);
    expect(comp.open).toBe(true);
    comp.onOutsideClick({ target: document.body } as unknown as MouseEvent);
    expect(comp.open).toBe(false);
  });

  it('Other turns the control into a free-text box that reports what is typed and can go back to the list', () => {
    vi.useFakeTimers();
    create();
    control().click();
    refresh();
    (document.querySelectorAll('.hq-ss-panel__option')[4] as HTMLElement).click();
    refresh();
    vi.runAllTimers();

    expect(comp.otherMode).toBe(true);
    expect(changes).toEqual(['']);
    type('Tulu');
    expect(changes[changes.length - 1]).toBe('Tulu');
    expect(el.querySelector('.hq-ss__link')?.textContent).toContain('Choose from the list');

    el.querySelector<HTMLButtonElement>('.hq-ss__link')!.click();
    refresh();
    expect(comp.otherMode).toBe(false);
    expect(comp.value).toBe('');
  });

  it('shows a stored value that is not in the list as free text, and keeps it', () => {
    create();
    comp.writeValue('Reddy');
    refresh();

    expect(comp.otherMode).toBe(true);
    expect(control().value).toBe('Reddy');
    comp.writeValue('telugu');
    refresh();
    expect(comp.otherMode).toBe(false);
    expect(control().value).toBe('telugu');
  });

  it('keeps an unlisted stored value visible when the list arrives after the value', () => {
    create({ options: [] });
    comp.writeValue('Reddy');
    refresh();
    expect(comp.otherMode).toBe(true);

    fixture.componentRef.setInput('options', ['Hindi']);
    refresh();
    expect(comp.otherMode).toBe(true);
    expect(control().value).toBe('Reddy');
  });

  it('is a free-text box with a notice when the list is empty, and no notice for a child list', () => {
    create({ options: [] });
    expect(comp.otherMode).toBe(true);
    expect(el.querySelector('.hq-ss__notice')?.textContent).toContain('Nothing is set up in this list yet');
    expect(el.querySelector('.hq-ss__link')).toBeNull();

    fixture.componentRef.setInput('showUnavailableNotice', false);
    refresh();
    expect(el.querySelector('.hq-ss__notice')).toBeNull();
    type('Anything');
    expect(changes).toEqual(['Anything']);
  });

  it('selects the shown text on focus, so typing replaces a chosen value instead of being added to it', () => {
    create({});
    const input = control() as HTMLInputElement;
    const select = vi.spyOn(input, 'select');
    input.dispatchEvent(new Event('focus'));
    expect(select).toHaveBeenCalled();
  });

  it('without Other offers only the listed values', () => {
    create({ allowOther: false });
    control().click();
    refresh();
    expect(optionTexts()).toEqual(['Telugu', 'Tamil', 'Hindi', 'Kannada']);
  });

  it('clears the value with the clear button', () => {
    create();
    comp.writeValue('Hindi');
    refresh();
    const clear = el.querySelector<HTMLButtonElement>('.hq-ss__clear')!;
    clear.dispatchEvent(new Event('mousedown', { cancelable: true }));
    clear.click();
    refresh();

    expect(comp.value).toBe('');
    expect(changes).toEqual(['']);
    expect(el.querySelector('.hq-ss__clear')).toBeNull();
  });

  it('is not interactive while blocked, shows the hint and never opens', () => {
    create({ blockedHint: 'Choose Country first.' });
    control().click();
    control().dispatchEvent(key('ArrowDown'));
    type('x');
    refresh();

    expect(control().disabled).toBe(true);
    expect(comp.open).toBe(false);
    expect(el.querySelector('.hq-ss__hint')?.textContent).toContain('Choose Country first.');
  });

  it('blocking closes an open list', () => {
    create();
    control().click();
    expect(comp.open).toBe(true);
    fixture.componentRef.setInput('blockedHint', 'Choose X first.');
    refresh();
    expect(comp.open).toBe(false);
  });

  it('is not interactive when disabled', () => {
    create();
    comp.setDisabledState(true);
    refresh();
    control().click();
    control().dispatchEvent(key('ArrowDown'));
    expect(control().disabled).toBe(true);
    expect(comp.open).toBe(false);
    expect(el.querySelector('.hq-ss__clear')).toBeNull();
  });

  it('uses translated texts when the app provides them', () => {
    const texts: HqSearchableSelectTexts = {
      placeholder: () => 'Elige…',
      other: () => 'Otro',
      otherPlaceholder: () => 'Escribe',
      backToList: () => 'Volver',
      clear: () => 'Borrar',
      noMatch: (typed) => `Nada: ${typed}`,
      loading: () => 'Cargando…',
      optionsUnavailable: () => 'Sin lista',
      retry: () => 'Reintentar',
      chooseFirst: (label) => `Elige ${label}`,
    };
    create({}, [{ provide: HQ_SEARCHABLE_SELECT_TEXTS, useValue: texts }]);
    expect(control().placeholder).toBe('Elige…');
    control().click();
    refresh();
    expect(optionTexts()[4]).toBe('Otro');
  });

  describe('as a form control', () => {
    it('writes the control value, reports edits and the required rule', () => {
      TestBed.configureTestingModule({ imports: [FormHost] });
      const host = TestBed.createComponent(FormHost);
      host.detectChanges();
      const input = host.nativeElement.querySelector('.hq-ss__control') as HTMLInputElement;
      expect(input.id).toBe('lang');
      expect(host.componentInstance.ctrl.valid).toBe(false);

      host.componentInstance.ctrl.setValue('Hindi');
      host.detectChanges();
      expect(input.value).toBe('Hindi');

      input.click();
      host.detectChanges();
      (document.querySelectorAll('.hq-ss-panel__option')[0] as HTMLElement).click();
      host.detectChanges();
      expect(host.componentInstance.ctrl.value).toBe('Telugu');
      expect(host.componentInstance.ctrl.valid).toBe(true);

      host.componentInstance.ctrl.setValue(null);
      host.detectChanges();
      expect(input.value).toBe('');
    });

    it('disables with the control', () => {
      TestBed.configureTestingModule({ imports: [FormHost] });
      const host = TestBed.createComponent(FormHost);
      host.detectChanges();
      host.componentInstance.ctrl.disable();
      host.detectChanges();
      expect((host.nativeElement.querySelector('.hq-ss__control') as HTMLInputElement).disabled).toBe(true);
    });
  });
});
