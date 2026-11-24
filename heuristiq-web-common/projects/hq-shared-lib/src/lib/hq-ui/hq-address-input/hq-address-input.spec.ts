import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqAddressInput } from './hq-address-input';
import { HQ_ADDRESS_DEFAULT_COUNTRY, HQ_INDIAN_PINCODE_PATTERN, hqEmptyAddress } from './hq-address-input.constants';
import { HQ_ADDRESS_INPUT_DEFAULT_TEXTS, HQ_ADDRESS_INPUT_TEXTS, HqAddressInputTexts } from './hq-address-input.texts';
import { HqAddress } from './hq-address.model';

const FULL: HqAddress = { lines: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', country: 'India', pincode: '560001', landmark: 'Near park' };

@Component({
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-address-input [formControl]="control" [isRequired]="required" [label]="label" [showLandmark]="showLandmark" [variant]="variant"></hq-address-input>`,
})
class FormHost {
  control = new FormControl<HqAddress | null>(null);
  required = false;
  label = '';
  showLandmark = true;
  variant: 'default' | 'compact' = 'default';
}

@Component({
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-address-input [value]="value" [disabled]="disabled" country="Bharat"></hq-address-input>`,
})
class InputHost {
  value: HqAddress | null = FULL;
  disabled = false;
}

function configure(texts?: HqAddressInputTexts): void {
  TestBed.configureTestingModule({
    imports: [CommonModule, ReactiveFormsModule, FormHost, InputHost],
    providers: texts ? [{ provide: HQ_ADDRESS_INPUT_TEXTS, useValue: texts }] : [],
  });
}

/** Zoneless: plain field changes are not notified, so mark the host dirty before checking. */
function refresh(fixture: ComponentFixture<unknown>): void {
  fixture.componentRef.changeDetectorRef.markForCheck();
  fixture.detectChanges();
}

function render<H>(type: new () => H): { fixture: ComponentFixture<H>; host: H; el: HTMLElement } {
  const fixture = TestBed.createComponent(type);
  refresh(fixture);
  return { fixture, host: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

/** Inputs in template order (landmark shown): lines, landmark, city, state, pincode, country. */
function inputs(el: HTMLElement): HTMLInputElement[] {
  return Array.from(el.querySelectorAll('input'));
}

function type(fixture: ComponentFixture<unknown>, input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
  refresh(fixture);
}

function blur(fixture: ComponentFixture<unknown>, input: HTMLInputElement): void {
  input.dispatchEvent(new Event('blur'));
  refresh(fixture);
}

function errors(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.address-input__error')).map((e) => e.textContent?.trim() ?? '');
}

describe('hqEmptyAddress / constants', () => {
  it('builds an empty Indian address each time', () => {
    const a = hqEmptyAddress();
    expect(a).toEqual({ lines: '', city: '', state: '', country: HQ_ADDRESS_DEFAULT_COUNTRY, pincode: '', landmark: '' });
    expect(hqEmptyAddress()).not.toBe(a);
  });

  it('accepts only six-digit pincodes without a leading zero', () => {
    expect(HQ_INDIAN_PINCODE_PATTERN.test('560001')).toBe(true);
    expect(HQ_INDIAN_PINCODE_PATTERN.test('060001')).toBe(false);
    expect(HQ_INDIAN_PINCODE_PATTERN.test('56000')).toBe(false);
  });
});

describe('HqAddressInput', () => {
  describe('with default texts in a reactive form', () => {
    beforeEach(() => configure());

    it('renders default labels, placeholders, the country and the landmark field', () => {
      const { el } = render(FormHost);
      expect(el.querySelector('.address-input__label')?.textContent?.trim()).toBe('Address');
      expect(el.querySelector('.address-input__required')).toBeNull();
      const labels = Array.from(el.querySelectorAll('.address-input__field-label')).map((l) => l.textContent?.trim());
      expect(labels).toEqual(['Address line', 'Landmark (optional)', 'City', 'State', 'Pincode', 'Country']);
      const placeholders = inputs(el).map((i) => i.placeholder);
      expect(placeholders.slice(0, 5)).toEqual(['House / street / area', 'Nearby landmark', 'City', 'State', '6-digit pincode']);
      expect(inputs(el)[5].value).toBe('India');
      expect(inputs(el)[5].disabled).toBe(true);
    });

    it('writes the form value into the inputs, filling missing fields with defaults', async () => {
      const { el, host, fixture } = render(FormHost);
      host.control.setValue({ lines: 'L1', city: 'C', state: 'S', pincode: '500001' } as HqAddress);
      refresh(fixture);
      await fixture.whenStable();
      refresh(fixture);
      expect(inputs(el).map((i) => i.value)).toEqual(['L1', '', 'C', 'S', '500001', 'India']);
      host.control.setValue(null);
      refresh(fixture);
      expect(inputs(el).map((i) => i.value).slice(0, 5)).toEqual(['', '', '', '', '']);
    });

    it('propagates typed values to the form control and strips non-digits from the pincode', () => {
      const { el, host, fixture } = render(FormHost);
      const [lines, landmark, city, state, pincode] = inputs(el);
      type(fixture, lines, '1 Main St');
      type(fixture, landmark, 'Temple');
      type(fixture, city, 'Chennai');
      type(fixture, state, 'TN');
      type(fixture, pincode, '60a0-01 23');
      expect(host.control.value).toEqual({
        lines: '1 Main St', landmark: 'Temple', city: 'Chennai', state: 'TN', pincode: '600012', country: 'India',
      });
      expect(host.control.valid).toBe(true);
    });

    it('registers itself as the value accessor and the validator of its control', () => {
      const { fixture } = render(FormHost);
      const child = fixture.debugElement.children[0];
      const component = child.componentInstance as HqAddressInput;
      expect(child.injector.get(NG_VALUE_ACCESSOR)).toContain(component);
      expect(child.injector.get(NG_VALIDATORS)).toContain(component);
    });

    it('marks the control touched on blur', () => {
      const { el, host, fixture } = render(FormHost);
      expect(host.control.touched).toBe(false);
      blur(fixture, inputs(el)[0]);
      expect(host.control.touched).toBe(true);
    });

    it('when required: shows *, reports required errors after blur and clears them once filled', () => {
      const { el, host, fixture } = render(FormHost);
      host.required = true;
      refresh(fixture);
      host.control.updateValueAndValidity();
      expect(el.querySelector('.address-input__required')?.textContent).toBe('*');
      expect(host.control.errors).toEqual({ linesRequired: true, cityRequired: true, stateRequired: true, pincodeRequired: true });
      expect(errors(el)).toEqual([]);

      blur(fixture, inputs(el)[0]);
      expect(errors(el)).toEqual(['Address line is required.', 'City is required.', 'State is required.', 'Pincode is required.']);

      const [lines, , city, state, pincode] = inputs(el);
      type(fixture, lines, 'x');
      type(fixture, city, 'y');
      type(fixture, state, '   ');
      type(fixture, pincode, '123');
      expect(errors(el)).toEqual(['State is required.', 'Enter a valid 6-digit pincode.']);
      expect(host.control.errors).toEqual({ stateRequired: true, pincodePattern: true });

      type(fixture, state, 'z');
      type(fixture, pincode, '123456');
      expect(errors(el)).toEqual([]);
      expect(host.control.valid).toBe(true);
    });

    it('validates the pincode pattern even when not required', () => {
      const { el, host, fixture } = render(FormHost);
      type(fixture, inputs(el)[4], '012345');
      expect(host.control.errors).toEqual({ pincodePattern: true });
      blur(fixture, inputs(el)[4]);
      expect(errors(el)).toEqual(['Enter a valid 6-digit pincode.']);
    });

    it('disables every editable input when the form control is disabled', () => {
      const { el, host, fixture } = render(FormHost);
      host.control.disable();
      refresh(fixture);
      expect(inputs(el).every((i) => i.disabled)).toBe(true);
      expect(el.querySelector('.address-input')?.classList.contains('is-disabled')).toBe(true);
      host.control.enable();
      refresh(fixture);
      expect(inputs(el)[0].disabled).toBe(false);
    });

    it('hides the landmark field, applies the compact variant and uses the caller label', () => {
      const { el, host, fixture } = render(FormHost);
      host.showLandmark = false;
      host.variant = 'compact';
      host.label = 'Home address';
      refresh(fixture);
      expect(inputs(el).length).toBe(5);
      expect(el.querySelector('.address-input')?.classList.contains('is-compact')).toBe(true);
      expect(el.querySelector('.address-input__label')?.textContent?.trim()).toBe('Home address');
    });
  });

  describe('as a plain [value] input', () => {
    beforeEach(() => configure());

    it('shows the bound value and country, merges null with an empty address, and respects [disabled]', () => {
      const { el, host, fixture } = render(InputHost);
      expect(inputs(el).map((i) => i.value)).toEqual(['12 MG Road', 'Near park', 'Bengaluru', 'Karnataka', '560001', 'Bharat']);
      host.value = null;
      host.disabled = true;
      refresh(fixture);
      expect(inputs(el).map((i) => i.value).slice(0, 5)).toEqual(['', '', '', '', '']);
      expect(inputs(el)[0].disabled).toBe(true);
    });

    it('updates its own value on typing without a registered form (no-op callbacks)', () => {
      const { el, fixture } = render(InputHost);
      const component = fixture.debugElement.children[0].componentInstance as HqAddressInput;
      type(fixture, inputs(el)[2], 'Mysuru');
      blur(fixture, inputs(el)[2]);
      expect(component.value.city).toBe('Mysuru');
      expect(component.touched).toBe(true);
    });
  });

  describe('component API', () => {
    beforeEach(() => configure());

    function create(): HqAddressInput {
      return TestBed.runInInjectionContext(() => new HqAddressInput());
    }

    it('hasError is false until touched, then reflects validation', () => {
      const c = create();
      c.isRequired = true;
      expect(c.hasError).toBe(false);
      c.onBlur();
      expect(c.hasError).toBe(true);
      c.writeValue(FULL);
      expect(c.hasError).toBe(false);
    });

    it('fieldError is empty before touch, when valid, and for fields without an error', () => {
      const c = create();
      c.isRequired = true;
      expect(c.fieldError('lines')).toBe('');
      c.onBlur();
      c.writeValue({ ...FULL, city: '' });
      expect(c.fieldError('city')).toBe('City is required.');
      expect(c.fieldError('lines')).toBe('');
      expect(c.fieldError('pincode')).toBe('');
      c.writeValue(FULL);
      expect(c.fieldError('city')).toBe('');
    });

    it('validate treats missing (undefined) fields as empty when required', () => {
      const c = create();
      c.isRequired = true;
      c.value = { pincode: '560001' } as HqAddress;
      expect(c.validate()).toEqual({ linesRequired: true, cityRequired: true, stateRequired: true });
      expect(c.value.country).toBe('India');
      (c as unknown as { _value: Partial<HqAddress> })._value = {};
      expect(c.validate()).toEqual({ linesRequired: true, cityRequired: true, stateRequired: true, pincodeRequired: true });
    });

    it('setDisabledState toggles disabled', () => {
      const c = create();
      c.setDisabledState(true);
      expect(c.disabled).toBe(true);
      c.setDisabledState(false);
      expect(c.disabled).toBe(false);
    });

    it('calls the registered change and touched callbacks', () => {
      const c = create();
      const onChange = vi.fn();
      const onTouched = vi.fn();
      c.registerOnChange(onChange);
      c.registerOnTouched(onTouched);
      c.updateField('pincode', '5600019999');
      expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ pincode: '560001' }));
      c.onBlur();
      expect(onTouched).toHaveBeenCalledTimes(1);
    });
  });

  describe('with HQ_ADDRESS_INPUT_TEXTS', () => {
    const texts: HqAddressInputTexts = {
      ...HQ_ADDRESS_INPUT_DEFAULT_TEXTS,
      label: () => 'Pata',
      linesLabel: () => 'Line FR',
      linesRequired: () => 'Line needed',
      cityRequired: () => 'City needed',
      stateRequired: () => 'State needed',
      pincodeRequired: () => 'Pin needed',
      pincodeInvalid: () => 'Pin bad',
    };
    beforeEach(() => configure(texts));

    it('uses the provided labels and messages', () => {
      const { el, host, fixture } = render(FormHost);
      host.required = true;
      refresh(fixture);
      expect(el.querySelector('.address-input__label')?.textContent?.trim()).toBe('Pata *');
      expect(el.querySelector('.address-input__field-label')?.textContent?.trim()).toBe('Line FR');
      blur(fixture, inputs(el)[0]);
      expect(errors(el)).toEqual(['Line needed', 'City needed', 'State needed', 'Pin needed']);
      type(fixture, inputs(el)[4], '99');
      expect(errors(el)).toContain('Pin bad');
    });
  });
});
