import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';
import { ApiError } from '../../../core/api/api-error';

/** Custom messages per error key; a function receives the error value (e.g. `{ requiredLength }`). */
export type ErrorMessages = Record<string, string | ((value: unknown) => string)>;

/** First human-readable message for a control's `errors`. */
export function describeError(errors: ValidationErrors | null, custom: ErrorMessages = {}): string {
  if (!errors) return '';
  const [key, value] = Object.entries(errors)[0];
  const override = custom[key];
  if (typeof override === 'function') return override(value);
  if (typeof override === 'string') return override;
  const v = value as Record<string, number> | string | true;
  switch (key) {
    case 'required':
      return 'This field is required.';
    case 'minlength':
      return `Use at least ${(v as Record<string, number>)['requiredLength']} characters.`;
    case 'maxlength':
      return `Use at most ${(v as Record<string, number>)['requiredLength']} characters.`;
    case 'min':
      return `Must be at least ${(v as Record<string, number>)['min']}.`;
    case 'max':
      return `Must be at most ${(v as Record<string, number>)['max']}.`;
    case 'email':
      return 'Enter a valid e-mail address.';
    case 'pattern':
      return 'The format is not valid.';
    case 'server':
      return String(v);
    default:
      return typeof v === 'string' ? v : 'This value is not valid.';
  }
}

/** Friendly message for any error thrown by an API call. */
export function errorMessage(
  error: unknown,
  fallback = 'Something went wrong. Please try again.',
): string {
  if (error instanceof ApiError) return error.message || fallback;
  return fallback;
}

/** `variants[0].price` (Spring) → `variants.0.price` (Angular forms path). */
export function toControlPath(field: string): string {
  return field.replace(/\[(\d+)\]/g, '.$1');
}

/**
 * Puts VALIDATION_FAILED `details` on the matching controls as `{ server: message }` and marks them touched.
 * Returns the messages that did not match any control (show them in a form-level alert).
 */
export function applyServerErrors(
  form: FormGroup,
  error: unknown,
  fieldMap: Record<string, string> = {},
): string[] {
  if (!(error instanceof ApiError)) return [];
  const unmatched: string[] = [];
  for (const detail of error.fieldErrors) {
    const control: AbstractControl | null = form.get(toControlPath(fieldMap[detail.field] ?? detail.field));
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: detail.message });
      control.markAsTouched();
    } else {
      unmatched.push(`${detail.field}: ${detail.message}`);
    }
  }
  return unmatched;
}

/** Sets a server error on one control (e.g. DUPLICATE_NAME → name). */
export function setServerError(control: AbstractControl, message: string): void {
  control.setErrors({ ...(control.errors ?? {}), server: message });
  control.markAsTouched();
}
