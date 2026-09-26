import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Mirrors the backend `PasswordPolicy`: 8–72 characters with at least one letter and one digit. */
export const passwordPolicy: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '');
  if (!value) return null; // `required` reports empty values
  if (value.length < 8 || value.length > 72 || !/\p{L}/u.test(value) || !/\d/.test(value)) {
    return { weakPassword: true };
  }
  return null;
};

/** Group validator: `newPassword` must equal `confirmPassword` and differ from `currentPassword`. */
export const passwordChangeRules: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const current = group.get('currentPassword')?.value as string | undefined;
  const next = group.get('newPassword')?.value as string | undefined;
  const confirm = group.get('confirmPassword')?.value as string | undefined;
  const errors: ValidationErrors = {};
  if (next && confirm && next !== confirm) errors['mismatch'] = true;
  if (next && current && next === current) errors['reused'] = true;
  return Object.keys(errors).length ? errors : null;
};

export const PASSWORD_MESSAGES = {
  weakPassword: 'Use 8–72 characters with at least one letter and one digit.',
};

/** A random temporary password that satisfies the policy (for new staff / resets). */
export function generateTemporaryPassword(length = 12): string {
  const letters = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ';
  const digits = '23456789';
  const all = letters + digits;
  const random = new Uint32Array(length);
  crypto.getRandomValues(random);
  const chars = Array.from(random, (n) => all[n % all.length]);
  chars[0] = letters[random[0] % letters.length];
  chars[length - 1] = digits[random[length - 1] % digits.length];
  return chars.join('');
}
