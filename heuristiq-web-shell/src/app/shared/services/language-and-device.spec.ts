import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { DeviceLocationService } from './device-location-service';
import { DEFAULT_LANGUAGE, LANGUAGE_STORAGE_KEY, LanguageService, SUPPORTED_LANGUAGES } from './language-service';

describe('LanguageService', () => {
  let translate: { use: ReturnType<typeof vi.fn>; getCurrentLang: ReturnType<typeof vi.fn> };
  let svc: LanguageService;
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.clear();
    root.removeAttribute('lang');
    root.removeAttribute('dir');
    translate = { use: vi.fn().mockReturnValue(of({})), getCurrentLang: vi.fn().mockReturnValue('') };
    TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useValue: translate }] });
    svc = TestBed.inject(LanguageService);
  });
  afterEach(() => localStorage.clear());

  it('lists the 7 supported languages with Arabic as the only right-to-left one', () => {
    expect(svc.languages.map((l) => l.code)).toEqual(['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar']);
    expect(SUPPORTED_LANGUAGES.filter((l) => l.dir === 'rtl').map((l) => l.code)).toEqual(['ar']);
  });

  it('reports English when no language is active and the active one otherwise', () => {
    expect(svc.currentLanguage).toBe(DEFAULT_LANGUAGE);
    expect(svc.current.code).toBe('en');
    translate.getCurrentLang.mockReturnValue('ta');
    expect(svc.current.nativeName).toBe('தமிழ்');
    translate.getCurrentLang.mockReturnValue('xx');
    expect(svc.current.code).toBe('en');
  });

  it('init uses the stored language and sets html lang and dir', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'ar');
    await svc.init();
    expect(translate.use).toHaveBeenCalledWith('ar');
    expect(root.getAttribute('lang')).toBe('ar');
    expect(root.getAttribute('dir')).toBe('rtl');
  });

  it('init falls back to English for no stored or an unknown language', async () => {
    await svc.init();
    expect(translate.use).toHaveBeenLastCalledWith('en');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'zz');
    await svc.init();
    expect(translate.use).toHaveBeenLastCalledWith('en');
    expect(root.getAttribute('dir')).toBe('ltr');
  });

  it('init falls back to English, and never rejects, when the language file cannot be loaded', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'hi');
    translate.use.mockImplementation((lang: string) => (lang === 'hi' ? throwError(() => new Error('404')) : of({})));
    await svc.init();
    expect(translate.use).toHaveBeenLastCalledWith('en');
    expect(root.getAttribute('lang')).toBe('en');
    warn.mockRestore();
  });

  it('init survives English failing to load too', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    translate.use.mockReturnValue(throwError(() => new Error('down')));
    await expect(svc.init()).resolves.toBeUndefined();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'hi');
    await expect(svc.init()).resolves.toBeUndefined();
    warn.mockRestore();
  });

  it('useSaved switches to the language saved on the account and remembers it in this browser', () => {
    translate.getCurrentLang.mockReturnValue('en');

    svc.useSaved('ar');

    expect(translate.use).toHaveBeenCalledWith('ar');
    expect(root.getAttribute('dir')).toBe('rtl');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('ar');
  });

  it('useSaved does nothing for no language, an unsupported one, or the one already showing', () => {
    translate.getCurrentLang.mockReturnValue('ta');

    svc.useSaved(null);
    svc.useSaved('');
    svc.useSaved('od');
    svc.useSaved('ta');

    expect(translate.use).not.toHaveBeenCalled();
  });

  it('setLanguage switches, stores the choice and updates the document', () => {
    svc.setLanguage('ml');
    expect(translate.use).toHaveBeenCalledWith('ml');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('ml');
    expect(root.getAttribute('lang')).toBe('ml');
    svc.setLanguage('bogus');
    expect(translate.use).toHaveBeenLastCalledWith('en');
  });

  it('still switches when storage is unavailable', () => {
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied'); });
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied'); });
    svc.setLanguage('te');
    expect(translate.use).toHaveBeenCalledWith('te');
    void svc.init();
    expect(translate.use).toHaveBeenLastCalledWith('en');
    set.mockRestore();
    get.mockRestore();
  });
});

describe('DeviceLocationService', () => {
  let svc: DeviceLocationService;
  let http: HttpTestingController;
  let getCurrentPosition: ReturnType<typeof vi.fn>;
  const URL = `${environment.uamApiUrl}/identity/me/devices/current/location`;
  const nav = navigator as unknown as Record<string, unknown>;

  const grant = () => getCurrentPosition.mockImplementation((ok: (p: unknown) => void) =>
    ok({ coords: { latitude: 12.9, longitude: 77.6, accuracy: 25 } }));

  beforeEach(() => {
    sessionStorage.clear();
    getCurrentPosition = vi.fn();
    Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition }, configurable: true });
    Object.defineProperty(navigator, 'permissions', { value: { query: vi.fn().mockResolvedValue({ state: 'prompt' }) }, configurable: true });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    svc = TestBed.inject(DeviceLocationService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    delete nav['geolocation'];
    delete nav['permissions'];
    sessionStorage.clear();
  });

  it('sends the browser position to UAM once per user per tab session', async () => {
    grant();
    await svc.captureOnce('u1');
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ latitude: 12.9, longitude: 77.6, accuracyMeters: 25 });
    req.flush({});
    await svc.captureOnce('u1');
    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    http.expectNone(URL);
  });

  it('asks again for a different user', async () => {
    grant();
    await svc.captureOnce('u1');
    http.expectOne(URL).flush({});
    await svc.captureOnce('u2');
    http.expectOne(URL).flush({});
  });

  it('does nothing without a user id', async () => {
    grant();
    await svc.captureOnce(null);
    await svc.captureOnce(undefined);
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it('does nothing when the browser has no geolocation', async () => {
    delete nav['geolocation'];
    await svc.captureOnce('u1');
    http.expectNone(URL);
  });

  it('does not prompt when location is already blocked for the site', async () => {
    (navigator.permissions.query as ReturnType<typeof vi.fn>).mockResolvedValue({ state: 'denied' });
    await svc.captureOnce('u1');
    expect(getCurrentPosition).not.toHaveBeenCalled();
  });

  it('still asks when the permission state cannot be read', async () => {
    (navigator.permissions.query as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('unsupported'));
    grant();
    await svc.captureOnce('u1');
    http.expectOne(URL).flush({});
  });

  it('sends nothing when the user refuses or the lookup fails', async () => {
    getCurrentPosition.mockImplementation((_ok: unknown, fail: () => void) => fail());
    await svc.captureOnce('u1');
    http.expectNone(URL);
  });

  it('ignores a failed upload and works when session storage is unavailable', async () => {
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('denied'); });
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied'); });
    grant();
    await svc.captureOnce('u1');
    http.expectOne(URL).flush('x', { status: 500, statusText: 'err' });
    get.mockRestore();
    set.mockRestore();
  });
});
