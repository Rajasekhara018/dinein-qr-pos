import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiService } from '../shared/services/api-service';
import { TODAY_RIDES_TREND_PATH } from './today-insights.constants';
import { TodayInsightsService } from './today-insights.service';

describe('TodayInsightsService', () => {
  let service: TodayInsightsService;
  let http: HttpTestingController;
  let apiGet: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    apiGet = vi.fn((): Observable<unknown> => of(null));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ApiService, useValue: { get: apiGet } }],
    });
    service = TestBed.inject(TodayInsightsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the matrimony charts for whole UTC days ending at the start of tomorrow, so the server can use its stored daily numbers', () => {
    service.fetch({ matrimony: true, train: false, wallet: false, rides: false }, new Date('2026-11-05T15:42:10.123Z')).subscribe();

    const expected = { from: '2026-10-07T00:00:00.000Z', to: '2026-11-06T00:00:00.000Z' };
    const series = http.expectOne((r) => r.url === `${environment.matrimonyApiUrl}/ops/analytics/timeseries`);
    expect(series.request.params.get('from')).toBe(expected.from);
    expect(series.request.params.get('to')).toBe(expected.to);
    expect(series.request.params.get('interval')).toBe('DAY');
    series.flush({});
    for (const path of ['funnel', 'revenue']) {
      const req = http.expectOne((r) => r.url === `${environment.matrimonyApiUrl}/ops/analytics/${path}`);
      expect(req.request.params.get('from')).toBe(expected.from);
      expect(req.request.params.get('to')).toBe(expected.to);
      req.flush({});
    }
    http.expectOne((r) => r.url === `${environment.matrimonyApiUrl}/ops/analytics/demographics`).flush({});
  });

  it('does not call the matrimony endpoints when that source is off', () => {
    service.fetch({ matrimony: false, train: false, wallet: false, rides: false }).subscribe();

    http.expectNone((r) => r.url.includes('/ops/analytics/'));
  });

  it('asks for the rides trend of the last 30 days through the gateway and hands it on', () => {
    const points = [{ day: '2026-11-05', requested: 3, completed: 2, cancelled: 1, revenue: { code: 'INR', value: 250 } }];
    apiGet.mockReturnValue(of(points));
    let result: unknown;

    service.fetch({ matrimony: false, train: false, wallet: false, rides: true }).subscribe((insights) => (result = insights.ridesTrend));

    expect(apiGet).toHaveBeenCalledWith(TODAY_RIDES_TREND_PATH, { days: 30 });
    expect(TODAY_RIDES_TREND_PATH).toBe('/v1/admin/dashboard/rides-trend');
    expect(result).toEqual(points);
  });

  it('does not ask for the rides trend when the user may not see it', () => {
    let result: unknown = 'unset';

    service.fetch({ matrimony: false, train: false, wallet: false, rides: false }).subscribe((insights) => (result = insights.ridesTrend));

    expect(apiGet).not.toHaveBeenCalled();
    expect(result).toBeNull();
  });

  it('leaves only the rides chart out when its call fails', () => {
    apiGet.mockReturnValue(throwError(() => new Error('503')));
    let result: unknown = 'unset';

    service.fetch({ matrimony: false, train: false, wallet: false, rides: true }).subscribe((insights) => (result = insights.ridesTrend));

    expect(result).toBeNull();
  });
});
