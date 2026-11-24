/**
 * Input transforms for the common fields. A template passes whatever its expression gives (a form's error message, an optional
 * flag, a translated text that may be missing), so the inputs accept that and keep a plain value inside.
 */

/** Text, or nothing: missing text is empty text. */
export function hqTextInput(value: string | null | undefined): string {
  return value ?? '';
}

/** Any truthy value is on (a form's error message turns an invalid mark on); anything else is off. */
export function hqFlagInput(value: unknown): boolean {
  return !!value;
}

/** A number, or nothing: a missing number is null. */
export function hqNumberInput(value: number | null | undefined): number | null {
  return value ?? null;
}
