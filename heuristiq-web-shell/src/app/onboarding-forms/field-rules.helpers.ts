import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { MAX_FIELD_PATTERN_LENGTH } from './onboarding-forms.constants';
import { FieldType, SchemaStep } from './onboarding-forms.models';
import { NUMERIC_TYPES, TEXT_LIKE_TYPES } from './field-dialog/field-dialog.constants';

/** What can be wrong with the rules of a form field; each is also the translation key under `onboardingForms.rules`. */
export type FieldRuleCode = 'patternInvalid' | 'patternTooLong' | 'lengthNegative' | 'lengthRange' | 'valueRange' | 'keyDuplicate'
  | 'ageNegative' | 'ageRange' | 'ageWithFuture';

export interface FieldRuleProblem {
  code: FieldRuleCode;
  /** Key of the field the problem is on. */
  field: string;
}

/** The rule inputs of a field as typed: numbers may still be strings (or blank) when they come straight from the form. */
export interface FieldRuleInput {
  key: string;
  /** When given, only the rules that apply to the type are checked (a number field keeps no pattern, a text field no value range). */
  type?: FieldType;
  pattern?: string | null;
  minLength?: number | string | null;
  maxLength?: number | string | null;
  minValue?: number | string | null;
  maxValue?: number | string | null;
  dateRange?: string | null;
  minAgeYears?: number | string | null;
  maxAgeYears?: number | string | null;
}

/** The number typed, or null when the box is blank or not a number. */
function toNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

/** Whether the browser can compile the pattern. The server compiles it with Java's engine and has the last word. */
function compiles(pattern: string): boolean {
  try {
    new RegExp(pattern);
    return true;
  } catch {
    return false;
  }
}

/** The problems with one field's pattern, length range and value range (not its key: see {@link schemaRuleProblems}). */
export function fieldRuleProblems(field: FieldRuleInput): FieldRuleProblem[] {
  const problems: FieldRuleProblem[] = [];
  const add = (code: FieldRuleCode): void => {
    problems.push({ code, field: field.key });
  };
  const textRules = !field.type || TEXT_LIKE_TYPES.includes(field.type);
  const numberRules = !field.type || NUMERIC_TYPES.includes(field.type);
  const pattern = textRules ? field.pattern?.trim() : undefined;
  if (pattern) {
    if (pattern.length > MAX_FIELD_PATTERN_LENGTH) {
      add('patternTooLong');
    } else if (!compiles(pattern)) {
      add('patternInvalid');
    }
  }
  const minLength = textRules ? toNumber(field.minLength) : null;
  const maxLength = textRules ? toNumber(field.maxLength) : null;
  if ((minLength !== null && minLength < 0) || (maxLength !== null && maxLength < 0)) {
    add('lengthNegative');
  } else if (minLength !== null && maxLength !== null && minLength > maxLength) {
    add('lengthRange');
  }
  const minValue = numberRules ? toNumber(field.minValue) : null;
  const maxValue = numberRules ? toNumber(field.maxValue) : null;
  if (minValue !== null && maxValue !== null && minValue > maxValue) {
    add('valueRange');
  }
  // Date rules (DATE only), the same checks as Rider_OnboardingService's OnboardingSchemaValidator.
  const dateRules = !field.type || field.type === 'DATE';
  const minAge = dateRules ? toNumber(field.minAgeYears) : null;
  const maxAge = dateRules ? toNumber(field.maxAgeYears) : null;
  if ((minAge !== null && minAge < 0) || (maxAge !== null && maxAge < 0)) {
    add('ageNegative');
  } else if (minAge !== null && maxAge !== null && minAge > maxAge) {
    add('ageRange');
  }
  if (dateRules && field.dateRange === 'FUTURE' && ((minAge !== null && minAge > 0) || maxAge !== null)) {
    add('ageWithFuture');
  }
  return problems;
}

/** Every problem in a whole form: each field's rules, and a key used by more than one field (keys are the answer columns). */
export function schemaRuleProblems(steps: readonly SchemaStep[]): FieldRuleProblem[] {
  const problems: FieldRuleProblem[] = [];
  const seen = new Set<string>();
  const reported = new Set<string>();
  for (const step of steps) {
    for (const field of step.fields) {
      problems.push(...fieldRuleProblems(field));
      if (seen.has(field.key) && !reported.has(field.key)) {
        problems.push({ code: 'keyDuplicate', field: field.key });
        reported.add(field.key);
      }
      seen.add(field.key);
    }
  }
  return problems;
}

/** Form-group validator: sets one error per broken rule (named by its code) so the dialog can show each message. */
export function fieldRulesValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const value = group.value as FieldRuleInput;
    if (!value) {
      return null;
    }
    const errors: ValidationErrors = {};
    for (const problem of fieldRuleProblems(value)) {
      errors[problem.code] = true;
    }
    return Object.keys(errors).length > 0 ? errors : null;
  };
}

/** Control validator: the key must not be used by another field of the form. */
export function uniqueFieldKeyValidator(takenKeys: readonly string[]): ValidatorFn {
  const taken = new Set(takenKeys);
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value as string | null)?.trim();
    return value && taken.has(value) ? { keyDuplicate: true } : null;
  };
}
