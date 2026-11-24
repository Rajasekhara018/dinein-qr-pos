import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { BrandingService } from './branding-service';
import { FcmService } from './fcm-service';
import { FCM_REGISTRATION_KEY, FCM_REREGISTER_AFTER_MS, FCM_TOKEN_KEY } from './fcm.constants';
import { GoogleAuthService } from './google-auth-service';

const fb = vi.hoisted(() => ({
  initializeApp: vi.fn(),
  getApps: vi.fn(() => [] as unknown[]),
  getMessaging: vi.fn(() => ({ m: 1 })),
  getToken: vi.fn(),
  onMessage: vi.fn(),
  deleteToken: vi.fn(),
  getAuth: vi.fn(() => ({ a: 1 })),
  signInWithPopup: vi.fn(),
}));

vi.mock('firebase/app', () => ({ initializeApp: fb.initializeApp, getApps: fb.getApps }));
vi.mock('firebase/messaging', () => ({ getMessaging: fb.getMessaging, getToken: fb.getToken, onMessage: fb.onMessage, deleteToken: fb.deleteToken }));
vi.mock('firebase/auth', () => ({
  getAuth: fb.getAuth,
  GoogleAuthProvider: class { },
  signInWithPopup: fb.signInWithPopup,
}));

describe('GoogleAuthService', () => {
  beforeEach(() => {
    fb.initializeApp.mockClear();
    fb.getApps.mockReturnValue([]);
  });

  it('initialises Firebase once, opens the Google popup and returns the ID token', async () => {
    fb.signInWithPopup.mockResolvedValue({ user: { getIdToken: () => Promise.resolve('id-token') } });
    const token = await TestBed.inject(GoogleAuthService).signIn();
    expect(token).toBe('id-token');
    expect(fb.initializeApp).toHaveBeenCalledWith(environment.firebase);
  });

  it('does not initialise a second Firebase app', async () => {
    fb.getApps.mockReturnValue([{}]);
    fb.signInWithPopup.mockResolvedValue({ user: { getIdToken: () => Promise.resolve('t') } });
    await TestBed.inject(GoogleAuthService).signIn();
    expect(fb.initializeApp).not.toHaveBeenCalled();
  });

  it('lets a cancelled popup reject to the caller', async () => {
    fb.signInWithPopup.mockRejectedValue(new Error('popup closed'));
    await expect(TestBed.inject(GoogleAuthService).signIn()).rejects.toThrow('popup closed');
  });
});

describe('FcmService', () => {
  let svc: FcmService;
  let http: HttpTestingController;
  let showNotification: ReturnType<typeof vi.fn>;
  const URL = `${environment.apiUrl}/v1/nms/devices/fcm-token`;
  const env = environment as unknown as { firebase?: { apiKey?: string; vapidKey?: string } };
  const originalFirebase = env.firebase;

  const stubBrowser = (permission: string) => {
    vi.stubGlobal('Notification', Object.assign(vi.fn(), { requestPermission: vi.fn().mockResolvedValue(permission), permission }));
    showNotification = vi.fn();
    Object.defineProperty(navigator, 'serviceWorker', {
      value: { register: vi.fn().mockResolvedValue({ showNotification }), ready: Promise.resolve({}) },
      configurable: true,
    });
  };

  beforeEach(() => {
    localStorage.clear();
    fb.initializeApp.mockClear();
    fb.getToken.mockReset().mockResolvedValue('tok-1');
    fb.onMessage.mockReset().mockReturnValue(() => undefined);
    fb.deleteToken.mockReset().mockResolvedValue(true);
    fb.getApps.mockReturnValue([]);
    env.firebase = { apiKey: 'key', vapidKey: 'vapid' };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BrandingService, useValue: { branding: { appName: 'Acme' } } }],
    });
    svc = TestBed.inject(FcmService);
    http = TestBed.inject(HttpTestingController);
    stubBrowser('granted');
  });

  afterEach(() => {
    env.firebase = originalFirebase;
    vi.unstubAllGlobals();
    delete (navigator as unknown as Record<string, unknown>)['serviceWorker'];
    localStorage.clear();
  });

  /** setup() awaits several dynamic imports and promises before it reaches the HTTP call. */
  const flushPending = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); await new Promise((r) => setTimeout(r, 0)); };

  it('skips setup when the environment has no Firebase config', async () => {
    env.firebase = undefined;
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    await svc.setup();
    expect(fb.getToken).not.toHaveBeenCalled();
    warn.mockRestore();
  });

  it('skips setup when the browser has no notifications or service workers', async () => {
    vi.unstubAllGlobals();
    delete (window as unknown as Record<string, unknown>)['Notification'];
    await svc.setup();
    expect(fb.getToken).not.toHaveBeenCalled();
  });

  it('does not register a token when permission is not granted', async () => {
    stubBrowser('denied');
    await svc.setup();
    expect(fb.getToken).not.toHaveBeenCalled();
    http.expectNone(URL);
  });

  it('registers a new token with device details and remembers it', async () => {
    localStorage.setItem('user_data', JSON.stringify({ id: 'u1' }));
    const done = svc.setup();
    await flushPending();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ token: 'tok-1', platform: 'web' });
    expect(Object.keys(req.request.body)).toEqual(expect.arrayContaining(['app', 'deviceModel', 'osVersion', 'locale', 'timezone']));
    req.flush({});
    await done;
    expect(localStorage.getItem(FCM_TOKEN_KEY)).toBe('tok-1');
    expect(JSON.parse(localStorage.getItem(FCM_REGISTRATION_KEY)!)).toMatchObject({ userId: 'u1' });
    expect(fb.onMessage).toHaveBeenCalled();
  });

  it('shares one in-flight setup between overlapping callers', async () => {
    const a = svc.setup();
    const b = svc.setup();
    expect(a).toBe(b);
    await flushPending();
    http.expectOne(URL).flush({});
    await a;
  });

  it('does not re-register an unchanged token for the same user within the refresh window', async () => {
    localStorage.setItem('user_data', JSON.stringify({ id: 'u1' }));
    localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
    localStorage.setItem(FCM_REGISTRATION_KEY, JSON.stringify({ userId: 'u1', at: Date.now() }));
    await svc.setup();
    http.expectNone(URL);
  });

  it('re-registers for a different user or after the refresh window, and on corrupt bookkeeping', async () => {
    localStorage.setItem('user_data', JSON.stringify({ id: 'u2' }));
    localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
    localStorage.setItem(FCM_REGISTRATION_KEY, JSON.stringify({ userId: 'u1', at: Date.now() }));
    let done = svc.setup();
    await flushPending();
    http.expectOne(URL).flush({});
    await done;

    localStorage.setItem(FCM_REGISTRATION_KEY, JSON.stringify({ userId: 'u2', at: Date.now() - FCM_REREGISTER_AFTER_MS - 1000 }));
    done = svc.setup();
    await flushPending();
    http.expectOne(URL).flush({});
    await done;

    localStorage.setItem(FCM_REGISTRATION_KEY, '{bad');
    done = svc.setup();
    await flushPending();
    http.expectOne(URL).flush({});
    await done;
  });

  it('does nothing when Firebase returns no token, and swallows setup errors', async () => {
    fb.getToken.mockResolvedValue('');
    await svc.setup();
    http.expectNone(URL);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    fb.getToken.mockRejectedValue(new Error('blocked'));
    await svc.setup();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it('shows a foreground push as a notification with the branding name as fallback title', async () => {
    const done = svc.setup();
    await flushPending();
    http.expectOne(URL).flush({});
    await done;
    const handler = fb.onMessage.mock.calls[0][1] as (p: unknown) => void;
    handler({ notification: { title: 'Hi', body: 'There' }, data: { url: '/x' } });
    expect(showNotification).toHaveBeenCalledWith('Hi', { body: 'There', icon: '/favicon.ico', data: { url: '/x' } });
    handler({});
    expect(showNotification).toHaveBeenLastCalledWith('Acme', { body: '', icon: '/favicon.ico', data: {} });
  });

  it('replaces the previous foreground listener instead of stacking them', async () => {
    const off = vi.fn();
    fb.onMessage.mockReturnValue(off);
    localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
    localStorage.setItem(FCM_REGISTRATION_KEY, JSON.stringify({ userId: null, at: Date.now() }));
    await svc.setup();
    await svc.setup();
    expect(off).toHaveBeenCalledTimes(1);
  });

  describe('unregisterBeforeLogout', () => {
    it('does nothing when no token was registered', () => {
      svc.unregisterBeforeLogout();
      http.expectNone(URL);
    });

    it('sends the delete synchronously, with the token in the body, and forgets it locally', async () => {
      localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
      localStorage.setItem(FCM_REGISTRATION_KEY, '{}');
      svc.unregisterBeforeLogout();
      const req = http.expectOne(URL);
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({ token: 'tok-1' });
      req.flush('x', { status: 500, statusText: 'err' });
      expect(localStorage.getItem(FCM_TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(FCM_REGISTRATION_KEY)).toBeNull();
      await flushPending();
      expect(fb.deleteToken).toHaveBeenCalled();
    });
  });

  describe('cleanup', () => {
    it('stops the listener and returns when there is no token', async () => {
      const off = vi.fn();
      fb.onMessage.mockReturnValue(off);
      const done = svc.setup();
      await flushPending();
      http.expectOne(URL).flush({});
      await done;
      localStorage.removeItem(FCM_TOKEN_KEY);
      await svc.cleanup();
      expect(off).toHaveBeenCalled();
      http.expectNone(URL);
    });

    it('unregisters with the backend, forgets the token and deletes it from Firebase', async () => {
      localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
      const done = svc.cleanup();
      await flushPending();
      const req = http.expectOne(URL);
      expect(req.request.method).toBe('DELETE');
      req.flush({});
      await done;
      expect(localStorage.getItem(FCM_TOKEN_KEY)).toBeNull();
      expect(fb.deleteToken).toHaveBeenCalled();
    });

    it('forgets the token locally even when the backend call fails', async () => {
      localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
      const done = svc.cleanup();
      await flushPending();
      http.expectOne(URL).flush('x', { status: 500, statusText: 'err' });
      await done;
      expect(localStorage.getItem(FCM_TOKEN_KEY)).toBeNull();
    });

    it('does not touch Firebase when the environment has none', async () => {
      env.firebase = undefined;
      localStorage.setItem(FCM_TOKEN_KEY, 'tok-1');
      const done = svc.cleanup();
      await flushPending();
      http.expectOne(URL).flush({});
      await done;
      expect(fb.deleteToken).not.toHaveBeenCalled();
    });
  });
});
