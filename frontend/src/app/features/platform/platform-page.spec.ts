import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthStore } from '../../core/auth/auth.store';
import { authInterceptor } from '../../core/http/auth.interceptor';
import { PlatformModule } from './platform-module';
import { PlatformPage } from './platform-page';

describe('PlatformPage', () => {
  let fixture: ComponentFixture<PlatformPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  function signInAsPlatformAdmin() {
    const auth = TestBed.inject(AuthStore);
    (auth as unknown as { _user: { set: (v: unknown) => void } })['_user'].set({
      id: 1,
      username: 'owner',
      displayName: 'Owner',
      role: 'OWNER',
      mustChangePassword: false,
      platformAdmin: true,
      restaurantId: 1,
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
    signInAsPlatformAdmin();
    fixture = TestBed.createComponent(PlatformPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('skips the key prompt and lists restaurants with the caller\'s own bearer token', () => {
    expect(el.querySelector('#platform-key')).toBeNull();
    const req = http.expectOne('/api/v1/platform/restaurants');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer fake-token');
    expect(req.request.headers.get('X-Platform-Admin-Key')).toBeNull();
    req.flush([]);
  });

  it('submits the onboard form on click and shows the returned owner credentials', async () => {
    http.expectOne('/api/v1/platform/restaurants').flush([]);

    const name: HTMLInputElement = el.querySelector('#plat-name')!;
    name.value = 'Pizza Corner';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const submit: HTMLButtonElement = el.querySelector('button[type="submit"]')!;
    submit.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/platform/restaurants');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer fake-token');
    expect(req.request.body).toEqual({
      restaurantName: 'Pizza Corner',
      slug: null,
      address: null,
      phone: null,
      gstin: null,
      fssaiNo: null,
      pricesIncludeGst: false,
      openingTime: null,
      closingTime: null,
      brandColor: null,
      takeawayEnabled: true,
      ownerDisplayName: null,
      ownerUsername: null,
      ownerEmail: null,
      ownerPhone: null,
      ownerPassword: null,
    });
    req.flush({
      restaurantId: 5,
      restaurantName: 'Pizza Corner',
      slug: 'pizza-corner',
      ownerUsername: 'pizza-corner.owner',
      temporaryPassword: 'abc123',
    });
    await fixture.whenStable();
    http.expectOne('/api/v1/platform/restaurants').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.textContent).toContain('Pizza Corner onboarded');
    expect(el.textContent).toContain('pizza-corner.owner');
  });
});
