import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function matchPasswords(newKey: string, confirmKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const np = group.get(newKey)?.value;
    const cp = group.get(confirmKey)?.value;
    return np === cp ? null : { mismatch: true };
  };
}
