import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '../api/api-error';
import { ToastService } from '../ui/toast.service';
import { SKIP_ERROR_TOAST } from './http-context';

/**
 * Outermost interceptor: turns every `HttpErrorResponse` into a typed {@link ApiError} and shows a toast for
 * unexpected failures (network errors and 5xx). Handled 4xx errors are left to the caller; callers that handle
 * 5xx themselves pass `silentErrors()` as the request context.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toasts = inject(ToastService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const apiError = ApiError.fromHttpError(error);
      if (
        (apiError.isNetworkError || apiError.isServerError) &&
        !req.context.get(SKIP_ERROR_TOAST)
      ) {
        toasts.error(apiError.message, { key: `http-${apiError.code}` });
      }
      return throwError(() => apiError);
    }),
  );
};
