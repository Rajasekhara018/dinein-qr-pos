import { HqOnboardingAnswer, HqOnboardingAnswers, HqOnboardingField, HqOnboardingFieldKind, HqOnboardingStep } from './hq-onboarding-form.model';

/** How a field is drawn: the type read case-insensitively, a field with configured choices drawn as a select. */
export function hqOnboardingFieldKind(field: HqOnboardingField, optionOverride?: readonly string[] | null): HqOnboardingFieldKind {
  if (optionOverride && optionOverride.length > 0) return 'select';
  switch (field.type.toUpperCase()) {
    case 'NUMBER':
    case 'CURRENCY':
      return 'number';
    case 'DATE':
      return 'date';
    case 'SELECT':
      return 'select';
    case 'CHECKBOX':
      return 'checkbox';
    case 'PHONE':
      return 'phone';
    case 'EMAIL':
      return 'email';
    case 'DOCUMENT':
      return 'document';
    default:
      return 'text';
  }
}

/** The fields of the steps that this component draws (documents are not). */
export function hqOnboardingDrawnFields(steps: readonly HqOnboardingStep[]): HqOnboardingField[] {
  return steps.flatMap((step) => step.fields).filter((field) => hqOnboardingFieldKind(field) !== 'document');
}

/**
 * The answers to send: trimmed text, numbers as numbers, nothing for an empty field. An untouched, optional
 * checkbox is left out; a ticked one is `true`.
 */
export function hqOnboardingCleanAnswers(
  fields: readonly HqOnboardingField[],
  raw: Record<string, unknown>,
): HqOnboardingAnswers {
  const answers: HqOnboardingAnswers = {};
  for (const field of fields) {
    const value = raw[field.key];
    if (value === null || value === undefined) continue;
    let answer: HqOnboardingAnswer;
    if (typeof value === 'string') {
      const text = value.trim();
      if (!text) continue;
      answer = text;
    } else if (typeof value === 'number') {
      if (Number.isNaN(value)) continue;
      answer = value;
    } else if (typeof value === 'boolean') {
      if (!value && !field.required) continue;
      answer = value;
    } else {
      continue;
    }
    answers[field.key] = answer;
  }
  return answers;
}
