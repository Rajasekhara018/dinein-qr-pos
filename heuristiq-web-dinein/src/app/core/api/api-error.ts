import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorCode, CartProblem, ErrorResponse, FieldErrorDetail } from './models';

/**
 * Normalised error thrown by every HttpClient call (see `errorInterceptor`). Features switch on `code` (the backend's
 * stable machine-readable code) or `status`; `message` is safe to show to users.
 */
export class ApiError extends Error {
  override readonly name = 'ApiError';

  constructor(
    readonly status: number,
    readonly code: ApiErrorCode,
    message: string,
    readonly details: unknown[] = [],
    readonly traceId?: string,
    /** Seconds from a `Retry-After` header (429/503), when present. */
    readonly retryAfter?: number,
  ) {
    super(message);
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }

  /** `details` typed as the ITEM_UNAVAILABLE cart problems. */
  get cartProblems(): CartProblem[] {
    return this.code === 'ITEM_UNAVAILABLE' ? (this.details as CartProblem[]) : [];
  }

  /** `details` typed as VALIDATION_FAILED field errors. */
  get fieldErrors(): FieldErrorDetail[] {
    return this.code === 'VALIDATION_FAILED' ? (this.details as FieldErrorDetail[]) : [];
  }

  static fromHttpError(error: HttpErrorResponse): ApiError {
    const retryAfterHeader = error.headers?.get('Retry-After');
    const retryAfter = retryAfterHeader ? Number(retryAfterHeader) || undefined : undefined;
    const traceHeader = error.headers?.get('X-Trace-Id') ?? undefined;
    if (error.status === 0) {
      return new ApiError(
        0,
        'NETWORK_ERROR',
        'You appear to be offline. Check your connection and try again.',
      );
    }
    const body = parseBody(error.error);
    if (body) {
      return new ApiError(
        error.status,
        body.code,
        body.message || defaultMessage(error.status),
        Array.isArray(body.details) ? body.details : [],
        body.traceId ?? traceHeader,
        retryAfter,
      );
    }
    return new ApiError(
      error.status,
      defaultCode(error.status),
      defaultMessage(error.status),
      [],
      traceHeader,
      retryAfter,
    );
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error;
    if (error instanceof HttpErrorResponse) return ApiError.fromHttpError(error);
    const message = error instanceof Error ? error.message : 'Something went wrong';
    return new ApiError(-1, 'CLIENT_ERROR', message);
  }
}

function parseBody(raw: unknown): ErrorResponse | null {
  let value = raw;
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      return null;
    }
  }
  if (value && typeof value === 'object' && typeof (value as ErrorResponse).code === 'string') {
    return value as ErrorResponse;
  }
  return null;
}

function defaultCode(status: number): ApiErrorCode {
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  if (status >= 500) return 'INTERNAL_ERROR';
  return 'BAD_REQUEST';
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Please sign in to continue.';
  if (status === 403) return 'You do not have access to this.';
  if (status === 404) return 'Not found.';
  if (status === 429) return 'Too many attempts. Please wait a moment and try again.';
  if (status >= 500) return 'Something went wrong on our side. Please try again.';
  return 'The request could not be completed.';
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
