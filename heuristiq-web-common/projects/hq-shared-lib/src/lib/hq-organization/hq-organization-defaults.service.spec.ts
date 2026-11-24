import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HQ_CONFIG } from '../hq-common.config';
import { hqReadSetting } from '../hq-ui/hq-app-setting';
import { hqTenantIdFromToken } from './hq-organization-defaults.helpers';
import { HqOrganizationDefaultsService, hqOrganizationCountry, hqOrganizationCurrency } from './hq-organization-defaults.service';

function tokenFor(claims: Record<string, unknown>): string {
  const payload = btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `h.${payload}.s`;
}

const URL = 'https://gw/api/v1/uam/identity/me/organizations';

describe('HqOrganizationDefaultsService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('access_token', tokenFor({ tenantId: 'org-b' }));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: HQ_CONFIG, useValue: { gatewayUrl: 'https://gw/api', adminApiUrl: 'https://gw/api/v1' } }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('access_token');
  });

  it("gives nothing until the organizations load, then the token organization's currency and country", () => {
    const service = TestBed.inject(HqOrganizationDefaultsService);
    expect(service.currency()).toBeNull();
    http.expectOne(URL).flush({ success: true, responseObject: [
      { tenantId: 'org-a', currency: 'INR', country: 'IN' },
      { tenantId: 'org-b', currency: 'AED', country: 'AE' },
    ] });
    expect(service.currency()).toBe('AED');
    expect(service.country()).toBe('AE');
  });

  it('asks only once, and a failed request leaves the app defaults in place', () => {
    const service = TestBed.inject(HqOrganizationDefaultsService);
    service.currency();
    service.country();
    http.expectOne(URL).flush('down', { status: 503, statusText: 'Unavailable' });
    expect(service.currency()).toBeNull();
    http.expectNone(URL);
  });

  it('does not call UAM without a signed-in token', () => {
    localStorage.removeItem('access_token');
    const service = TestBed.inject(HqOrganizationDefaultsService);
    expect(service.country()).toBeNull();
    http.expectNone(URL);
  });

  it('feeds the amount and phone defaults, falling back to the app setting', () => {
    const currency = TestBed.runInInjectionContext(hqOrganizationCurrency('INR'));
    const country = TestBed.runInInjectionContext(hqOrganizationCountry('IN'));
    expect(hqReadSetting(currency, 'X')).toBe('INR');
    http.expectOne(URL).flush({ success: true, responseObject: [{ tenantId: 'org-b', currency: 'USD', country: 'US' }] });
    expect(hqReadSetting(currency, 'X')).toBe('USD');
    expect(hqReadSetting(country, 'X')).toBe('US');
  });

  it('reads the organization id from a token, and nothing from a broken one', () => {
    expect(hqTenantIdFromToken(tokenFor({ tenantId: 't1' }))).toBe('t1');
    expect(hqTenantIdFromToken('not-a-jwt')).toBeNull();
    expect(hqTenantIdFromToken(null)).toBeNull();
    expect(hqTenantIdFromToken(tokenFor({ sub: 'u1' }))).toBeNull();
  });
});
