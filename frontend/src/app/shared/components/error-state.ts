import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ApiError } from '../../core/api/api-error';

/**
 * Per-route/per-section error state with a Retry action (Angular has no error boundaries — pages render this
 * explicitly when their data failed to load).
 */
@Component({
  selector: 'app-error-state',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col items-center px-4 py-10 text-center sm:py-16', role: 'alert' },
  template: `
    <div class="mb-4 flex size-16 items-center justify-center rounded-full bg-red-50 text-danger dark:bg-red-950" aria-hidden="true">
      <svg viewBox="0 0 24 24" class="size-8" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M12 9v4m0 4h.01M10.3 3.9 2.4 17.5A2 2 0 0 0 4.1 20.5h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
      </svg>
    </div>
    <h2 class="font-display text-lg font-semibold text-ink sm:text-xl">{{ title() }}</h2>
    <p class="mt-1 max-w-sm text-sm text-ink-muted sm:text-base">{{ message() }}</p>
    <div class="mt-6 flex flex-wrap items-center justify-center gap-3">
      @if (retryable()) {
        <button appButton type="button" (click)="retry.emit()">Try again</button>
      }
      <ng-content />
    </div>
    @if (traceId()) {
      <p class="mt-4 font-mono text-xs text-ink-subtle">Ref: {{ traceId() }}</p>
    }
  `,
})
export class ErrorState {
  readonly title = input('Something went wrong');
  readonly error = input<unknown>(null);
  /** Overrides the message derived from `error`. */
  readonly description = input('');
  readonly retryable = input(true);
  readonly retry = output<void>();

  protected readonly message = computed(() => {
    if (this.description()) return this.description();
    const error = this.error();
    if (error instanceof ApiError) return error.message;
    return 'Please check your connection and try again.';
  });

  protected readonly traceId = computed(() => {
    const error = this.error();
    return error instanceof ApiError ? (error.traceId ?? '') : '';
  });
}
