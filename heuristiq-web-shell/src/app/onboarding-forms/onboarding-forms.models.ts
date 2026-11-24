/** Mirrors com.heuristiq.onboardingservice.dto.FieldType exactly. */
export type FieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'CHECKBOX' | 'DOCUMENT' | 'PHONE' | 'EMAIL' | 'CURRENCY';

/** Mirrors com.heuristiq.onboardingservice.domain.SchemaStatus exactly. */
export type SchemaStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** Mirrors com.heuristiq.onboardingservice.dto.SchemaField exactly. */
export interface SchemaField {
  key: string;
  label: string;
  type: FieldType;
  required: boolean;
  options?: string[];
  documentCategory?: string;
  /** DOCUMENT fields: the document type (Document types screen) whose file rules apply; its category is the field's category. */
  documentType?: string;
  /** Custom regex overriding the type's built-in default (PHONE/EMAIL). Only enforced for
   *  TEXT if explicitly set (TEXT has no built-in default of its own). */
  pattern?: string;
  /** Shown instead of the generic per-rule message when the pattern fails to match. */
  patternErrorMessage?: string;
  /** Range check for NUMBER/CURRENCY. */
  minValue?: number;
  maxValue?: number;
  /** Length check for TEXT/PHONE/EMAIL. */
  minLength?: number;
  maxLength?: number;
  /** DATE: PAST (today or earlier) or FUTURE (today or later); absent for any date. */
  dateRange?: DateRange;
  /** DATE: the date must be at least / at most this many whole years before today (an age). */
  minAgeYears?: number;
  maxAgeYears?: number;
}

/** Which dates a DATE field accepts (Rider_OnboardingService DateRange). */
export type DateRange = 'PAST' | 'FUTURE';

/** Mirrors com.heuristiq.onboardingservice.dto.SchemaStep exactly. */
export interface SchemaStep {
  key: string;
  title: string;
  fields: SchemaField[];
}

/** Mirrors com.heuristiq.onboardingservice.dto.OnboardingSchemaDefinition exactly. */
export interface OnboardingSchemaDefinition {
  steps: SchemaStep[];
}

/** Mirrors com.heuristiq.onboardingservice.dto.OnboardingSchemaResponse exactly. */
export interface OnboardingSchemaResponse {
  id: string;
  typeCode: string;
  version: number;
  status: SchemaStatus;
  definition: OnboardingSchemaDefinition;
  publishedAt?: string;
  createdAt?: string;
}

/** Mirrors com.heuristiq.onboardingservice.domain.OnboardingTypeEntity (GET /admin/onboarding-types row shape). */
export interface OnboardingType {
  id: string;
  code: string;
  displayName: string;
  active: boolean;
  requiresReview: boolean;
}

/** Body of POST /admin/onboarding-types. */
export interface CreateOnboardingTypeRequest {
  code: string;
  displayName: string;
  requiresReview: boolean;
}

/** Response envelope of Rider_OnboardingService (boolean `status`, unlike UAM's `success`). */
export interface OnboardingEnvelope<T> {
  status: boolean;
  data?: T;
  message?: string;
  errorCode?: string;
}

/** Raw value of the "new type" inline form. */
export interface NewTypeFormValue {
  code: string | null;
  displayName: string | null;
  requiresReview: boolean | null;
}

/** Raw value of the field dialog's form. */
export interface FieldFormValue {
  key: string;
  label: string;
  type: FieldType;
  required: boolean | null;
  documentCategory: string | null;
  documentType: string | null;
  options: string[];
  pattern: string | null;
  patternErrorMessage: string | null;
  minValue: number | string | null;
  maxValue: number | string | null;
  minLength: number | string | null;
  maxLength: number | string | null;
  /** DATE: '' (any date), PAST or FUTURE. */
  dateRange: DateRange | '' | null;
  minAgeYears: number | string | null;
  maxAgeYears: number | string | null;
}
