import { NgZone } from '@angular/core';

/** Attributes of <html> that change the chart colours: the theme, its light/dark mode and the motion setting. */
export const HQ_CHART_THEME_ATTRIBUTES: readonly string[] = ['data-theme', 'data-theme-mode', 'data-reduced-motion'];

/**
 * Calls `changed` (inside Angular) whenever the page's theme, mode or motion setting changes, so an HTML chart can recolour itself.
 * Returns the function that stops watching; call it from ngOnDestroy.
 */
export function hqWatchChartTheme(zone: NgZone, changed: () => void): () => void {
  if (typeof document === 'undefined' || typeof MutationObserver === 'undefined') {
    return () => undefined;
  }
  let observer: MutationObserver | null = null;
  zone.runOutsideAngular(() => {
    observer = new MutationObserver(() => zone.run(changed));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: [...HQ_CHART_THEME_ATTRIBUTES] });
  });
  return () => observer?.disconnect();
}

/** True when the user asked for less motion (the app's own setting or the system's). */
export function hqChartReducedMotion(): boolean {
  if (typeof document === 'undefined') {
    return false;
  }
  return document.documentElement.getAttribute('data-reduced-motion') === 'true'
    || !!(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
}
