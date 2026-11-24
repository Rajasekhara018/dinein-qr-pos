import { FormControl, FormGroup } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { fieldRuleProblems, fieldRulesValidator, schemaRuleProblems, uniqueFieldKeyValidator } from './field-rules.helpers';
import { MAX_FIELD_PATTERN_LENGTH } from './onboarding-forms.constants';
import { SchemaField, SchemaStep } from './onboarding-forms.models';

const field = (key: string, extra: Partial<SchemaField> = {}): SchemaField => ({ key, label: key, type: 'TEXT', required: false, ...extra });
const step = (key: string, ...fields: SchemaField[]): SchemaStep => ({ key, title: key, fields });
const codes = (input: Parameters<typeof fieldRuleProblems>[0]) => fieldRuleProblems(input).map((p) => p.code);

describe('field rule helpers', () => {
  describe('pattern', () => {
    it('accepts a pattern that compiles, including an escaped bracket', () => {
      expect(codes({ key: 'a', pattern: '^[A-Z]{5}[0-9]{4}[A-Z]$' })).toEqual([]);
      expect(codes({ key: 'a', pattern: '\\[' })).toEqual([]);
    });

    it('refuses a pattern that does not compile, naming the field', () => {
      expect(fieldRuleProblems({ key: 'licence', pattern: '[' })).toEqual([{ code: 'patternInvalid', field: 'licence' }]);
      expect(codes({ key: 'a', pattern: '(abc' })).toEqual(['patternInvalid']);
    });

    it('refuses a pattern longer than the limit, but not one of exactly that length', () => {
      expect(codes({ key: 'a', pattern: 'a'.repeat(MAX_FIELD_PATTERN_LENGTH + 1) })).toEqual(['patternTooLong']);
      expect(codes({ key: 'a', pattern: 'a'.repeat(MAX_FIELD_PATTERN_LENGTH) })).toEqual([]);
    });

    it('ignores a blank pattern', () => {
      expect(codes({ key: 'a', pattern: '   ' })).toEqual([]);
      expect(codes({ key: 'a', pattern: null })).toEqual([]);
    });
  });

  describe('length range', () => {
    it('refuses a minimum above the maximum (the QA case: 10 and 2)', () => {
      expect(codes({ key: 'a', minLength: 10, maxLength: 2 })).toEqual(['lengthRange']);
      expect(codes({ key: 'a', minLength: '10', maxLength: '2' })).toEqual(['lengthRange']);
    });

    it('accepts equal, one-sided and blank bounds', () => {
      expect(codes({ key: 'a', minLength: 4, maxLength: 4 })).toEqual([]);
      expect(codes({ key: 'a', minLength: 3 })).toEqual([]);
      expect(codes({ key: 'a', maxLength: 0 })).toEqual([]);
      expect(codes({ key: 'a', minLength: '', maxLength: null })).toEqual([]);
    });

    it('refuses a negative length', () => {
      expect(codes({ key: 'a', minLength: -1 })).toEqual(['lengthNegative']);
      expect(codes({ key: 'a', maxLength: -5, minLength: 2 })).toEqual(['lengthNegative']);
    });
  });

  describe('value range', () => {
    it('refuses a minimum above the maximum', () => {
      expect(codes({ key: 'a', minValue: 65.5, maxValue: 18 })).toEqual(['valueRange']);
    });

    it('accepts a valid or one-sided range, and a negative bound', () => {
      expect(codes({ key: 'a', minValue: -5, maxValue: 5 })).toEqual([]);
      expect(codes({ key: 'a', minValue: 1 })).toEqual([]);
    });
  });

  describe('only the rules of the field type apply', () => {
    it('ignores a pattern and length range left over on a number field', () => {
      expect(codes({ key: 'a', type: 'NUMBER', pattern: '[', minLength: 10, maxLength: 2 })).toEqual([]);
    });

    it('ignores a value range left over on a text field', () => {
      expect(codes({ key: 'a', type: 'TEXT', minValue: 9, maxValue: 1 })).toEqual([]);
    });

    it('checks value ranges on number and currency fields', () => {
      expect(codes({ key: 'a', type: 'CURRENCY', minValue: 9, maxValue: 1 })).toEqual(['valueRange']);
    });
  });

  describe('a whole form', () => {
    it('is fine when every field is sound', () => {
      expect(schemaRuleProblems([step('s1', field('a'), field('b', { minLength: 1, maxLength: 5 })), step('s2', field('c'))])).toEqual([]);
    });

    it('reports a key used in two steps once, and each broken rule with its field', () => {
      const problems = schemaRuleProblems([
        step('s1', field('email'), field('name', { pattern: '[' })),
        step('s2', field('email'), field('email'), field('age', { type: 'NUMBER', minValue: 9, maxValue: 1 })),
      ]);
      expect(problems).toEqual([
        { code: 'patternInvalid', field: 'name' },
        { code: 'keyDuplicate', field: 'email' },
        { code: 'valueRange', field: 'age' },
      ]);
    });
  });

  describe('form validators', () => {
    it('the group validator sets one error per broken rule and none when sound', () => {
      const group = new FormGroup({ key: new FormControl('a'), type: new FormControl('TEXT'), pattern: new FormControl('['), minLength: new FormControl(10), maxLength: new FormControl(2) }, { validators: [fieldRulesValidator()] });
      expect(group.errors).toEqual({ patternInvalid: true, lengthRange: true });
      group.patchValue({ pattern: '^a$', minLength: 1 });
      expect(group.errors).toBeNull();
    });

    it('the key validator refuses a key another field already uses, but not the field\'s own', () => {
      const validate = uniqueFieldKeyValidator(['email', 'phone']);
      expect(validate(new FormControl('email'))).toEqual({ keyDuplicate: true });
      expect(validate(new FormControl(' phone '))).toEqual({ keyDuplicate: true });
      expect(validate(new FormControl('fullName'))).toBeNull();
      expect(validate(new FormControl(''))).toBeNull();
      expect(uniqueFieldKeyValidator([])(new FormControl('email'))).toBeNull();
    });
  });

  describe('date rules', () => {
    const codes = (input: Parameters<typeof fieldRuleProblems>[0]) => fieldRuleProblems(input).map((p) => p.code);

    it('accepts a date of birth and an expiry date set up the usual way', () => {
      expect(codes({ key: 'dob', type: 'DATE', dateRange: 'PAST', minAgeYears: 18, maxAgeYears: 100 })).toEqual([]);
      expect(codes({ key: 'expiry', type: 'DATE', dateRange: 'FUTURE' })).toEqual([]);
    });

    it('refuses a negative or inverted age range', () => {
      expect(codes({ key: 'dob', type: 'DATE', minAgeYears: -1 })).toEqual(['ageNegative']);
      expect(codes({ key: 'dob', type: 'DATE', minAgeYears: '60', maxAgeYears: '18' })).toEqual(['ageRange']);
    });

    it('refuses an age limit on a future-only date', () => {
      expect(codes({ key: 'expiry', type: 'DATE', dateRange: 'FUTURE', minAgeYears: 18 })).toEqual(['ageWithFuture']);
      expect(codes({ key: 'expiry', type: 'DATE', dateRange: 'FUTURE', minAgeYears: 0 })).toEqual([]);
    });

    it('ignores date rules on a field that is not a date', () => {
      expect(codes({ key: 'name', type: 'TEXT', minAgeYears: 60, maxAgeYears: 18 })).toEqual([]);
    });
  });
});
