import { hqOnboardingCleanAnswers, hqOnboardingDrawnFields, hqOnboardingFieldKind } from './hq-onboarding-form.helpers';
import { HqOnboardingField } from './hq-onboarding-form.model';
import { HQ_ONBOARDING_FORM_DEFAULT_TEXTS } from './hq-onboarding-form.texts';
import { HQ_ONBOARDING_EMAIL_PATTERN } from './hq-onboarding-form.constants';

const f = (key: string, type: HqOnboardingField['type'], extra: Partial<HqOnboardingField> = {}): HqOnboardingField =>
  ({ key, label: key, type, ...extra });

describe('hqOnboardingFieldKind', () => {
  it('maps each schema type, case-insensitively', () => {
    const cases: [HqOnboardingField['type'], string][] = [
      ['NUMBER', 'number'], ['currency', 'number'], ['DATE', 'date'], ['select', 'select'], ['CHECKBOX', 'checkbox'],
      ['PHONE', 'phone'], ['email', 'email'], ['DOCUMENT', 'document'], ['TEXT', 'text'], ['PATTERN', 'text'],
    ];
    for (const [type, kind] of cases) expect(hqOnboardingFieldKind(f('k', type)), type).toBe(kind);
  });

  it('draws a field with non-empty override choices as a select', () => {
    expect(hqOnboardingFieldKind(f('k', 'TEXT'), ['A'])).toBe('select');
    expect(hqOnboardingFieldKind(f('k', 'TEXT'), [])).toBe('text');
    expect(hqOnboardingFieldKind(f('k', 'DATE'), null)).toBe('date');
  });
});

describe('hqOnboardingDrawnFields', () => {
  it('flattens the steps and skips documents', () => {
    const fields = hqOnboardingDrawnFields([
      { key: 'a', title: 'A', fields: [f('x', 'TEXT'), f('doc', 'DOCUMENT')] },
      { key: 'b', title: 'B', fields: [f('y', 'NUMBER')] },
    ]);
    expect(fields.map((x) => x.key)).toEqual(['x', 'y']);
  });
});

describe('hqOnboardingCleanAnswers', () => {
  const fields = [
    f('text', 'TEXT'), f('blank', 'TEXT'), f('num', 'NUMBER'), f('nan', 'NUMBER'), f('optBox', 'CHECKBOX'),
    f('reqBox', 'CHECKBOX', { required: true }), f('ticked', 'CHECKBOX'), f('obj', 'TEXT'), f('nil', 'TEXT'), f('undef', 'TEXT'),
  ];

  it('trims text, keeps numbers and booleans, and drops empty or unusable values', () => {
    const answers = hqOnboardingCleanAnswers(fields, {
      text: '  hi  ', blank: '   ', num: 0, nan: Number.NaN, optBox: false, reqBox: false, ticked: true,
      obj: { a: 1 }, nil: null, extra: 'ignored',
    });
    expect(answers).toEqual({ text: 'hi', num: 0, reqBox: false, ticked: true });
  });
});

describe('onboarding defaults', () => {
  it('has English messages with the values filled in', () => {
    const t = HQ_ONBOARDING_FORM_DEFAULT_TEXTS;
    expect(t.required('Name')).toBe('Name is required.');
    expect(t.invalidEmail()).toBe('Enter a valid email address.');
    expect(t.invalidPattern('Code')).toBe('Code is not in the expected format.');
    expect(t.min('Age', 1)).toBe('Age must be at least 1.');
    expect(t.max('Age', 9)).toBe('Age must be at most 9.');
    expect(t.minLength('Name', 2)).toBe('Name must be at least 2 characters.');
    expect(t.maxLength('Name', 5)).toBe('Name must be at most 5 characters.');
    expect(t.dateMin('D', '2000-01-01')).toBe('D must be on or after 2000-01-01.');
    expect(t.dateMax('D', '2001-01-01')).toBe('D must be on or before 2001-01-01.');
  });

  it('accepts only plausible email addresses', () => {
    expect(HQ_ONBOARDING_EMAIL_PATTERN.test('a@b.co')).toBe(true);
    expect(HQ_ONBOARDING_EMAIL_PATTERN.test('a@b')).toBe(false);
    expect(HQ_ONBOARDING_EMAIL_PATTERN.test('a b@c.de')).toBe(false);
  });
});
