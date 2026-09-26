import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-dvh max-w-lg flex-col items-center justify-center px-4 text-center">
      <p class="font-display text-6xl font-bold text-brand-ink">404</p>
      <h1 class="mt-2 font-display text-2xl font-semibold">Page not found</h1>
      <p class="mt-2 text-ink-muted">The page you are looking for does not exist or has moved.</p>
      <a appButton routerLink="/menu" class="mt-6">Go to the menu</a>
    </main>
  `,
})
export class NotFound {}
