import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Shown when the QR token is invalid/inactive or there is no guest session. */
@Component({
  selector: 'app-scan-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center px-6 py-10 text-center">
      <div class="flex size-24 items-center justify-center rounded-3xl bg-brand-soft text-brand-ink" aria-hidden="true">
        <svg viewBox="0 0 48 48" class="size-14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
          <rect x="6" y="6" width="13" height="13" rx="2" />
          <rect x="29" y="6" width="13" height="13" rx="2" />
          <rect x="6" y="29" width="13" height="13" rx="2" />
          <path d="M29 29h5v5M42 29v0M29 42h13v-8M37 37h0" />
        </svg>
      </div>
      <h1 class="mt-6 font-display text-2xl font-semibold sm:text-3xl">Please scan the QR code on your table</h1>
      <p class="mt-3 text-ink-muted">
        Open your phone's camera and point it at the QR code on the table to see the menu and order.
        If it still doesn't work, please ask our staff for help.
      </p>
    </main>
  `,
})
export class ScanPage {}
