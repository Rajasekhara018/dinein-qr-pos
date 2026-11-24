export interface PasswordCheck {
  /** Translation key (shared.passwordForm.checks.*) -- render with the `translate` pipe. */
  label: string;
  valid: boolean;
}

export type PasswordStrength = 'weak' | 'medium' | 'strong';

export function getPasswordChecks(value: string): PasswordCheck[] {
  return [
    { label: 'shared.passwordForm.checks.length', valid: value.length >= 13 },
    { label: 'shared.passwordForm.checks.mixedCase', valid: /[A-Z]/.test(value) && /[a-z]/.test(value) },
    { label: 'shared.passwordForm.checks.numberOrSymbol', valid: /[\d\W_]/.test(value) },
  ];
}

export function getPasswordStrength(checks: PasswordCheck[]): PasswordStrength {
  const passed = checks.filter((c) => c.valid).length;
  if (passed >= 3) return 'strong';
  if (passed >= 2) return 'medium';
  return 'weak';
}
