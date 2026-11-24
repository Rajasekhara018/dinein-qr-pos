import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HQ_CONFIG } from '../hq-common.config';
import { HqApplication } from './hq-application.model';
import { HQ_APPLICATIONS_CACHE_TTL_MS } from './hq-applications.constants';
import { HqApplicationsService } from './hq-applications.service';

const URL = 'http://gw/api/v1/uam/identity/applications';
const WALLET: HqApplication = { code: 'CENTRALISED_WALLET_APP', name: 'Centralised Wallet', type: 'WEB', displayOrder: 50 };
const RIDER: HqApplication = { code: 'BESTRIDE_RIDER_APP', name: 'BestRide Rider', type: 'MOBILE', displayOrder: 20, iconHint: 'local_taxi' };
const SHOP: HqApplication = { code: 'BEST_PURCHASE_APP', name: 'Best Purchase', type: 'WEB', displayOrder: 20 };

describe('HqApplicationsService', () => {
  let service: HqApplicationsService;
  let http: HttpTestingController;
  let now: number;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HQ_CONFIG, useValue: { gatewayUrl: 'http://gw/api/', adminApiUrl: 'http://admin', currentUserId: () => 'u' } },
      ],
    });
    now = 1_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    service = TestBed.inject(HqApplicationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  const answer = (list: HqApplication[] | undefined, success = true) =>
    http.expectOne(URL).flush({ success, responseObject: list });

  it('loads the session organization applications through the gateway UAM route, sorted by display order then code', async () => {
    const result = firstValueFrom(service.load());
    expect(service.loading()).toBe(true);
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, responseObject: [WALLET, SHOP, RIDER] });

    expect((await result).map((a) => a.code)).toEqual(['BESTRIDE_RIDER_APP', 'BEST_PURCHASE_APP', 'CENTRALISED_WALLET_APP']);
    expect(service.applications().length).toBe(3);
    expect(service.loading()).toBe(false);
    expect(service.loadFailed()).toBe(false);
  });

  it('reuses a fresh list without a request and reloads once it is stale', async () => {
    const first = firstValueFrom(service.load());
    answer([RIDER]);
    await first;

    now += HQ_APPLICATIONS_CACHE_TTL_MS - 1;
    expect(await firstValueFrom(service.load())).toEqual([RIDER]);
    http.expectNone(URL);

    now += 1;
    const stale = firstValueFrom(service.load());
    answer([RIDER, WALLET]);
    expect((await stale).length).toBe(2);
  });

  it('shares one request between concurrent readers', async () => {
    const a = firstValueFrom(service.load());
    const b = firstValueFrom(service.load());
    answer([WALLET]);

    expect(await a).toEqual([WALLET]);
    expect(await b).toEqual([WALLET]);
  });

  it('refresh reloads at once even when the list is fresh', async () => {
    const first = firstValueFrom(service.load());
    answer([RIDER]);
    await first;

    const refreshed = firstValueFrom(service.refresh());
    answer([WALLET]);

    expect(await refreshed).toEqual([WALLET]);
    expect(service.applications()).toEqual([WALLET]);
  });

  it('a failed load reaches load() callers, keeps the last good list and flags the failure', async () => {
    const first = firstValueFrom(service.load());
    answer([RIDER]);
    await first;

    const failed = firstValueFrom(service.refresh());
    http.expectOne(URL).flush('boom', { status: 503, statusText: 'Service Unavailable' });

    await expect(failed).rejects.toBeTruthy();
    expect(service.loadFailed()).toBe(true);
    expect(service.applications()).toEqual([RIDER]);
    expect(service.loading()).toBe(false);
  });

  it('treats an unsuccessful envelope as a failure, with the server message or a default', async () => {
    const withMessage = firstValueFrom(service.load());
    http.expectOne(URL).flush({ success: false, message: 'Nope' });
    await expect(withMessage).rejects.toEqual({ error: { message: 'Nope' } });

    const withoutMessage = firstValueFrom(service.load());
    http.expectOne(URL).flush(null);
    await expect(withoutMessage).rejects.toEqual({ error: { message: 'Applications request failed' } });
    expect(service.loadFailed()).toBe(true);
  });

  it('a successful answer without a list is an empty list and clears the failure flag', async () => {
    const failed = firstValueFrom(service.load());
    http.expectOne(URL).flush({ success: false });
    await expect(failed).rejects.toBeTruthy();

    const empty = firstValueFrom(service.load());
    answer(undefined);

    expect(await empty).toEqual([]);
    expect(service.loadFailed()).toBe(false);
  });

  it('applications$ loads when needed, follows later changes and never errors', async () => {
    const seen: HqApplication[][] = [];
    const sub = service.applications$.subscribe((list) => seen.push(list));
    answer([RIDER]);

    const refreshed = firstValueFrom(service.refresh());
    answer([RIDER, WALLET]);
    await refreshed;
    service.clear();
    sub.unsubscribe();

    expect(seen).toEqual([[RIDER], [RIDER, WALLET], []]);
  });

  it('applications$ falls back to the last list when its load fails', () => {
    const seen: HqApplication[][] = [];
    const sub = service.applications$.subscribe({ next: (list) => seen.push(list), error: () => seen.push([WALLET]) });
    http.expectOne(URL).flush('x', { status: 500, statusText: 'Error' });
    sub.unsubscribe();

    expect(seen).toEqual([[]]);
    expect(service.loadFailed()).toBe(true);
  });

  it('clear forgets the list and the failure, so the next read loads again', async () => {
    const failed = firstValueFrom(service.load());
    http.expectOne(URL).flush('x', { status: 500, statusText: 'Error' });
    await expect(failed).rejects.toBeTruthy();
    const ok = firstValueFrom(service.load());
    answer([RIDER]);
    await ok;

    service.clear();

    expect(service.applications()).toEqual([]);
    expect(service.loadFailed()).toBe(false);
    const again = firstValueFrom(service.load());
    answer([WALLET]);
    expect(await again).toEqual([WALLET]);
  });

  it('nameOf and find use the current list and fall back to the code', async () => {
    const loaded = firstValueFrom(service.load());
    answer([RIDER]);
    await loaded;

    expect(service.nameOf('BESTRIDE_RIDER_APP')).toBe('BestRide Rider');
    expect(service.nameOf('UNKNOWN_APP')).toBe('UNKNOWN_APP');
    expect(service.nameOf(null)).toBe('');
    expect(service.nameOf(undefined)).toBe('');
    expect(service.find('BESTRIDE_RIDER_APP')?.iconHint).toBe('local_taxi');
    expect(service.find('')).toBeUndefined();
  });
});
