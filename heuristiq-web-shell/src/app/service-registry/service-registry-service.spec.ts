import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { RegistryApp } from './service-registry.models';
import { ServiceRegistryService } from './service-registry-service';

describe('ServiceRegistryService', () => {
  const url = `${environment.adminApiUrl}/registry/apps`;
  let service: ServiceRegistryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ServiceRegistryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the registered applications from the AdminAPI', () => {
    const apps: RegistryApp[] = [{ name: 'UAM', instances: [{ instanceId: 'uam-1', status: 'UP' }] }];
    let result: RegistryApp[] | undefined;

    service.applications().subscribe((r) => (result = r));
    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush({ status: true, data: apps });

    expect(result).toEqual(apps);
  });

  it('returns an empty list when the envelope carries no data', () => {
    let result: RegistryApp[] | undefined;

    service.applications().subscribe((r) => (result = r));
    http.expectOne(url).flush({ status: true });

    expect(result).toEqual([]);
  });

  it('returns an empty list for an empty body', () => {
    let result: RegistryApp[] | undefined;

    service.applications().subscribe((r) => (result = r));
    http.expectOne(url).flush(null);

    expect(result).toEqual([]);
  });

  it('turns a status:false envelope into an error carrying the server message', () => {
    let error: unknown;

    service.applications().subscribe({ error: (e) => (error = e) });
    http.expectOne(url).flush({ status: false, message: 'Registry down' });

    expect(error).toEqual({ error: { message: 'Registry down' } });
  });

  it('passes an HTTP failure through', () => {
    let status = 0;

    service.applications().subscribe({ error: (e) => (status = e.status) });
    http.expectOne(url).flush('nope', { status: 503, statusText: 'Unavailable' });

    expect(status).toBe(503);
  });
});
