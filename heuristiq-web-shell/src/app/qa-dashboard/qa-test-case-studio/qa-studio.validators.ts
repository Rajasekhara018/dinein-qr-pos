import { AbstractControl, FormArray, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

import {
  assertionRowIssue,
  assertionsIssue,
  bodyIssue,
  descriptionIssue,
  keyValueRowIssue,
  keyValueTableIssue,
  nameIssue,
  pathIssue,
  subModuleIssue,
} from './qa-studio.helpers';
import { QaAssertionRow, QaKeyValueRow, QaStudioIssue } from './qa-studio.models';

/** The error every studio validator reports: the rule that was broken, ready to be translated. */
export const STUDIO_ISSUE_ERROR = 'studioIssue';

const asError = (found: QaStudioIssue | null): ValidationErrors | null => (found ? { [STUDIO_ISSUE_ERROR]: found } : null);

/** The issue a control or group carries, or null. */
export function issueOf(control: AbstractControl | null): QaStudioIssue | null {
  return (control?.errors?.[STUDIO_ISSUE_ERROR] as QaStudioIssue | undefined) ?? null;
}

export const studioNameValidator: ValidatorFn = (c) => asError(nameIssue(String(c.value ?? '')));
export const studioDescriptionValidator: ValidatorFn = (c) => asError(descriptionIssue(String(c.value ?? '')));
export const studioSubModuleValidator: ValidatorFn = (c) => asError(subModuleIssue(String(c.value ?? '')));
export const studioPathValidator: ValidatorFn = (c) => asError(pathIssue(String(c.value ?? '')));

/** The body is checked against the method of the same form, so the form must hold a `method` control. */
export const studioBodyValidator: ValidatorFn = (c) => {
  const method = String(c.parent?.get('method')?.value ?? 'GET');
  return asError(bodyIssue(String(c.value ?? ''), method));
};

/** One row of the headers or query table. */
export const studioKeyValueRowValidator =
  (kind: 'query' | 'headers'): ValidatorFn =>
  (c) => {
    const row = (c as FormGroup).getRawValue() as QaKeyValueRow;
    return asError(keyValueRowIssue(row, kind));
  };

/** The table as a whole: how many rows and no name twice. */
export const studioKeyValueTableValidator =
  (kind: 'query' | 'headers'): ValidatorFn =>
  (c) =>
    asError(keyValueTableIssue((c as FormArray).getRawValue() as QaKeyValueRow[], kind));

/** One assertion row: what it needs depends on its type. */
export const studioAssertionRowValidator: ValidatorFn = (c) => asError(assertionRowIssue((c as FormGroup).getRawValue() as QaAssertionRow));

/** The list of assertions: at most 20 and a status check among them. */
export const studioAssertionsValidator: ValidatorFn = (c) => asError(assertionsIssue((c as FormArray).getRawValue() as QaAssertionRow[]));
