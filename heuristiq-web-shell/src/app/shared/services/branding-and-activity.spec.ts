import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { HqDocumentService } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { BrandingService } from './branding-service';
import { MyActivityService } from './my-activity.service';
import { MY_ACTIVITY_PATH } from './my-activity.constants';
import { ProfilePhotoService } from './profile-photo-service';
import { PROFILE_PHOTO_CATEGORY } from './profile-photo.constants';
import { OrganizationContextService } from './organization-context-service';
import { AuthService } from './auth-service';
import { UamService } from './uam-service';

describe('BrandingService', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('starts with the fallback branding', () => {
    expect(TestBed.inject(BrandingService).branding.appName).toBe('RailCab');
  });

  it('merges the fetched JSON over the fallback and asks the server to revalidate', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ appName: 'Acme' }) });
    vi.stubGlobal('fetch', fetchMock);
    const svc = TestBed.inject(BrandingService);
    await svc.load();
    expect(fetchMock).toHaveBeenCalledWith(environment.configFile, { headers: { 'Cache-Control': 'no-cache' } });
    expect(svc.branding.appName).toBe('Acme');
    expect(svc.branding.tagline).toBe('Your ride, on track.');
  });

  it('keeps the fallback when the file cannot be fetched or is not an object', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const svc = TestBed.inject(BrandingService);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }));
    await svc.load();
    expect(svc.branding.appName).toBe('RailCab');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(null) }));
    await svc.load();
    expect(svc.branding.appName).toBe('RailCab');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await svc.load();
    expect(svc.branding.poweredBy).toBe('Powered by Heuristiq Gateway Solutions');
    expect(warn).toHaveBeenCalledTimes(3);
    warn.mockRestore();
  });
});

describe('MyActivityService', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('requests the caller page and returns the response object', () => {
    let page: unknown;
    TestBed.inject(MyActivityService).list(2, 50).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === `${environment.gatewayUrl}${MY_ACTIVITY_PATH}`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ responseObject: { content: [{ id: 'e' }], totalElements: 1 } });
    expect(page).toEqual({ content: [{ id: 'e' }], totalElements: 1 });
  });

  it('returns an empty page when the response has no object', () => {
    let page: unknown;
    TestBed.inject(MyActivityService).list(0, 5).subscribe((p) => (page = p));
    http.expectOne((r) => r.url.endsWith(MY_ACTIVITY_PATH)).flush({});
    expect(page).toEqual({ content: [], totalElements: 0 });
  });
});

describe('ProfilePhotoService', () => {
  let docs: { list: ReturnType<typeof vi.fn>; fetchFile: ReturnType<typeof vi.fn> };
  let svc: ProfilePhotoService;
  let latest: string | null;
  let seq: number;

  beforeEach(() => {
    seq = 0;
    (URL as unknown as { createObjectURL: unknown }).createObjectURL = vi.fn(() => `blob:${++seq}`);
    (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = vi.fn();
    docs = { list: vi.fn(), fetchFile: vi.fn().mockReturnValue(of(new Blob(['x']))) };
    docs.list.mockReturnValue(of([{ contentType: 'application/pdf' }, { contentType: 'image/png', id: 'p' }]));
    TestBed.configureTestingModule({ providers: [{ provide: HqDocumentService, useValue: docs }] });
    svc = TestBed.inject(ProfilePhotoService);
    latest = 'unset';
    svc.photoUrl$.subscribe((u) => (latest = u));
  });

  it('loads the first image document of the user once', () => {
    svc.load('u1');
    expect(docs.list).toHaveBeenCalledWith(PROFILE_PHOTO_CATEGORY, 'u1');
    expect(docs.fetchFile).toHaveBeenCalledWith({ contentType: 'image/png', id: 'p' });
    expect(latest).toBe('blob:1');
    svc.load('u1');
    expect(docs.list).toHaveBeenCalledTimes(1);
  });

  it('shows nothing when there is no image, or the load fails', () => {
    docs.list.mockReturnValue(of([{ contentType: 'application/pdf' }, {}]));
    svc.load('u1');
    expect(latest).toBeNull();
    docs.list.mockReturnValue(throwError(() => new Error('x')));
    svc.refresh('u1');
    expect(latest).toBeNull();
  });

  it('clears for a missing user id and never shows the previous account photo', () => {
    svc.load('u1');
    expect(latest).toBe('blob:1');
    svc.load(null);
    expect(latest).toBeNull();
    svc.load('u1');
    docs.list.mockReturnValue(throwError(() => new Error('x')));
    svc.load('u2');
    expect(latest).toBeNull();
  });

  it('refresh reloads and revokes the old object URL; refresh without a user does nothing', () => {
    svc.load('u1');
    svc.refresh('u1');
    expect(latest).toBe('blob:2');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1');
    docs.list.mockClear();
    svc.refresh(undefined);
    expect(docs.list).not.toHaveBeenCalled();
  });
});

describe('OrganizationContextService', () => {
  const token = (claims: object) => `h.${btoa(JSON.stringify(claims))}.s`;
  const orgs = [{ tenantId: 't1', name: 'One', code: 'ONE', timeZone: 'Asia/Kolkata' }, { tenantId: 't2', name: 'Two', code: 'TWO' }];
  let uam: { get: ReturnType<typeof vi.fn> };
  let auth: { refresh: ReturnType<typeof vi.fn> };
  let svc: OrganizationContextService;

  beforeEach(() => {
    localStorage.clear();
    uam = { get: vi.fn().mockReturnValue(of(orgs)) };
    auth = { refresh: vi.fn().mockReturnValue(of({})) };
    TestBed.configureTestingModule({
      providers: [{ provide: UamService, useValue: uam }, { provide: AuthService, useValue: auth }],
    });
    svc = TestBed.inject(OrganizationContextService);
  });
  afterEach(() => localStorage.clear());

  it('loads the account organizations and resolves the current one from the token', () => {
    localStorage.setItem('access_token', token({ tenantId: 't1' }));
    const names: Array<string | null> = [];
    svc.currentName$.subscribe((n) => names.push(n));
    svc.load().subscribe();
    expect(uam.get).toHaveBeenCalledWith('/identity/me/organizations');
    expect(names.at(-1)).toBe('One');
    expect(svc.currentTenantId()).toBe('t1');
  });

  it('copes with a null list', () => {
    uam.get.mockReturnValue(of(null));
    let list: unknown;
    svc.organizations$.subscribe((l) => (list = l));
    svc.load().subscribe();
    expect(list).toEqual([]);
  });

  it('only an organization-wide caller may switch, and switching refreshes the session naming the organization', () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });
    localStorage.setItem('access_token', token({ tenantId: 't1' }));
    expect(svc.canSwitch).toBe(false);
    svc.switchTo(orgs[1] as never);
    expect(auth.refresh).not.toHaveBeenCalled();

    localStorage.setItem('access_token', token({ tenantId: 't1', orgWide: true }));
    expect(svc.canSwitch).toBe(true);
    svc.switchTo(orgs[1] as never);
    expect(auth.refresh).toHaveBeenCalledWith('TWO');
    expect(reload).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
