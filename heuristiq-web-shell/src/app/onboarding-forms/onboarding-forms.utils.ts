import { formatLocaleInZone } from 'hq-shared-lib';
import { apiErrorMessage } from '../shared/utils/api-error.helpers';
import { SchemaStep } from './onboarding-forms.models';

interface ThrownError {
  error?: { detail?: string; message?: string };
  message?: string;
}

/** Pulls the most specific human-readable message out of a thrown service/HTTP error. */
export function errorMessage(err: unknown, fallback: string): string {
  const e = err as ThrownError | null | undefined;
  return apiErrorMessage(err, e?.message || fallback);
}

/** Deep-ish copy so builder edits never mutate a version held in the history list. */
export function cloneSteps(steps: SchemaStep[]): SchemaStep[] {
  return steps.map((step) => ({
    ...step,
    fields: step.fields.map((field) => ({ ...field, options: field.options ? [...field.options] : undefined })),
  }));
}

export function formatDateTime(iso: string | undefined, locale: string, zone?: string | null): string {
  if (!iso) return '-';
  return formatLocaleInZone(iso, 'datetime', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }, locale, zone);
}
