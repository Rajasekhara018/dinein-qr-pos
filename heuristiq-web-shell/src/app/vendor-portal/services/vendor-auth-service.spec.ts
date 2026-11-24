import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { VendorAuthService } from './vendor-auth-service';

describe('VendorAuthService', () => {
  const http = { post: vi.fn((_url: string, _body: unknown): Observable<unknown> => of({})) };
  const router = { navigate: vi.fn() };
  const make = () => new VendorAuthService(http as never, router as never);
  const base = environment.uamApiUrl + '/identity/auth';

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe('restoring a session', () => {
    it('starts signed out with nothing stored', () => {
      const service = make();
      expect(service.currentVendor).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('restores the vendor when both the token and the profile are stored', () => {
      localStorage.setItem('vendor_access_token', 't');
      localStorage.setItem('vendor_data', JSON.stringify({ id: 'v1', name: 'Acme' }));
      const service = make();
      expect(service.currentVendor).toEqual({ id: 'v1', name: 'Acme' });
      expect(service.isAuthenticated()).toBe(true);
    });

    it('ignores a corrupt profile and a profile without a token', () => {
      localStorage.setItem('vendor_access_token', 't');
      localStorage.setItem('vendor_data', '{oops');
      expect(make().currentVendor).toBeNull();
      localStorage.clear();
      localStorage.setItem('vendor_data', JSON.stringify({ id: 'v1' }));
      expect(make().currentVendor).toBeNull();
    });
  });

  describe('register', () => {
    it('creates a VENDOR identity and returns its user id', () => {
      http.post.mockReturnValue(of({ success: true, responseObject: { userId: 'u9' } }));
      let id = '';
      make().register({ name: 'Acme', mobile: '+91999', email: 'a@x.io', city: 'Pune' }).subscribe((v) => (id = v));
      expect(http.post).toHaveBeenCalledWith(base + '/register', {
        requestObject: { name: 'Acme', mobile: '+91999', email: 'a@x.io', city: 'Pune', userType: 'VENDOR' },
      });
      expect(id).toBe('u9');
    });

    it('surfaces the server message when the envelope reports failure', () => {
      http.post.mockReturnValue(of({ success: false, message: 'Email taken' }));
      let error: unknown;
      make().register({ name: 'A', mobile: '1', email: 'e' }).subscribe({ error: (e) => (error = e) });
      expect(error).toEqual({ error: { detail: 'Email taken', message: 'Email taken' } });
    });

    it('falls back to a generic message and rejects an empty response object', () => {
      http.post.mockReturnValue(of({ success: true }));
      let error: { error: { detail: string } } | undefined;
      make().register({ name: 'A', mobile: '1', email: 'e' }).subscribe({ error: (e) => (error = e) });
      expect(error?.error.detail).toBe('Request failed');
    });
  });

  describe('phone sign-in', () => {
    it('asks for an OTP for a vendor number', () => {
      let done = false;
      make().sendPhoneOtp('+91999').subscribe((v) => { expect(v).toBeUndefined(); done = true; });
      expect(http.post).toHaveBeenCalledWith(base + '/phone/send-otp', { requestObject: { phone: '+91999', userType: 'VENDOR' } });
      expect(done).toBe(true);
    });

    it('verifies the OTP, stores the session and publishes the vendor', () => {
      http.post.mockReturnValue(of({ success: true, responseObject: { accessToken: 'acc', token: 'old', userId: 'v1', username: 'acme', email: 'a@x.io', fullName: 'Acme Ltd' } }));
      const service = make();
      const seen: unknown[] = [];
      service.vendor$.subscribe((v) => seen.push(v));
      let session: unknown;
      service.verifyPhoneOtp('+91999', '123456').subscribe((v) => (session = v));
      expect(http.post).toHaveBeenCalledWith(base + '/phone/verify-otp', { requestObject: { phone: '+91999', otp: '123456', userType: 'VENDOR' } });
      expect(session).toEqual({ id: 'v1', name: 'Acme Ltd', email: 'a@x.io', username: 'acme' });
      expect(localStorage.getItem('vendor_access_token')).toBe('acc');
      expect(JSON.parse(localStorage.getItem('vendor_data') ?? '{}').id).toBe('v1');
      expect(service.isAuthenticated()).toBe(true);
      expect(seen.at(-1)).toEqual(session);
    });

    it('uses the legacy token field and the username when there is no full name', () => {
      http.post.mockReturnValue(of({ success: true, responseObject: { token: 'legacy', userId: 'v2', username: 'bob' } }));
      const service = make();
      service.verifyPhoneOtp('1', '1').subscribe();
      expect(localStorage.getItem('vendor_access_token')).toBe('legacy');
      expect(service.currentVendor?.name).toBe('bob');
    });

    it('fails and stores nothing when no access token comes back', () => {
      http.post.mockReturnValue(of({ success: true, responseObject: { userId: 'v3' } }));
      const service = make();
      let error: Error | undefined;
      service.verifyPhoneOtp('1', '1').subscribe({ error: (e) => (error = e) });
      expect(error?.message).toBe('Login response did not include an access token');
      expect(localStorage.getItem('vendor_access_token')).toBeNull();
      expect(service.currentVendor).toBeNull();
    });
  });

  it('logs out: clears storage, empties the session and goes to the vendor login', () => {
    localStorage.setItem('vendor_access_token', 't');
    localStorage.setItem('vendor_data', JSON.stringify({ id: 'v1' }));
    const service = make();
    service.logout();
    expect(localStorage.getItem('vendor_access_token')).toBeNull();
    expect(localStorage.getItem('vendor_data')).toBeNull();
    expect(service.currentVendor).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/vendor/login']);
  });

  it('is not authenticated when the token was removed behind its back', () => {
    localStorage.setItem('vendor_access_token', 't');
    localStorage.setItem('vendor_data', JSON.stringify({ id: 'v1' }));
    const service = make();
    localStorage.removeItem('vendor_access_token');
    expect(service.isAuthenticated()).toBe(false);
  });
});
