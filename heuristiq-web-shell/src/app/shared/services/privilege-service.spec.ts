import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HQ_PERMISSION_PRIVILEGES as PERMISSION_PRIVILEGES, HQ_PRIVILEGES_STORAGE_KEY as PRIVILEGES_STORAGE_KEY } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { PrivilegeService } from './privilege-service';
import { MY_PRIVILEGES_PATH } from './privilege.constants';

const URL = `${environment.uamApiUrl}${MY_PRIVILEGES_PATH}`;

describe('PrivilegeService', () => {
  let service: PrivilegeService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(PRIVILEGES_STORAGE_KEY);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PrivilegeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => localStorage.removeItem(PRIVILEGES_STORAGE_KEY));

  it('starts pending with nothing granted', () => {
    expect(service.isPending).toBe(true);
    expect(service.isLoaded).toBe(false);
    expect(service.hasAny(['ADMIN_RIDE_LIST'])).toBe(false);
  });

  it('loads the privileges from UAM and saves them for AdminWeb', () => {
    service.load().subscribe();
    http.expectOne(URL).flush({
      success: true,
      responseObject: { roles: ['MANAGER'], permissions: ['ADMIN_RIDE_LIST'], privilegeIds: [1] },
    });

    expect(service.isLoaded).toBe(true);
    expect(service.hasAny(['ADMIN_RIDE_LIST', 'OTHER'])).toBe(true);
    expect(service.hasAny(['OTHER'])).toBe(false);
    expect(JSON.parse(localStorage.getItem(PRIVILEGES_STORAGE_KEY) as string).permissions).toEqual(['ADMIN_RIDE_LIST']);
  });

  it('reports failed (so callers fall back to role hints) when UAM cannot answer', () => {
    service.load().subscribe();
    http.expectOne(URL).flush('down', { status: 503, statusText: 'Service Unavailable' });

    expect(service.status).toBe('failed');
    expect(service.isLoaded).toBe(false);
    expect(service.isPending).toBe(false);
  });

  it('keeps the earlier snapshot when a refresh fails', () => {
    localStorage.setItem(PRIVILEGES_STORAGE_KEY, JSON.stringify({ roles: [], permissions: ['USER_LIST'], privilegeIds: [] }));
    service.restore();
    service.load().subscribe();
    http.expectOne(URL).flush('down', { status: 500, statusText: 'Server Error' });

    expect(service.isLoaded).toBe(true);
    expect(service.hasAny(['USER_LIST'])).toBe(true);
  });

  it('restores a saved snapshot at start-up', () => {
    localStorage.setItem(PRIVILEGES_STORAGE_KEY, JSON.stringify({ roles: [], permissions: ['ROLE_LIST'], privilegeIds: [] }));

    service.restore();

    expect(service.isLoaded).toBe(true);
    expect(service.hasAny(['ROLE_LIST'])).toBe(true);
  });

  it('ignores a corrupt saved snapshot', () => {
    localStorage.setItem(PRIVILEGES_STORAGE_KEY, '{not json');

    service.restore();

    expect(service.isLoaded).toBe(false);
  });

  it('forgets everything on clear', () => {
    service.load().subscribe();
    http.expectOne(URL).flush({ success: true, responseObject: { roles: [], permissions: ['ROLE_LIST'], privilegeIds: [] } });

    service.clear();

    expect(service.isPending).toBe(true);
    expect(service.hasAny(['ROLE_LIST'])).toBe(false);
    expect(localStorage.getItem(PRIVILEGES_STORAGE_KEY)).toBeNull();
  });
});

describe('PERMISSION_PRIVILEGES', () => {
  it('maps every permission key to at least one privilege code', () => {
    for (const [key, codes] of Object.entries(PERMISSION_PRIVILEGES)) {
      expect(codes.length, key).toBeGreaterThan(0);
      for (const code of codes) {
        expect(code, key).toMatch(/^[A-Z][A-Z0-9_]+$/);
      }
    }
  });

  it('lets any single train screen privilege open the train area', () => {
    for (const key of Object.keys(PERMISSION_PRIVILEGES).filter((k) => k.startsWith('view_train_') && k !== 'view_train_ops')) {
      for (const code of PERMISSION_PRIVILEGES[key]) {
        expect(PERMISSION_PRIVILEGES['view_train_ops'], key).toContain(code);
      }
    }
  });
});
