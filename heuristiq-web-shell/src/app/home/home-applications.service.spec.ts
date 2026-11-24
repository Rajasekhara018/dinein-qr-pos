import { TestBed } from '@angular/core/testing';
import { HqApplicationsService } from 'hq-shared-lib';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HomeApplicationsService } from './home-applications.service';

const tokenWith = (claims: object): string => `h.${btoa(JSON.stringify(claims))}.s`;
const app = (code: string) => ({ code, name: code, type: 'WEB', displayOrder: 1 });

describe('HomeApplicationsService', () => {
  const hq = { load: vi.fn(), refresh: vi.fn() };
  let service: HomeApplicationsService;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [{ provide: HqApplicationsService, useValue: hq }] });
    service = TestBed.inject(HomeApplicationsService);
  });

  it('starts loading with nothing subscribed', () => {
    expect(service.status()).toBe('loading');
    expect(service.subscribedCodes()).toEqual([]);
  });

  it('keeps the upper-cased codes of the organization applications and becomes ready', () => {
    hq.load.mockReturnValue(of([app('food_deliver_app'), app('BEST_PURCHASE_APP')]));

    service.load().subscribe();

    expect(service.status()).toBe('ready');
    expect(service.subscribedCodes()).toEqual(['FOOD_DELIVER_APP', 'BEST_PURCHASE_APP']);
    expect(hq.refresh).not.toHaveBeenCalled();
  });

  it('fails closed: no subscribed codes and status failed when the lookup errors', () => {
    hq.load.mockReturnValue(of([app('BEST_PURCHASE_APP')]));
    service.load().subscribe();
    hq.load.mockReturnValue(throwError(() => new Error('503')));

    service.load().subscribe();

    expect(service.status()).toBe('failed');
    expect(service.subscribedCodes()).toEqual([]);
  });

  it('reads the subscriptions again when forced (retry)', () => {
    hq.refresh.mockReturnValue(of([app('CENTRALISED_WALLET_APP')]));

    service.load(true).subscribe();

    expect(hq.refresh).toHaveBeenCalledTimes(1);
    expect(service.subscribedCodes()).toEqual(['CENTRALISED_WALLET_APP']);
  });

  it('is loading while the request is outstanding', () => {
    hq.load.mockReturnValue(new Observable(() => undefined));

    service.load().subscribe();

    expect(service.status()).toBe('loading');
  });

  it('reads the granted application codes from the access token, none without a token', () => {
    expect(service.grantedCodes()).toEqual([]);
    localStorage.setItem('access_token', tokenWith({ apps: ['best_purchase_app'] }));
    expect(service.grantedCodes()).toEqual(['BEST_PURCHASE_APP']);
  });

  describe('ensureLoaded (sidebar and header)', () => {
    it('reads the subscriptions once and not again while known', () => {
      hq.load.mockReturnValue(of([app('CENTRALISED_WALLET_APP')]));

      service.ensureLoaded();
      service.ensureLoaded();

      expect(hq.load).toHaveBeenCalledTimes(1);
      expect(service.subscribedCodes()).toEqual(['CENTRALISED_WALLET_APP']);
    });

    it('does not start a second request while one is outstanding', () => {
      hq.load.mockReturnValue(new Observable(() => undefined));

      service.ensureLoaded();
      service.ensureLoaded();

      expect(hq.load).toHaveBeenCalledTimes(1);
      expect(service.status()).toBe('loading');
    });

    it('retries quietly after a failure, fail closed meanwhile', () => {
      hq.load.mockReturnValueOnce(throwError(() => new Error('503'))).mockReturnValueOnce(of([app('FOOD_DELIVER_APP')]));

      service.ensureLoaded();
      expect(service.status()).toBe('failed');
      expect(service.subscribedCodes()).toEqual([]);

      service.ensureLoaded();
      expect(service.status()).toBe('ready');
      expect(service.subscribedCodes()).toEqual(['FOOD_DELIVER_APP']);
    });
  });
});
