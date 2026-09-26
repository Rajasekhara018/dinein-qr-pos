import { BreakpointObserver } from '@angular/cdk/layout';
import { computed, inject, Injectable, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/** Tailwind breakpoints (keep in sync with the default theme). */
export const BREAKPOINTS = {
  sm: '(min-width: 640px)',
  md: '(min-width: 768px)',
  lg: '(min-width: 1024px)',
  xl: '(min-width: 1280px)',
  '2xl': '(min-width: 1536px)',
} as const;

/**
 * Layout signals for the few cases Tailwind classes cannot express (e.g. dialog ↔ bottom sheet).
 * Prefer responsive Tailwind classes everywhere else.
 */
@Injectable({ providedIn: 'root' })
export class BreakpointService {
  private readonly observer = inject(BreakpointObserver);

  private readonly md = this.match(BREAKPOINTS.md);
  private readonly lg = this.match(BREAKPOINTS.lg);

  /** < 768px — phones (portrait and most landscape). */
  readonly isHandset: Signal<boolean> = computed(() => !this.md());
  /** 768px – 1023px. */
  readonly isTablet: Signal<boolean> = computed(() => this.md() && !this.lg());
  /** ≥ 1024px. */
  readonly isDesktop: Signal<boolean> = this.lg;

  readonly prefersReducedMotion = this.match('(prefers-reduced-motion: reduce)');
  readonly isLandscape = this.match('(orientation: landscape)');
  readonly coarsePointer = this.match('(pointer: coarse)');

  /** A signal for any media query. */
  match(query: string): Signal<boolean> {
    return toSignal(this.observer.observe(query).pipe(map((state) => state.matches)), {
      initialValue: this.observer.isMatched(query),
    });
  }
}
