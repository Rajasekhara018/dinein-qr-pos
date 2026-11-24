import { API_ERROR_GENERIC_MESSAGES, API_ERROR_MAX_LENGTH, API_ERROR_SERVER_STATUS_FROM } from './api-error.constants';

interface FieldErrorShape {
  field?: unknown;
  message?: unknown;
  defaultMessage?: unknown;
}

interface ApiErrorBody {
  message?: unknown;
  detail?: unknown;
  errors?: unknown;
  responseObject?: unknown;
}

interface ApiErrorShape {
  status?: unknown;
  error?: unknown;
}

/** Trims, collapses whitespace, rejects HTML/stack traces and caps the length; returns null when nothing usable is left. */
function clean(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const text = value.replace(/\s+/g, ' ').trim();
  if (!text || /<\s*(html|!doctype|body|head)/i.test(text) || /\bat [\w.$]+\(.*\)/.test(text)) {
    return null;
  }
  return text.length > API_ERROR_MAX_LENGTH ? `${text.slice(0, API_ERROR_MAX_LENGTH - 1)}…` : text;
}

function specific(value: unknown): string | null {
  const text = clean(value);
  return text && !API_ERROR_GENERIC_MESSAGES.includes(text.toLowerCase()) ? text : null;
}

function fieldMessage(entry: unknown): string | null {
  if (typeof entry === 'string') return specific(entry);
  const shape = (entry ?? {}) as FieldErrorShape;
  const message = specific(shape.message) ?? specific(shape.defaultMessage);
  if (!message) return null;
  const field = clean(shape.field);
  return field ? `${field}: ${message}` : message;
}

function firstFieldError(list: unknown): string | null {
  if (!Array.isArray(list)) return null;
  for (const entry of list) {
    const text = fieldMessage(entry);
    if (text) return text;
  }
  return null;
}

/**
 * The text to show for a failed API call. Order: the envelope's {@code message} (when it says something specific), RFC 7807
 * {@code detail}, the first field error ({@code errors} or the envelope's {@code responseObject} list), then the screen's
 * translated {@code fallback}. Network failures (status 0) and server faults (5xx) always show the fallback, never server
 * internals. Server texts are English data from the API and are not translated.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  const shape = (error ?? {}) as ApiErrorShape;
  const status = typeof shape.status === 'number' ? shape.status : null;
  if (status === 0 || (status !== null && status >= API_ERROR_SERVER_STATUS_FROM)) return fallback;
  const body = (typeof shape.error === 'object' && shape.error !== null ? shape.error : {}) as ApiErrorBody;
  const response = (typeof body.responseObject === 'object' && body.responseObject !== null ? body.responseObject : null) as
    | (ApiErrorBody & object)
    | null;
  return (
    specific(body.message) ??
    specific(body.detail) ??
    firstFieldError(body.errors) ??
    firstFieldError(body.responseObject) ??
    specific(response?.message) ??
    specific(typeof shape.error === 'string' ? shape.error : null) ??
    fallback
  );
}
