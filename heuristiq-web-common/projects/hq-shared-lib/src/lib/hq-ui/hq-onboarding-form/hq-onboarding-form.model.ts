/** Field types Rider_OnboardingService can author (its `FieldType` enum), upper case as the service sends them. */
export type HqOnboardingFieldType =
  | 'TEXT'
  | 'NUMBER'
  | 'DATE'
  | 'SELECT'
  | 'CHECKBOX'
  | 'DOCUMENT'
  | 'PHONE'
  | 'EMAIL'
  | 'CURRENCY'
  | 'PATTERN';

/** One question of an onboarding form (the service's `SchemaField`). */
export interface HqOnboardingField {
  key: string;
  label: string;
  /** Read case-insensitively, so `text` and `TEXT` both work. */
  type: HqOnboardingFieldType | Lowercase<HqOnboardingFieldType>;
  required?: boolean;
  /** Choices of a SELECT field. */
  options?: string[];
  /** Regular expression the answer must match (a TEXT field with a pattern is a "pattern" field). */
  pattern?: string;
  /** Shown instead of the generic message when `pattern` does not match. */
  patternErrorMessage?: string;
  minValue?: number;
  maxValue?: number;
  minLength?: number;
  maxLength?: number;
  /** DOCUMENT fields: not rendered by this component. */
  documentCategory?: string;
}

/** A group of fields shown under one heading (the service's `SchemaStep`). */
export interface HqOnboardingStep {
  key: string;
  title: string;
  fields: HqOnboardingField[];
}

/** The value of one answered field. */
export type HqOnboardingAnswer = string | number | boolean;

/** Answers by field key; empty fields are left out. */
export type HqOnboardingAnswers = Record<string, HqOnboardingAnswer>;

/** An inclusive date range (yyyy-MM-dd) a DATE field must fall in. Either bound may be left out. */
export interface HqOnboardingDateLimit {
  min?: string;
  max?: string;
}

/** How a field is drawn once overrides are applied. */
export type HqOnboardingFieldKind = 'text' | 'email' | 'phone' | 'number' | 'date' | 'select' | 'checkbox' | 'document';
