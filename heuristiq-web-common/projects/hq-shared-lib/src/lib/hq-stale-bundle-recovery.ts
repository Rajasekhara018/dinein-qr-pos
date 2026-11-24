import { ErrorHandler, Injectable, Provider } from '@angular/core';

/**
 * Recovers an app that was left open across a deployment.
 *
 * Every build gives each JavaScript chunk a new content hash and the new image no longer has the old files. A browser tab
 * still running the previous build then asks for a chunk that is gone, gets a 404, and the screen goes blank. The cure is to
 * load the new build: reloading fetches a fresh index.html (the servers send it with no-cache), which names the new chunks.
 *
 * This turns that failure into a single automatic reload. A reload is attempted at most once per
 * {@link STALE_BUNDLE_RELOAD_COOLDOWN_MS}, so a chunk that is really missing (a broken deployment) cannot loop; after that the
 * error is reported as usual.
 *
 * Wire it in once per app: {@code providers: [provideHqStaleBundleRecovery()]}.
 */

/** Messages the browsers and the Angular / native-federation loaders produce when a chunk cannot be fetched. */
const STALE_BUNDLE_PATTERNS: readonly RegExp[] = [
  /Failed to fetch dynamically imported module/i, // Chrome, Edge
  /error loading dynamically imported module/i, // Firefox
  /Importing a module script failed/i, // Safari
  /Loading (CSS )?chunk [\w-]+ failed/i, // webpack-style loaders
  /ChunkLoadError/i,
  /^(TypeError: ?)?(404|410)\s+\S*\.m?js\b/i, // native federation: "404 https://host/chunk-XXXX.js"
];

/** Key in sessionStorage holding when the last automatic reload happened. */
export const STALE_BUNDLE_RELOAD_KEY = 'rou.staleBundleReloadAt';
/** A second automatic reload within this time is not attempted. */
export const STALE_BUNDLE_RELOAD_COOLDOWN_MS = 60_000;

/** True when the error (or a rejection reason) means "a chunk of the running build no longer exists on the server". */
export function isStaleBundleError(error: unknown): boolean {
  const text = describe(error);
  return !!text && STALE_BUNDLE_PATTERNS.some((pattern) => pattern.test(text));
}

/**
 * Reloads the page once when the error is a stale-bundle error.
 * @returns true when it was one (and a reload was started, or one just happened), so the caller need not report it again
 */
export function recoverFromStaleBundle(
  error: unknown,
  reload: () => void = () => location.reload(),
  now: () => number = () => Date.now(),
): boolean {
  if (!isStaleBundleError(error)) {
    return false;
  }
  try {
    const last = Number(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY) ?? 0);
    if (last && now() - last < STALE_BUNDLE_RELOAD_COOLDOWN_MS) {
      return false; // already reloaded a moment ago and it still fails: a real problem, let it be reported
    }
    sessionStorage.setItem(STALE_BUNDLE_RELOAD_KEY, String(now()));
  } catch {
    // sessionStorage unavailable (private mode, blocked): reloading once per error could loop, so do not reload.
    return false;
  }
  reload();
  return true;
}

function describe(error: unknown): string {
  if (error == null) return '';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return `${error.name}: ${error.message}`;
  const candidate = error as { message?: unknown; reason?: unknown; rejection?: unknown };
  // Angular wraps a rejected promise as { rejection } and the browser delivers { reason }; look inside both.
  return describe(candidate.rejection ?? candidate.reason) || (typeof candidate.message === 'string' ? candidate.message : '');
}

/** Angular's default error handler, except that a stale-bundle error reloads the page instead of leaving it blank. */
@Injectable()
export class HqStaleBundleErrorHandler extends ErrorHandler {
  constructor() {
    super();
    if (typeof window !== 'undefined') {
      // Errors that never reach Angular's handler: a script tag that fails to load, and promises nobody caught.
      window.addEventListener(
        'error',
        (event) => {
          const target = event.target as { tagName?: string; src?: string } | null;
          if (target?.tagName === 'SCRIPT' && target.src) {
            // A failed <script> is a resource error (no message); describe it the way the loaders do.
            recoverFromStaleBundle(`Failed to fetch dynamically imported module: ${target.src}`);
          }
        },
        true,
      );
      window.addEventListener('unhandledrejection', (event) => {
        recoverFromStaleBundle(event.reason);
      });
    }
  }

  override handleError(error: unknown): void {
    if (recoverFromStaleBundle(error)) {
      return;
    }
    super.handleError(error);
  }
}

/** Providers that make the app reload itself, once, when it runs into a chunk of an older build that is gone. */
export function provideHqStaleBundleRecovery(): Provider[] {
  return [{ provide: ErrorHandler, useClass: HqStaleBundleErrorHandler }];
}
