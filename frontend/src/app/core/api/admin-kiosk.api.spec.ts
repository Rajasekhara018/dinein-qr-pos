import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AdminKioskApi } from './admin.api';
import { UpdateKioskBrandingRequest } from './models';

describe('AdminKioskApi', () => {
  let api: AdminKioskApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AdminKioskApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists kiosk devices', () => {
    let result: unknown;
    api.devices().subscribe((r) => (result = r));
    const req = http.expectOne('/api/v1/admin/kiosk-devices');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Door', status: 'ACTIVE', createdAt: '2026-09-30T10:00:00Z' }]);
    expect(result).toEqual([
      { id: 1, name: 'Door', status: 'ACTIVE', createdAt: '2026-09-30T10:00:00Z' },
    ]);
  });

  it('creates a device with just a name and returns the one-time code', () => {
    let code = '';
    api.createDevice({ name: 'Door' }).subscribe((r) => (code = r.pairingCode));
    const req = http.expectOne('/api/v1/admin/kiosk-devices');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Door' });
    req.flush({
      id: 1,
      name: 'Door',
      pairingCode: '123456',
      pairingExpiresAt: '2026-09-30T10:10:00Z',
    });
    expect(code).toBe('123456');
  });

  it('requests a new pairing code for a device', () => {
    api.newPairingCode(5).subscribe();
    const req = http.expectOne('/api/v1/admin/kiosk-devices/5/pairing-code');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 5,
      name: 'Door',
      pairingCode: '654321',
      pairingExpiresAt: '2026-09-30T10:10:00Z',
    });
  });

  it('revokes a device', () => {
    let done = false;
    api.revokeDevice(5).subscribe(() => (done = true));
    const req = http.expectOne('/api/v1/admin/kiosk-devices/5/revoke');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });

  it('reads and replaces the branding', () => {
    api.branding().subscribe();
    const get = http.expectOne('/api/v1/admin/kiosk-branding');
    expect(get.request.method).toBe('GET');
    get.flush({ restaurantName: 'Cafe', kioskEnabled: true });

    const body: UpdateKioskBrandingRequest = {
      kioskEnabled: false,
      primaryColor: '#112233',
      secondaryColor: null,
      headline: null,
      subtext: null,
      startButtonLabel: null,
      idleTimeoutSeconds: 60,
      logoImageId: 3,
      backgroundImageId: null,
    };
    api.updateBranding(body).subscribe();
    const put = http.expectOne('/api/v1/admin/kiosk-branding');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual(body);
    put.flush({ restaurantName: 'Cafe', kioskEnabled: false });
  });
});
