import { ErrorHandler, inject, Injectable, isDevMode } from '@angular/core';
import { ApiError } from '../api/api-error';
import { ToastService } from './toast.service';

/**
 * Global ErrorHandler for errors nobody handled (template errors, rejected promises, effects…).
 * HTTP failures are already normalised and toasted by `errorInterceptor`, so ApiErrors are only logged.
 */
@Injectable()
export class AppErrorHandler implements ErrorHandler {
  private readonly toasts = inject(ToastService);
  private lastToastAt = 0;

  handleError(error: unknown): void {
    const unwrapped = unwrap(error);
    if (unwrapped instanceof ApiError) {
      if (isDevMode()) console.warn('[unhandled ApiError]', unwrapped);
      return;
    }
    if (isChunkLoadError(unwrapped)) {
      this.toasts.error('A new version is available. Reload to continue.', {
        key: 'chunk-load',
        durationMs: 0,
        action: { label: 'Reload', run: () => location.reload() },
      });
      return;
    }
    console.error(unwrapped);
    const now = Date.now();
    if (now - this.lastToastAt > 5000) {
      this.lastToastAt = now;
      this.toasts.error('Something went wrong. Please try again.', { key: 'app-error' });
    }
  }
}

function unwrap(error: unknown): unknown {
  let current = error;
  for (let i = 0; i < 5; i++) {
    const inner =
      (current as { rejection?: unknown; ngOriginalError?: unknown } | null)?.rejection ??
      (current as { ngOriginalError?: unknown } | null)?.ngOriginalError;
    if (!inner) break;
    current = inner;
  }
  return current;
}

function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(
    message,
  );
}
