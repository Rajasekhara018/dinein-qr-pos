import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  HqStaleBundleErrorHandler,
  STALE_BUNDLE_RELOAD_COOLDOWN_MS,
  STALE_BUNDLE_RELOAD_KEY,
  isStaleBundleError,
  provideHqStaleBundleRecovery,
  recoverFromStaleBundle,
} from './hq-stale-bundle-recovery';

const CHUNK_ERROR = 'Failed to fetch dynamically imported module: https://host/chunk-ABC.js';

describe('isStaleBundleError', () => {
  it.each([
    'Failed to fetch dynamically imported module: https://host/chunk-1.js',
    'error loading dynamically imported module',
    'Importing a module script failed.',
    'Loading chunk 42 failed.',
    'Loading CSS chunk main-x failed',
    'ChunkLoadError: whatever',
    '404 https://host/chunk-XXXX.js',
    'TypeError: 410 https://host/remoteEntry.mjs',
  ])('recognises "%s"', (message) => {
    expect(isStaleBundleError(message)).toBe(true);
  });

  it('recognises Error objects by name and message', () => {
    expect(isStaleBundleError(new TypeError('Failed to fetch dynamically imported module: x'))).toBe(true);
    const named = new Error('boom');
    named.name = 'ChunkLoadError';
    expect(isStaleBundleError(named)).toBe(true);
  });

  it('looks inside Angular-wrapped rejections and browser rejection events', () => {
    expect(isStaleBundleError({ rejection: new Error(CHUNK_ERROR) })).toBe(true);
    expect(isStaleBundleError({ reason: CHUNK_ERROR })).toBe(true);
    expect(isStaleBundleError({ message: CHUNK_ERROR })).toBe(true);
  });

  it('is false for ordinary errors and empty values', () => {
    expect(isStaleBundleError(null)).toBe(false);
    expect(isStaleBundleError(undefined)).toBe(false);
    expect(isStaleBundleError('')).toBe(false);
    expect(isStaleBundleError(new Error('Cannot read properties of undefined'))).toBe(false);
    expect(isStaleBundleError('500 https://host/chunk.js')).toBe(false);
    expect(isStaleBundleError({ message: 42 })).toBe(false);
    expect(isStaleBundleError({ rejection: 'Http failure' })).toBe(false);
    expect(isStaleBundleError({})).toBe(false);
  });
});

describe('recoverFromStaleBundle', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('does nothing for an error that is not a stale bundle', () => {
    const reload = vi.fn();
    expect(recoverFromStaleBundle(new Error('other'), reload, () => 1000)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY)).toBeNull();
  });

  it('reloads once and remembers when', () => {
    const reload = vi.fn();
    expect(recoverFromStaleBundle(CHUNK_ERROR, reload, () => 1_000_000)).toBe(true);
    expect(reload).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY)).toBe('1000000');
  });

  it('does not reload again within the cooldown, so a broken deployment cannot loop', () => {
    const reload = vi.fn();
    sessionStorage.setItem(STALE_BUNDLE_RELOAD_KEY, '1000000');
    expect(recoverFromStaleBundle(CHUNK_ERROR, reload, () => 1_000_000 + STALE_BUNDLE_RELOAD_COOLDOWN_MS - 1)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY)).toBe('1000000');
  });

  it('reloads again once the cooldown has passed', () => {
    const reload = vi.fn();
    sessionStorage.setItem(STALE_BUNDLE_RELOAD_KEY, '1000000');
    const later = 1_000_000 + STALE_BUNDLE_RELOAD_COOLDOWN_MS;
    expect(recoverFromStaleBundle(CHUNK_ERROR, reload, () => later)).toBe(true);
    expect(reload).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY)).toBe(String(later));
  });

  it('does not reload when sessionStorage is unavailable', () => {
    const reload = vi.fn();
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(recoverFromStaleBundle(CHUNK_ERROR, reload, () => 1)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
  });

  it('uses the current time when no clock is given', () => {
    const before = Date.now();
    expect(recoverFromStaleBundle(CHUNK_ERROR, vi.fn())).toBe(true);
    const saved = Number(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY));
    expect(saved).toBeGreaterThanOrEqual(before);
    expect(saved).toBeLessThanOrEqual(Date.now());
  });
});

describe('HqStaleBundleErrorHandler', () => {
  let handler: HqStaleBundleErrorHandler;

  beforeEach(() => {
    // A recent reload puts every recovery attempt inside the cooldown, so no test triggers a real page reload.
    sessionStorage.setItem(STALE_BUNDLE_RELOAD_KEY, String(Date.now()));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: provideHqStaleBundleRecovery() });
    handler = TestBed.inject(ErrorHandler) as HqStaleBundleErrorHandler;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    document.querySelectorAll('script[data-test-stale]').forEach((s) => s.remove());
  });

  it('is provided as the app ErrorHandler', () => {
    expect(handler).toBeInstanceOf(HqStaleBundleErrorHandler);
    expect(provideHqStaleBundleRecovery()).toEqual([{ provide: ErrorHandler, useClass: HqStaleBundleErrorHandler }]);
  });

  it('reports ordinary errors through the default handler', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const error = new Error('ordinary');
    handler.handleError(error);
    expect(consoleError).toHaveBeenCalled();
    expect(consoleError.mock.calls.flat()).toContain(error);
  });

  it('reports a stale-bundle error normally when a reload already happened within the cooldown', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    handler.handleError(new Error(CHUNK_ERROR));
    expect(consoleError).toHaveBeenCalled();
  });

  it('swallows a stale-bundle error it recovers from (reload started)', () => {
    sessionStorage.removeItem(STALE_BUNDLE_RELOAD_KEY);
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const before = Date.now();
    // jsdom does not navigate; location.reload() is a no-op here, the handler's decision is what is asserted.
    handler.handleError(new Error(CHUNK_ERROR));
    expect(Number(sessionStorage.getItem(STALE_BUNDLE_RELOAD_KEY))).toBeGreaterThanOrEqual(before);
    expect(consoleError.mock.calls.flat()).not.toContainEqual(expect.objectContaining({ message: CHUNK_ERROR }));
  });

  it('treats a <script> that fails to load as a stale bundle', () => {
    // The cooldown set in beforeEach means the attempt is checked but no reload happens.
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    const script = document.createElement('script');
    script.setAttribute('data-test-stale', '');
    script.src = 'https://host/chunk-OLD.js';
    document.body.appendChild(script);
    getItem.mockClear();
    script.dispatchEvent(new Event('error'));
    expect(getItem).toHaveBeenCalledWith(STALE_BUNDLE_RELOAD_KEY);
  });

  it('ignores resource errors that are not scripts, and scripts without a src', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    const img = document.createElement('img');
    img.setAttribute('data-test-stale', '');
    document.body.appendChild(img);
    const inline = document.createElement('script');
    inline.setAttribute('data-test-stale', '');
    document.body.appendChild(inline);
    getItem.mockClear();
    img.dispatchEvent(new Event('error'));
    inline.dispatchEvent(new Event('error'));
    window.dispatchEvent(new Event('error'));
    img.remove();
    expect(getItem).not.toHaveBeenCalledWith(STALE_BUNDLE_RELOAD_KEY);
  });

  it('checks unhandled promise rejections', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    getItem.mockClear();
    window.dispatchEvent(Object.assign(new Event('unhandledrejection'), { reason: new Error(CHUNK_ERROR) }));
    expect(getItem).toHaveBeenCalledWith(STALE_BUNDLE_RELOAD_KEY);

    getItem.mockClear();
    window.dispatchEvent(Object.assign(new Event('unhandledrejection'), { reason: new Error('other') }));
    expect(getItem).not.toHaveBeenCalled();
  });
});
