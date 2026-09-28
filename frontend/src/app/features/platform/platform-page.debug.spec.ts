import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthStore } from '../../core/auth/auth.store';
import { authInterceptor } from '../../core/http/auth.interceptor';
import { PlatformModule } from './platform-module';
import { PlatformPage } from './platform-page';

describe('PlatformPage onboarding (as a logged-in platform admin)', () => {
  let fixture: ComponentFixture<PlatformPage>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let auth: AuthStore;

  function setLoggedInPlatformAdmin() {
    auth = TestBed.inject(AuthStore);
    (auth as unknown as { _user: { set: (v: unknown) => void } })['_user'].set({
      id: 1, username: 'owner', displayName: 'Owner', role: 'OWNER',
      mustChangePassword: false, platformAdmin: true,
    });
    (auth as unknown as { _accessToken: { set: (v: unknown) => void } })['_accessToken'].set('fake-token');
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlatformModule],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    setLoggedInPlatformAdmin();
    fixture = TestBed.createComponent(PlatformPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('shows the onboarding form immediately (no key prompt) and auto-loads restaurants with the bearer token', () => {
    expect(el.querySelector('#platform-key')).toBeNull();
    const req = http.expectOne('/api/v1/platform/restaurants');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer fake-token');
    expect(req.request.headers.get('X-Platform-Admin-Key')).toBeNull();
    req.flush([]);
  });

  it('submits the onboard form with the bearer token and shows the result', async () => {
    http.expectOne('/api/v1/platform/restaurants').flush([]);

    const name: HTMLInputElement = el.querySelector('#plat-name')!;
    name.value = 'RAJA';
    name.dispatchEvent(new Event('input'));
    const slug: HTMLInputElement = el.querySelector('#plat-slug')!;
    slug.value = 'Test';
    slug.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = el.querySelector('form')!;
    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/platform/restaurants');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer fake-token');
    expect(req.request.body).toEqual({ restaurantName: 'RAJA', slug: 'Test', ownerDisplayName: null });
    req.flush({ restaurantId: 5, restaurantName: 'RAJA', slug: 'test', ownerUsername: 'test.owner', temporaryPassword: 'abc123' });
    http.expectOne('/api/v1/platform/restaurants').flush([{ id: 5, name: 'RAJA', slug: 'test', status: 'ACTIVE', createdAt: new Date().toISOString(), owners: [] }]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.textContent).toContain('RAJA onboarded');
    expect(el.textContent).toContain('test.owner');
  });
});
