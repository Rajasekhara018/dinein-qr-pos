import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HQ_PHONE_DEFAULT_COUNTRY } from '../hq-phone-number-input/hq-phone-number-input.constants';
import { HqPhoneNumberInput } from '../hq-phone-number-input/hq-phone-number-input';
import { HqOnboardingForm } from './hq-onboarding-form';
import { HqOnboardingAnswers, HqOnboardingField, HqOnboardingStep } from './hq-onboarding-form.model';
import { HQ_ONBOARDING_FORM_DEFAULT_TEXTS, HQ_ONBOARDING_FORM_TEXTS, HqOnboardingFormTexts } from './hq-onboarding-form.texts';

const STEPS: HqOnboardingStep[] = [
  {
    key: 'about',
    title: 'About you',
    fields: [
      { key: 'name', label: 'Name', type: 'TEXT', required: true, minLength: 2, maxLength: 5 },
      { key: 'email', label: 'Email', type: 'EMAIL', required: true },
      { key: 'age', label: 'Age', type: 'NUMBER', minValue: 18, maxValue: 60 },
      { key: 'dob', label: 'Birth date', type: 'DATE' },
      { key: 'phone', label: 'Mobile', type: 'PHONE', required: true },
      { key: 'licence', label: 'Licence', type: 'DOCUMENT', documentCategory: 'DL' },
    ],
  },
  {
    key: 'more',
    title: '',
    fields: [
      { key: 'city', label: 'City', type: 'SELECT', options: ['PUN', 'BLR'] },
      { key: 'code', label: 'Code', type: 'text', pattern: '^[A-Z]{3}$', patternErrorMessage: 'Three capitals' },
      { key: 'ref', label: 'Ref', type: 'PATTERN', pattern: '^R\\d+$' },
      { key: 'agree', label: 'I agree', type: 'CHECKBOX', required: true },
      { key: 'news', label: 'News', type: 'checkbox' },
      { key: 'fee', label: 'Fee', type: 'CURRENCY' },
    ],
  },
];

/** `agree` is a required checkbox: unticked it is still sent (as false); only optional ones are left out. */
const UNTICKED = { agree: false } as const;

describe('HqOnboardingForm', () => {
  let fixture: ComponentFixture<HqOnboardingForm>;
  let comp: HqOnboardingForm;
  let el: HTMLElement;
  let answers: HqOnboardingAnswers[];
  let validity: boolean[];

  function create(inputs: Record<string, unknown> = { steps: STEPS }, providers: unknown[] = []): void {
    TestBed.configureTestingModule({ imports: [HqSharedLibModule], providers: providers as never[] });
    fixture = TestBed.createComponent(HqOnboardingForm);
    comp = fixture.componentInstance;
    el = fixture.nativeElement;
    answers = [];
    validity = [];
    comp.answersChange.subscribe((a) => answers.push(a));
    comp.validityChange.subscribe((v) => validity.push(v));
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  }

  const flush = () => Promise.resolve();
  const q = <E extends HTMLElement = HTMLElement>(sel: string) => el.querySelector<E>(sel);
  const control = (k: string) => q<HTMLInputElement & HTMLSelectElement>(`#${comp.uid}-${k}`)!;
  const field = (k: string): HqOnboardingField => STEPS.flatMap((s) => s.fields).find((f) => f.key === k)!;
  const setText = (k: string, value: string) => {
    const input = control(k);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  };
  const errorUnder = (k: string) => control(k).closest('.hq-onb__field')?.querySelector('.hq-onb__error')?.textContent ?? null;

  describe('drawing the schema', () => {
    beforeEach(() => create());

    it('draws one control per field except documents, each of the right kind', () => {
      expect(Object.keys(comp.form.controls)).toEqual(['name', 'email', 'age', 'dob', 'phone', 'city', 'code', 'ref', 'agree', 'news', 'fee']);
      expect(control('name').type).toBe('text');
      expect(control('email').type).toBe('email');
      expect(control('age').type).toBe('number');
      expect(control('fee').type).toBe('number');
      expect(control('dob').type).toBe('date');
      expect(control('city').tagName).toBe('SELECT');
      expect(control('agree').type).toBe('checkbox');
      expect(control('ref').type).toBe('text');
      expect(q(`#${comp.uid}-licence`)).toBeNull();
      expect(fixture.debugElement.queryAll(By.directive(HqPhoneNumberInput)).length).toBe(1);
    });

    it('shows the step titles that exist, labels and required stars', () => {
      expect(Array.from(el.querySelectorAll('.hq-onb__step-title')).map((h) => h.textContent)).toEqual(['About you']);
      const nameLabel = el.querySelector(`label[for="${comp.uid}-name"]`)!;
      expect(nameLabel.textContent).toContain('Name');
      expect(nameLabel.querySelector('.hq-onb__required')).not.toBeNull();
      expect(el.querySelector(`label[for="${comp.uid}-dob"] .hq-onb__required`)).toBeNull();
      expect(el.querySelector(`label[for="${comp.uid}-agree"]`)?.textContent).toContain('I agree*');
      expect(el.querySelector(`label[for="${comp.uid}-news"] .hq-onb__required`)).toBeNull();
    });

    it('puts the schema limits on the inputs', () => {
      expect(control('age').getAttribute('min')).toBe('18');
      expect(control('age').getAttribute('max')).toBe('60');
      expect(control('fee').getAttribute('min')).toBeNull();
      expect(control('name').getAttribute('maxlength')).toBe('5');
      expect(control('email').getAttribute('maxlength')).toBeNull();
      expect(control('dob').getAttribute('min')).toBeNull();
    });

    it('draws a select with the placeholder and the schema options', () => {
      expect(Array.from(control('city').options).map((o) => o.textContent)).toEqual(['Select…', 'PUN', 'BLR']);
      expect(comp.selectPlaceholder).toBe('Select…');
    });

    it('starts checkboxes unticked and the rest empty', () => {
      expect(comp.form.getRawValue()).toEqual({
        name: null, email: null, age: null, dob: null, phone: null, city: null, code: null, ref: null,
        agree: false, news: false, fee: null,
      });
    });

    it('passes the label, required flag and app default country to the phone field', () => {
      const phone = fixture.debugElement.query(By.directive(HqPhoneNumberInput)).componentInstance as HqPhoneNumberInput;
      expect(phone.label).toBe('Mobile');
      expect(phone.isRequired).toBe(true);
      expect(phone.defaultCountry).toBe('IN');
      expect(comp.phoneCountry).toBe('IN');
    });

    it('gives each instance its own id prefix', () => {
      const other = TestBed.createComponent(HqOnboardingForm).componentInstance;
      expect(other.uid).not.toBe(comp.uid);
      expect(comp.id(field('name'))).toBe(`${comp.uid}-name`);
      expect(comp.trackStep(0, STEPS[0])).toBe('about');
      expect(comp.trackField(0, field('age'))).toBe('age');
    });
  });

  it('hides step titles on request', () => {
    create({ steps: STEPS, hideStepTitles: true });
    expect(el.querySelector('.hq-onb__step-title')).toBeNull();
  });

  it('draws nothing for no schema', () => {
    create({ steps: null });
    expect(comp.steps).toEqual([]);
    expect(el.querySelector('.hq-onb__step')).toBeNull();
    expect(comp.answers).toEqual({});
  });

  describe('answers and validity', () => {
    beforeEach(() => create());

    it('emits the answers and validity after building, deferred so a parent is not changed mid-check', async () => {
      await flush();
      answers.length = 0;
      validity.length = 0;
      comp.steps = STEPS;
      expect(answers).toEqual([]);
      expect(validity).toEqual([]);
      await flush();
      expect(answers).toEqual([{ ...UNTICKED }]);
      expect(validity).toEqual([false]);
      expect(comp.valid).toBe(false);
    });

    it('emits cleaned answers as the person types: trimmed text, numbers, E.164 phone', async () => {
      await flush();
      setText('name', '  Ana ');
      setText('age', '30');
      const phone = el.querySelector<HTMLInputElement>('.phone-number-input__control')!;
      phone.value = '98765 43210';
      phone.dispatchEvent(new Event('input'));
      control('city').value = control('city').options[2].value;
      control('city').dispatchEvent(new Event('change'));
      expect(answers.at(-1)).toEqual({ name: 'Ana', age: 30, phone: '+919876543210', city: 'BLR', ...UNTICKED });
    });

    it('leaves out an untouched optional checkbox but sends a ticked one', async () => {
      await flush();
      control('news').click();
      expect(answers.at(-1)).toEqual({ news: true, ...UNTICKED });
      control('news').click();
      expect(answers.at(-1)).toEqual({ ...UNTICKED });
    });

    it('reports valid only when every required and rule-checked field passes, emitting each change once', async () => {
      await flush();
      setText('name', 'Ana');
      setText('email', 'ana@x.io');
      comp.form.controls['phone'].setValue('+919876543210');
      expect(validity).toEqual([false]);
      control('agree').click();
      expect(comp.valid).toBe(true);
      expect(validity).toEqual([false, true]);
      setText('age', '30');
      expect(validity).toEqual([false, true]);
      control('agree').click();
      expect(validity).toEqual([false, true, false]);
      expect(comp.answers['agree']).toBe(false);
    });

    it('stops emitting once destroyed', async () => {
      await flush();
      const before = answers.length;
      fixture.destroy();
      comp.form.controls['name'].setValue('Bob');
      expect(answers.length).toBe(before);
    });
  });

  describe('validation messages', () => {
    beforeEach(() => create());

    it('shows nothing until a field is touched', () => {
      comp.form.controls['name'].setValue('');
      expect(comp.errorOf(field('name'))).toBe('');
      expect(comp.errorOf({ key: 'nope', label: 'X', type: 'TEXT' })).toBe('');
    });

    it('explains required, length, email, number range and pattern errors', () => {
      setText('name', '');
      expect(errorUnder('name')).toBe('Name is required.');
      expect(control('name').classList).toContain('has-error');
      setText('name', 'A');
      expect(errorUnder('name')).toBe('Name must be at least 2 characters.');
      setText('name', 'Abcdefg');
      expect(errorUnder('name')).toBe('Name must be at most 5 characters.');
      setText('name', 'Abc');
      expect(errorUnder('name')).toBeNull();

      setText('email', 'not-an-email');
      expect(errorUnder('email')).toBe('Enter a valid email address.');
      setText('email', ' ana@x.io ');
      expect(errorUnder('email')).toBeNull();

      setText('age', '10');
      expect(errorUnder('age')).toBe('Age must be at least 18.');
      setText('age', '70');
      expect(errorUnder('age')).toBe('Age must be at most 60.');

      setText('code', 'abc');
      expect(errorUnder('code')).toBe('Three capitals');
      setText('ref', 'X1');
      expect(errorUnder('ref')).toBe('Ref is not in the expected format.');
    });

    it('requires a required checkbox to be ticked', () => {
      comp.markAllTouched();
      fixture.detectChanges();
      expect(control('agree').closest('.hq-onb__field')?.querySelector('.hq-onb__error')?.textContent).toBe('I agree is required.');
    });

    it('falls back to the generic format message for an unknown error', () => {
      const c = comp.form.controls['name'];
      c.markAsTouched();
      c.setErrors({ custom: true });
      expect(comp.errorOf(field('name'))).toBe('Name is not in the expected format.');
    });

    it('shows every error and touches the phone field on markAllTouched', () => {
      comp.markAllTouched();
      fixture.detectChanges();
      expect(errorUnder('name')).toBe('Name is required.');
      expect(errorUnder('email')).toBe('Email is required.');
      expect(el.querySelector('.phone-number-input__error')?.textContent).toBe('Mobile is required.');
    });
  });

  describe('date limits', () => {
    it('enforces the range as a validator and on the picker', () => {
      create({ steps: STEPS, dateLimits: { dob: { min: '2000-01-01', max: '2005-12-31' } } });
      expect(control('dob').getAttribute('min')).toBe('2000-01-01');
      expect(control('dob').getAttribute('max')).toBe('2005-12-31');
      setText('dob', '1999-12-31');
      expect(errorUnder('dob')).toBe('Birth date must be on or after 2000-01-01.');
      setText('dob', '2006-01-01');
      expect(errorUnder('dob')).toBe('Birth date must be on or before 2005-12-31.');
      setText('dob', '2003-06-15');
      expect(errorUnder('dob')).toBeNull();
    });

    it('re-checks the current value when the limits change, and accepts any date without limits', async () => {
      create();
      setText('dob', '1990-01-01');
      expect(comp.form.controls['dob'].valid).toBe(true);
      fixture.componentRef.setInput('dateLimits', { dob: { max: '1980-01-01' } });
      expect(comp.form.controls['dob'].errors).toEqual({ dateMax: { limit: '1980-01-01' } });
      fixture.componentRef.setInput('dateLimits', { dob: { min: '1980-01-01' } });
      expect(comp.form.controls['dob'].valid).toBe(true);
      fixture.componentRef.setInput('dateLimits', null);
      expect(comp.dateLimits).toEqual({});
      expect(comp.form.controls['dob'].valid).toBe(true);
      await flush();
      expect(answers.at(-1)).toEqual({ dob: '1990-01-01', ...UNTICKED });
    });
  });

  describe('option overrides', () => {
    it('draws any field as a select of the given choices, with translated labels', () => {
      create({
        steps: STEPS,
        optionOverrides: { name: ['A', 'B'], city: [] },
        optionLabel: (key: string, value: string) => (value === 'A' ? `${key}:${value}` : ''),
      });
      expect(control('name').tagName).toBe('SELECT');
      expect(Array.from(control('name').options).map((o) => o.textContent)).toEqual(['Select…', 'name:A', 'B']);
      expect(comp.kind(field('city'))).toBe('select');
      expect(comp.choices(field('city'))).toEqual(['PUN', 'BLR']);
      expect(comp.choices(field('dob'))).toEqual([]);
    });

    it('shows the code itself when there is no label function', () => {
      create();
      expect(comp.choiceText(field('city'), 'PUN')).toBe('PUN');
    });
  });

  describe('hidden and disabled fields', () => {
    it('does not draw a hidden field, ignores it for validity and leaves it out of the answers', async () => {
      create({ steps: STEPS, hiddenFields: ['email'] });
      expect(q(`#${comp.uid}-email`)).toBeNull();
      expect(comp.form.controls['email'].disabled).toBe(true);
      comp.form.controls['email'].setValue('x@y.z');
      expect(comp.answers['email']).toBeUndefined();

      fixture.componentRef.setInput('hiddenFields', null);
      fixture.detectChanges();
      expect(comp.hiddenFields).toEqual([]);
      expect(comp.form.controls['email'].enabled).toBe(true);
      expect(control('email')).not.toBeNull();
      await flush();
      expect(answers.at(-1)).toEqual({ email: 'x@y.z', ...UNTICKED });
    });

    it('disables every field, also when the schema arrives later, and enables them again', () => {
      create({ disabled: true });
      expect(comp.disabled).toBe(true);
      fixture.componentRef.setInput('steps', STEPS);
      fixture.detectChanges();
      expect(comp.form.disabled).toBe(true);
      expect(control('name').disabled).toBe(true);
      fixture.componentRef.setInput('disabled', false);
      fixture.detectChanges();
      expect(comp.form.enabled).toBe(true);
    });

    it('keeps hidden fields disabled when the form is enabled', () => {
      create({ steps: STEPS, hiddenFields: ['age'], disabled: true });
      fixture.componentRef.setInput('disabled', false);
      expect(comp.form.controls['age'].disabled).toBe(true);
      expect(comp.form.controls['name'].enabled).toBe(true);
    });
  });

  describe('reset', () => {
    it('clears every answer and error, or sets the given values', () => {
      create();
      setText('name', 'X');
      comp.markAllTouched();
      fixture.detectChanges();
      comp.reset();
      fixture.detectChanges();
      expect(comp.form.getRawValue()['name']).toBeNull();
      expect(comp.form.getRawValue()['agree']).toBe(false);
      expect(errorUnder('name')).toBeNull();
      const phone = fixture.debugElement.query(By.directive(HqPhoneNumberInput)).componentInstance as HqPhoneNumberInput;
      expect(phone.touched).toBe(false);
      expect(answers.at(-1)).toEqual({ ...UNTICKED });

      comp.reset({ name: 'Ana', news: true });
      expect(answers.at(-1)).toEqual({ name: 'Ana', news: true, ...UNTICKED });
    });

    it('works before any view is drawn', () => {
      TestBed.configureTestingModule({ imports: [HqSharedLibModule] });
      const bare = TestBed.createComponent(HqOnboardingForm).componentInstance;
      bare.steps = STEPS;
      expect(() => bare.reset({ name: 'A' })).not.toThrow();
      expect(() => bare.markAllTouched()).not.toThrow();
      expect(bare.answers).toEqual({ name: 'A', ...UNTICKED });
    });
  });

  describe('phone country and texts', () => {
    it('takes the phone country from the input, else HQ_PHONE_DEFAULT_COUNTRY', () => {
      create({ steps: STEPS }, [{ provide: HQ_PHONE_DEFAULT_COUNTRY, useValue: 'AE' }]);
      expect(comp.phoneCountry).toBe('AE');
      fixture.componentRef.setInput('defaultPhoneCountry', 'GB');
      fixture.detectChanges();
      expect(comp.phoneCountry).toBe('GB');
      const phone = fixture.debugElement.query(By.directive(HqPhoneNumberInput)).componentInstance as HqPhoneNumberInput;
      expect(phone.defaultCountry).toBe('GB');
    });

    it('uses the app texts from HQ_ONBOARDING_FORM_TEXTS', () => {
      const texts: HqOnboardingFormTexts = {
        ...HQ_ONBOARDING_FORM_DEFAULT_TEXTS,
        required: (l) => `${l} obligatoire`,
        selectPlaceholder: () => 'Choisir',
      };
      create({ steps: STEPS }, [{ provide: HQ_ONBOARDING_FORM_TEXTS, useValue: texts }]);
      expect(control('city').options[0].textContent).toBe('Choisir');
      setText('name', '');
      expect(errorUnder('name')).toBe('Name obligatoire');
    });
  });

  it('checks email fields against the schema pattern instead of the built-in rule when one is given', () => {
    create({
      steps: [{
        key: 's', title: 'S', fields: [
          { key: 'mail', label: 'Mail', type: 'EMAIL', pattern: '^.+@corp\\.io$', minLength: 3 },
          { key: 'pick', label: 'Pick', type: 'SELECT', options: ['ab', 'X'], pattern: '^[a-z]+$' },
        ],
      }],
    });
    comp.form.controls['mail'].setValue('ana@other.io');
    expect(comp.form.controls['mail'].errors).toEqual({ pattern: { requiredPattern: '^.+@corp\\.io$', actualValue: 'ana@other.io' } });
    comp.form.controls['mail'].setValue('ana@corp.io');
    expect(comp.form.controls['mail'].valid).toBe(true);
    comp.form.controls['pick'].setValue('X');
    expect(comp.form.controls['pick'].hasError('pattern')).toBe(true);
  });

  describe('suggestions and dependent fields', () => {
    const GEO: HqOnboardingStep[] = [{
      key: 'where', title: 'Where', fields: [
        { key: 'country', label: 'Country', type: 'TEXT' },
        { key: 'state', label: 'State', type: 'TEXT' },
        { key: 'city', label: 'City', type: 'TEXT' },
        { key: 'age', label: 'Age', type: 'NUMBER' },
      ],
    }];

    it('draws a free text input with a datalist for a field that has suggestions, and none for the others', () => {
      create({ steps: GEO, suggestions: { country: ['India', 'Nepal'], age: ['1'] } });
      const input = control('country');
      expect(input.tagName).toBe('INPUT');
      expect(input.getAttribute('list')).toBe(`${comp.uid}-country-list`);
      expect(Array.from(el.querySelectorAll(`#${comp.uid}-country-list option`)).map((o) => o.getAttribute('value'))).toEqual(['India', 'Nepal']);
      expect(control('state').getAttribute('list')).toBeNull();
      expect(control('age').getAttribute('list')).toBeNull();
      expect(el.querySelectorAll('datalist').length).toBe(1);
    });

    it('still accepts a value that is not in the suggestions', () => {
      create({ steps: GEO, suggestions: { country: ['India'] } });
      setText('country', 'Atlantis');
      expect(answers.at(-1)).toEqual(expect.objectContaining({ country: 'Atlantis' }));
    });

    it('clears a child answer when its parent is changed, but not when the form is reset with values', () => {
      create({ steps: GEO, dependsOn: { state: 'country', city: 'state' } });
      comp.form.controls['country'].setValue('India');
      comp.form.controls['state'].setValue('Karnataka');
      comp.form.controls['city'].setValue('Mysuru');

      comp.form.controls['state'].setValue('Kerala');
      expect(comp.form.controls['city'].value).toBeNull();
      expect(comp.form.controls['state'].value).toBe('Kerala');

      comp.form.controls['country'].setValue('Nepal');
      expect(comp.form.controls['state'].value).toBeNull();

      comp.reset({ country: 'India', state: 'Goa', city: 'Panaji' });
      expect(comp.form.getRawValue()).toEqual(expect.objectContaining({ country: 'India', state: 'Goa', city: 'Panaji' }));
    });

    describe('searchable drop-downs', () => {
      const select = (key: string) => el.querySelector<HTMLElement>(`hq-searchable-select #${comp.uid}-${key}`);
      const selectInput = (key: string) => select(key) as HTMLInputElement;

      it('draws a searchable drop-down for the listed fields (even with an empty list) and keeps the rest as plain text', () => {
        create({ steps: GEO, searchableFields: ['country', 'state'], suggestions: { country: ['India', 'Nepal'] } });

        expect(select('country')?.getAttribute('role')).toBe('combobox');
        expect(select('state')).toBeTruthy();
        expect(control('city').closest('hq-searchable-select')).toBeNull();
        expect(el.querySelectorAll('hq-searchable-select').length).toBe(2);
        expect(el.querySelectorAll('datalist').length).toBe(0);
      });

      it('feeds the options to the control, reads the picked value into the answers and clears the child with the parent', () => {
        create({ steps: GEO, searchableFields: ['country', 'state'], suggestions: { country: ['India', 'Nepal'], state: ['Goa'] },
          dependsOn: { state: 'country' } });

        selectInput('country').click();
        fixture.detectChanges();
        (document.querySelectorAll('.hq-ss-panel__option')[1] as HTMLElement).click();
        fixture.detectChanges();
        expect(comp.form.controls['country'].value).toBe('Nepal');
        expect(answers.at(-1)).toEqual(expect.objectContaining({ country: 'Nepal' }));

        comp.form.controls['state'].setValue('Goa');
        comp.form.controls['country'].setValue('India');
        expect(comp.form.controls['state'].value).toBeNull();
      });

      it('blocks a dependent drop-down with a hint until its parent is answered, then frees it', () => {
        create({ steps: GEO, searchableFields: ['country', 'state'], suggestions: { country: ['India'], state: ['Goa'] },
          dependsOn: { state: 'country' } });

        expect(comp.blockedHintOf(GEO[0].fields[1])).toBe('Choose Country first.');
        expect(selectInput('state').disabled).toBe(true);
        expect(el.querySelector('.hq-ss__hint')?.textContent).toContain('Choose Country first.');

        comp.form.controls['country'].setValue('India');
        fixture.detectChanges();
        expect(comp.blockedHintOf(GEO[0].fields[1])).toBeNull();
        expect(selectInput('state').disabled).toBe(false);
      });

      it('a required child stays required while it is blocked', () => {
        const required: HqOnboardingStep[] = [{ key: 'w', title: '', fields: [
          { key: 'country', label: 'Country', type: 'TEXT' },
          { key: 'state', label: 'State', type: 'TEXT', required: true },
        ] }];
        create({ steps: required, searchableFields: ['country', 'state'], dependsOn: { state: 'country' } });

        expect(comp.form.valid).toBe(false);
        expect(comp.form.controls['state'].enabled).toBe(true);
      });

      it('shows a free-text child, without a notice, when the chosen parent has no listed children', () => {
        create({ steps: GEO, searchableFields: ['country', 'state'], suggestions: { country: ['India'], state: [] },
          dependsOn: { state: 'country' } });
        comp.form.controls['country'].setValue('Atlantis');
        fixture.detectChanges();

        expect(selectInput('state').disabled).toBe(false);
        expect(el.querySelector('hq-searchable-select .hq-ss__notice')).toBeNull();
        setText('state', 'Capital');
        expect(answers.at(-1)).toEqual(expect.objectContaining({ state: 'Capital' }));
      });

      it('a top-level drop-down without options says the list could not be loaded and still takes typed text', () => {
        create({ steps: GEO, searchableFields: ['country'] });

        expect(el.querySelector('hq-searchable-select .hq-ss__notice')?.textContent).toContain('Nothing is set up in this list yet');
        setText('country', 'India');
        expect(answers.at(-1)).toEqual(expect.objectContaining({ country: 'India' }));
      });

      it('passes loading and failed lists to the drop-down and reports the retry with the field key', () => {
        create({ steps: GEO, searchableFields: ['country', 'state'], loadingFields: ['country'], failedFields: ['state'] });
        const retried: string[] = [];
        comp.retryField.subscribe((key) => retried.push(key));

        expect(selectInput('country').readOnly).toBe(true);
        expect(el.querySelector('hq-searchable-select .hq-ss__spinner')).toBeTruthy();
        el.querySelector<HTMLButtonElement>('hq-searchable-select .hq-ss__notice .hq-ss__link')!.click();
        expect(retried).toEqual(['state']);
      });

      it('falls back to the parent key as the hint label when the parent is not in the schema', () => {
        create({ steps: GEO, searchableFields: ['state'], dependsOn: { state: 'ghost' } });
        expect(comp.blockedHintOf(GEO[0].fields[1])).toBe('Choose ghost first.');
      });

      it('has no hint for a field without a parent', () => {
        create({ steps: GEO, searchableFields: ['country'] });
        expect(comp.blockedHintOf(GEO[0].fields[0])).toBeNull();
        expect(comp.hasParent(GEO[0].fields[0])).toBe(false);
      });
    });

    it('ignores a dependency on a field the schema does not have, and stops watching when it is replaced', () => {
      create({ steps: GEO, dependsOn: { state: 'missing' } });
      comp.form.controls['state'].setValue('Goa');
      comp.dependsOn = { city: 'state' };
      comp.form.controls['city'].setValue('Panaji');
      comp.form.controls['state'].setValue('Kerala');
      expect(comp.form.controls['city'].value).toBeNull();
      comp.dependsOn = null;
      expect(comp.dependsOn).toEqual({});
    });
  });
});
