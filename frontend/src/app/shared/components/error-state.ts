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
  templateUrl: './error-state.html',
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
