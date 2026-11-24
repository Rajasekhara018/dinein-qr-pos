import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { HqTimezoneService } from 'hq-shared-lib';
import { LoginResponse } from '../models/models';
import { AuthService } from './auth-service';
import { FcmService } from './fcm-service';
import { PrivilegeService } from './privilege-service';

const BASE: LoginResponse = { userId: 'u1', accessToken: 'jwt', role: 'CUSTOMER', username: 'asha' };

describe('AuthService timezone wiring', () => {
  let auth: AuthService;
  let tz: HqTimezoneService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        { provide: FcmService, useValue: { unregisterBeforeLogout: () => undefined } },
        { provide: PrivilegeService, useValue: { restore: () => undefined, clear: () => undefined, load: () => of(undefined) } },
      ],
    });
    tz = TestBed.inject(HqTimezoneService);
    tz.reset();
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    tz.reset();
    localStorage.clear();
  });

  it('applies the user zone from the sign-in response', () => {
    auth.applySession({ ...BASE, timezone: 'Asia/Dubai' });
    expect(tz.effectiveTimezone()).toBe('Asia/Dubai');
  });

  it('leaves the device zone when the fields are absent (older server)', () => {
    const device = tz.effectiveTimezone();
    auth.applySession({ ...BASE });
    expect(tz.effectiveTimezone()).toBe(device);
  });

  it('leaves the device zone when the user zone is null or invalid', () => {
    const device = tz.effectiveTimezone();
    auth.applySession({ ...BASE, timezone: null, organizationTimeZone: null });
    expect(tz.effectiveTimezone()).toBe(device);
    auth.applySession({ ...BASE, timezone: 'Not/AZone' });
    expect(tz.effectiveTimezone()).toBe(device);
  });

  it('does not use the organization zone while the opt-in flag is off', () => {
    const device = tz.effectiveTimezone();
    auth.applySession({ ...BASE, organizationTimeZone: 'Pacific/Auckland' });
    expect(tz.effectiveTimezone()).toBe(device);
  });

  it('hands the organization zone over once the flag is on, and replaces it on an organization switch', () => {
    tz.setUseOrganizationTimezone(true);
    auth.applySession({ ...BASE, organizationTimeZone: 'Pacific/Auckland' });
    expect(tz.effectiveTimezone()).toBe('Pacific/Auckland');
    auth.applySession({ ...BASE, organizationTimeZone: 'America/New_York' });
    expect(tz.effectiveTimezone()).toBe('America/New_York');
    auth.applySession({ ...BASE, organizationTimeZone: null });
    expect(tz.effectiveTimezone()).not.toBe('America/New_York');
  });

  it('keeps the user zone over the organization zone', () => {
    tz.setUseOrganizationTimezone(true);
    auth.applySession({ ...BASE, timezone: 'Asia/Dubai', organizationTimeZone: 'Pacific/Auckland' });
    expect(tz.effectiveTimezone()).toBe('Asia/Dubai');
  });

  it('accepts the organization identity fields (present, null or absent) without changing the session', () => {
    const device = tz.effectiveTimezone();
    auth.applySession({ ...BASE, organizationId: 'org-a', organizationCode: 'ORG-A', organizationName: 'Org A' });
    auth.applySession({ ...BASE, organizationId: null, organizationCode: null, organizationName: null });
    auth.applySession({ ...BASE });
    expect(localStorage.getItem('access_token')).toBe('jwt');
    expect(tz.effectiveTimezone()).toBe(device);
  });

  it('forgets the zones on logout', () => {
    auth.applySession({ ...BASE, timezone: 'Asia/Dubai' });
    auth.logout();
    expect(tz.effectiveTimezone()).not.toBe('Asia/Dubai');
  });
});
