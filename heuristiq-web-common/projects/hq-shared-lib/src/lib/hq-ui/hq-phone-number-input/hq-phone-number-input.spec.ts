import { ChangeDetectorRef, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HQ_PHONE_DEFAULT_COUNTRY } from './hq-phone-number-input.constants';
import { HqPhoneNumberInput } from './hq-phone-number-input';
import { HQ_PHONE_NUMBER_INPUT_TEXTS, HqPhoneNumberInputTexts } from './hq-phone-number-input.texts';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-phone-number-input [formControl]="ctrl" [isRequired]="required" label="Mobile"></hq-phone-number-input>`,
})
class FormHost {
  ctrl = new FormControl<string | null>(null);
  required = false;
}

function key(k: string): KeyboardEvent {
  return new KeyboardEvent('keydown', { key: k, cancelable: true });
}

describe('HqPhoneNumberInput', () => {
  let fixture: ComponentFixture<HqPhoneNumberInput>;
  let comp: HqPhoneNumberInput;
  let el: HTMLElement;

  beforeAll(() => {
    // jsdom does not implement element scrolling.
    if (!Element.prototype.scrollTo) Element.prototype.scrollTo = () => {};
    if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {};
  });

  function create(providers: unknown[] = [], inputs: Record<string, unknown> = {}): void {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule], providers: providers as never[] });
    fixture = TestBed.createComponent(HqPhoneNumberInput);
    comp = fixture.componentInstance;
    el = fixture.nativeElement;
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  }

  /** Zoneless change detection only refreshes views marked dirty, so state changed from the test is marked first. */
  const refresh = () => {
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
  };
  const q = <E extends HTMLElement = HTMLElement>(sel: string) => el.querySelector<E>(sel);
  const numberInput = () => q<HTMLInputElement>('.phone-number-input__control')!;
  const trigger = () => q<HTMLButtonElement>('.phone-number-input__country')!;
  const panel = () => document.querySelector<HTMLElement>('.hq-phone-panel');
  const type = (value: string) => {
    numberInput().value = value;
    numberInput().dispatchEvent(new Event('input'));
    refresh();
  };

  afterEach(() => vi.useRealTimers());

  describe('defaults', () => {
    beforeEach(() => create());

    it('starts on India with the English label and placeholder', () => {
      expect(comp.selectedCountry).toBe('IN');
      expect(comp.defaultCountry).toBe('IN');
      expect(q('.phone-number-input__label')?.textContent?.trim()).toBe('Phone number');
      expect(numberInput().placeholder).toBe('Phone number');
      expect(q('.phone-number-input__dial')?.textContent).toBe('+91');
      expect(q('.phone-number-input__flag')?.textContent).toBe(comp.selectedFlag);
      expect(trigger().getAttribute('aria-label')).toBe('Country code: India +91');
      expect(trigger().getAttribute('title')).toBe('India');
      expect(q('.phone-number-input__required')).toBeNull();
      expect(q('.phone-number-input__error')).toBeNull();
    });

    it('emits the international form while typing, digits only', () => {
      const changes: string[] = [];
      comp.registerOnChange((v) => changes.push(v));
      type('98765-43210');
      expect(comp.nationalDigits).toBe('9876543210');
      expect(changes).toEqual(['+919876543210']);
      expect(comp.value).toBe('+919876543210');
      expect(comp.validate()).toBeNull();
    });

    it('caps the national digits so the full number fits E.164', () => {
      type('12345678901234567890');
      expect(comp.nationalDigits).toBe('1234567890123');
    });

    it('splits a pasted international number into country and digits', () => {
      type(' +44 20 7946 0958 ');
      expect(comp.selectedCountry).toBe('GB');
      expect(comp.nationalDigits).toBe('2079460958');
      expect(comp.value).toBe('+442079460958');
      expect(q('.phone-number-input__dial')?.textContent).toBe('+44');
    });

    it('reports a pattern error for an invalid number and shows it once touched', () => {
      type('12345');
      expect(comp.validate()).toEqual({ pattern: true });
      expect(comp.hasError).toBe(false);
      expect(comp.errorMessage).toBe('');
      const touched = vi.fn();
      comp.registerOnTouched(touched);
      numberInput().dispatchEvent(new Event('blur'));
      refresh();
      expect(touched).toHaveBeenCalled();
      expect(q('.phone-number-input__error')?.textContent).toBe('Enter a valid phone number.');
      expect(q('.phone-number-input__field')?.classList).toContain('has-error');
    });

    it('is valid when empty and optional, and shows no message for a valid touched number', () => {
      expect(comp.validate()).toBeNull();
      comp.onBlur();
      expect(comp.errorMessage).toBe('');
      type('9876543210');
      expect(comp.errorMessage).toBe('');
    });

    it('returns no message for an error it does not know', () => {
      comp.touched = true;
      vi.spyOn(comp, 'validate').mockReturnValue({ other: true });
      expect(comp.errorMessage).toBe('');
    });

    it('reads a written value, including a legacy bare mobile and an empty value', () => {
      comp.writeValue('+442079460958');
      expect([comp.selectedCountry, comp.nationalDigits]).toEqual(['GB', '2079460958']);
      comp.writeValue('9876543210');
      expect([comp.selectedCountry, comp.nationalDigits]).toEqual(['IN', '9876543210']);
      comp.writeValue(null);
      expect(comp.nationalDigits).toBe('');
      expect(comp.value).toBe('');
    });

    it('tracks countries by ISO code', () => {
      expect(comp.trackCountry(0, { iso: 'FR', name: 'France', callingCode: '33', flag: '' })).toBe('FR');
    });
  });

  describe('inputs', () => {
    it('shows the caller label, placeholder, required star and compact variant', () => {
      create([], { label: 'Mobile', placeholder: '98765 43210', isRequired: true, variant: 'compact' });
      expect(q('.phone-number-input__label')?.textContent).toContain('Mobile');
      expect(q('.phone-number-input__required')?.textContent).toBe('*');
      expect(numberInput().placeholder).toBe('98765 43210');
      expect(q('.phone-number-input')?.classList).toContain('is-compact');
      expect(comp.validate()).toEqual({ required: true });
      comp.onBlur();
      refresh();
      expect(q('.phone-number-input__error')?.textContent).toBe('Mobile is required.');
    });

    it('hides the label when showLabel is false', () => {
      create([], { showLabel: false });
      expect(q('.phone-number-input__label')).toBeNull();
    });

    it('starts on the defaultCountry input, but does not move a number already typed', () => {
      create([], { defaultCountry: 'gb' });
      expect(comp.selectedCountry).toBe('GB');
      expect(comp.defaultCountry).toBe('GB');
      fixture.componentRef.setInput('defaultCountry', null);
      expect(comp.selectedCountry).toBe('IN');
      type('9876543210');
      fixture.componentRef.setInput('defaultCountry', 'US');
      expect(comp.selectedCountry).toBe('IN');
      expect(comp.defaultCountry).toBe('US');
    });

    it('takes an initial value outside a form', () => {
      create([], { value: '+12025550123' });
      expect(comp.selectedCountry).toBe('US');
      expect(comp.nationalDigits).toBe('2025550123');
      expect(numberInput().value).toBe('2025550123');
    });

    it('uses the app default country from HQ_PHONE_DEFAULT_COUNTRY', () => {
      create([{ provide: HQ_PHONE_DEFAULT_COUNTRY, useValue: 'gb' }]);
      expect(comp.selectedCountry).toBe('GB');
      comp.writeValue('020 7946 0958');
      expect(comp.value).toBe('+442079460958');
    });

    it('uses app texts, falling back to English for the optional ones it leaves out', () => {
      const texts: HqPhoneNumberInputTexts = {
        label: () => 'Téléphone',
        placeholder: () => 'Numéro',
        required: (l) => `${l} requis`,
        invalid: () => 'Numéro invalide',
      };
      create([{ provide: HQ_PHONE_NUMBER_INPUT_TEXTS, useValue: texts }], { isRequired: true });
      expect(q('.phone-number-input__label')?.textContent).toContain('Téléphone');
      expect(numberInput().placeholder).toBe('Numéro');
      expect(comp.countryLabel).toBe('Country code');
      expect(comp.searchPlaceholder).toBe('Search country or code');
      expect(comp.noCountryFound).toBe('No country found');
      comp.onBlur();
      expect(comp.errorMessage).toBe('Téléphone requis');
      type('123');
      expect(comp.errorMessage).toBe('Numéro invalide');
    });

    it('uses the optional app texts when provided', () => {
      create([{
        provide: HQ_PHONE_NUMBER_INPUT_TEXTS,
        useValue: {
          label: () => 'L', placeholder: () => 'P', required: () => 'R', invalid: () => 'I',
          countryLabel: () => 'Indicatif', searchPlaceholder: () => 'Chercher', noCountryFound: () => 'Aucun pays',
        } satisfies HqPhoneNumberInputTexts,
      }]);
      expect([comp.countryLabel, comp.searchPlaceholder, comp.noCountryFound]).toEqual(['Indicatif', 'Chercher', 'Aucun pays']);
    });

    it('limits the country list and caches it until the allowed list changes', () => {
      create([], { allowedCountries: ['us', 'in'] });
      const first = comp.countries;
      expect(first.map((c) => c.iso)).toEqual(['IN', 'US']);
      expect(comp.countries).toBe(first);
      fixture.componentRef.setInput('allowedCountries', ['US']);
      expect(comp.countries.map((c) => c.iso)).toEqual(['US']);
      // India is selected but no longer listed: its code is shown as the name
      expect(comp.selectedCountryName).toBe('IN');
    });
  });

  it('can filter before the country list was ever built', () => {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
    const fresh = TestBed.createComponent(HqPhoneNumberInput).componentInstance;
    fresh.query = 'India';
    expect(fresh.filteredCountries.map((c) => c.iso)).toContain('IN');
  });

  describe('disabled', () => {
    it('disables both inputs and closes an open list', () => {
      create();
      comp.toggleList(trigger());
      expect(comp.listOpen).toBe(true);
      comp.setDisabledState(true);
      refresh();
      expect(comp.listOpen).toBe(false);
      expect(trigger().disabled).toBe(true);
      expect(numberInput().disabled).toBe(true);
      expect(q('.phone-number-input')?.classList).toContain('is-disabled');
      comp.toggleList(trigger());
      expect(comp.listOpen).toBe(false);
      comp.setDisabledState(false);
      expect(comp.disabled).toBe(false);
    });
  });

  describe('country list', () => {
    beforeEach(() => create());

    function open(): void {
      trigger().click();
      refresh();
    }

    it('opens under the field on the selected country and closes again on a second click', () => {
      open();
      expect(comp.listOpen).toBe(true);
      expect(trigger().getAttribute('aria-expanded')).toBe('true');
      expect(comp.panelWidth).toBe(320);
      expect(comp.filteredCountries[comp.activeIndex].iso).toBe('IN');
      expect(panel()).not.toBeNull();
      expect(panel()!.querySelector('.is-selected .hq-phone-panel__name')?.textContent).toBe('India');
      expect(panel()!.querySelector('.is-active .hq-phone-panel__name')?.textContent).toBe('India');

      const touched = vi.fn();
      comp.registerOnTouched(touched);
      open();
      expect(comp.listOpen).toBe(false);
      expect(comp.touched).toBe(true);
      expect(touched).toHaveBeenCalled();
      expect(panel()).toBeNull();
    });

    it('clears an old search when it opens again', () => {
      open();
      comp.onQuery('fra');
      comp.closeList();
      open();
      expect(comp.query).toBe('');
    });

    it('focuses the search box once the list is drawn', () => {
      vi.useFakeTimers();
      open();
      comp.onListAttached();
      vi.runAllTimers();
      expect(document.activeElement).toBe(panel()!.querySelector('.hq-phone-panel__search-input'));
    });

    it('filters by name, ISO code and calling code, and says when nothing matches', () => {
      open();
      const search = panel()!.querySelector<HTMLInputElement>('.hq-phone-panel__search-input')!;
      search.value = 'Ind';
      search.dispatchEvent(new Event('input'));
      refresh();
      const isos = comp.filteredCountries.map((c) => c.iso);
      expect(isos).toContain('IN');
      expect(isos).toContain('ID');
      expect(comp.activeIndex).toBe(0);
      expect(comp.filteredCountries).toBe(comp.filteredCountries);

      comp.onQuery('+44');
      expect(comp.filteredCountries.map((c) => c.iso)).toContain('GB');
      comp.onQuery('fr');
      expect(comp.filteredCountries.map((c) => c.iso)).toContain('FR');

      comp.onQuery('zzzz');
      refresh();
      expect(comp.filteredCountries).toEqual([]);
      expect(panel()!.querySelector('.hq-phone-panel__empty')?.textContent).toBe('No country found');
    });

    it('chooses a country on click, emits the number in it and closes', () => {
      type('2079460958');
      const changes: string[] = [];
      comp.registerOnChange((v) => changes.push(v));
      open();
      comp.onQuery('United Kingdom');
      refresh();
      const option = panel()!.querySelector<HTMLElement>('.hq-phone-panel__option')!;
      option.dispatchEvent(new Event('mouseenter'));
      option.click();
      refresh();
      expect(comp.selectedCountry).toBe('GB');
      expect(comp.listOpen).toBe(false);
      expect(changes).toEqual(['+442079460958']);
      expect(document.activeElement).toBe(trigger());
    });

    it('moves through the list with the keyboard, staying inside its ends', () => {
      open();
      const last = comp.filteredCountries.length - 1;
      const steps: [string, number][] = [
        ['Home', 0], ['ArrowUp', 0], ['ArrowDown', 1], ['PageDown', 9], ['PageUp', 1], ['PageUp', 0],
        ['End', last], ['ArrowDown', last], ['PageDown', last],
      ];
      for (const [k, expected] of steps) {
        const event = key(k);
        comp.onListKey(event);
        expect(comp.activeIndex, k).toBe(expected);
        expect(event.defaultPrevented).toBe(true);
      }
    });

    it('ignores other keys', () => {
      open();
      const before = comp.activeIndex;
      const event = key('a');
      comp.onListKey(event);
      expect(comp.activeIndex).toBe(before);
      expect(event.defaultPrevented).toBe(false);
    });

    it('chooses the active country on Enter', () => {
      open();
      comp.onQuery('United Kingdom');
      expect(comp.filteredCountries.map((c) => c.iso)).toEqual(['GB']);
      const enter = key('Enter');
      comp.onListKey(enter);
      expect(enter.defaultPrevented).toBe(true);
      expect(comp.selectedCountry).toBe('GB');
      expect(comp.listOpen).toBe(false);
    });

    it('does nothing on Enter when no country matches', () => {
      open();
      comp.onQuery('zzzz');
      comp.onListKey(key('Enter'));
      expect(comp.selectedCountry).toBe('IN');
      expect(comp.listOpen).toBe(true);
    });

    it('closes on Escape (back to the trigger) and on Tab', () => {
      open();
      const esc = key('Escape');
      panel()!.dispatchEvent(esc);
      refresh();
      expect(esc.defaultPrevented).toBe(true);
      expect(comp.listOpen).toBe(false);
      expect(document.activeElement).toBe(trigger());

      open();
      const tab = key('Tab');
      comp.onListKey(tab);
      expect(comp.listOpen).toBe(false);
      expect(tab.defaultPrevented).toBe(false);
    });

    it('opens from the trigger with the arrow keys only', () => {
      const other = key('Enter');
      trigger().dispatchEvent(other);
      expect(comp.listOpen).toBe(false);

      const down = key('ArrowDown');
      trigger().dispatchEvent(down);
      expect(down.defaultPrevented).toBe(true);
      expect(comp.listOpen).toBe(true);

      const up = key('ArrowUp');
      comp.onTriggerKey(up, trigger());
      expect(up.defaultPrevented).toBe(false);
      expect(comp.listOpen).toBe(true);

      comp.closeList();
      comp.onTriggerKey(key('ArrowUp'), trigger());
      expect(comp.listOpen).toBe(true);
    });

    it('closes on a click outside the list', () => {
      open();
      document.body.click();
      refresh();
      expect(comp.listOpen).toBe(false);
    });
  });

  describe('inside a reactive form', () => {
    let host: ComponentFixture<FormHost>;

    beforeEach(() => {
      TestBed.configureTestingModule({ imports: [FormHost] });
      host = TestBed.createComponent(FormHost);
      host.detectChanges();
    });

    const inner = () => host.debugElement.query(By.directive(HqPhoneNumberInput));
    const hostRefresh = () => {
      inner().injector.get(ChangeDetectorRef).markForCheck();
      host.debugElement.injector.get(ChangeDetectorRef).markForCheck();
      host.detectChanges();
    };

    it('writes the control value into the field and reports typed numbers back in E.164', () => {
      host.componentInstance.ctrl.setValue('9876543210');
      hostRefresh();
      const input = (inner().nativeElement as HTMLElement).querySelector<HTMLInputElement>('.phone-number-input__control')!;
      expect(input.value).toBe('9876543210');

      input.value = '+44 20 7946 0958';
      input.dispatchEvent(new Event('input'));
      expect(host.componentInstance.ctrl.value).toBe('+442079460958');
      expect(host.componentInstance.ctrl.valid).toBe(true);

      input.value = '123';
      input.dispatchEvent(new Event('input'));
      expect(host.componentInstance.ctrl.errors).toEqual({ pattern: true });
    });

    it('makes the control required when isRequired is set', () => {
      host.componentInstance.required = true;
      hostRefresh();
      host.componentInstance.ctrl.updateValueAndValidity();
      expect(host.componentInstance.ctrl.errors).toEqual({ required: true });
    });

    it('follows the control being disabled', () => {
      host.componentInstance.ctrl.disable();
      hostRefresh();
      expect((inner().componentInstance as HqPhoneNumberInput).disabled).toBe(true);
    });
  });
});
