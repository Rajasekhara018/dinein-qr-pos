import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiService } from '../shared/services/api-service';
import { TODAY_INSIGHTS_DAYS, TODAY_INSIGHTS_PATHS, TODAY_RIDES_TREND_DAYS, TODAY_RIDES_TREND_PATH, TODAY_TRAIN_TREND_PATH, TODAY_TRAIN_TREND_PERIOD, TODAY_WALLET_TREND_DAYS, TODAY_WALLET_TREND_PATH } from './today-insights.constants';
import { insightsRange } from './today-insights.helpers';
import { InsightRidesTrendPoint, InsightTrainTrend, InsightWalletTrend, TodayInsights } from './today-insights.models';

/** Which analytics the user may see; a source that is off is not asked for. */
export interface InsightSources {
  matrimony: boolean;
  train: boolean;
  wallet: boolean;
  rides: boolean;
}

/**
 * Reads the chart data of the Today screen from the matrimony analytics endpoints (one request each, so a failing one only leaves
 * its own chart out). Every call is read-only; the screen decides from privileges whether to ask at all.
 */
@Injectable({ providedIn: 'root' })
export class TodayInsightsService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);

  /** The last {@link TODAY_INSIGHTS_DAYS} UTC days (today included) of activity, funnel and revenue, and who is on the platform now. */
  fetch(sources: InsightSources, now: Date = new Date()): Observable<TodayInsights> {
    const base = environment.matrimonyApiUrl;
    const range = insightsRange(now, TODAY_INSIGHTS_DAYS);
    const get = <T>(path: string, params: Record<string, string> = {}): Observable<T | null> =>
      this.http.get<T>(`${base}${path}`, { params }).pipe(catchError(() => of(null)));
    const matrimony = <T>(path: string, params: Record<string, string> = {}): Observable<T | null> => (sources.matrimony ? get<T>(path, params) : of(null));
    const train$: Observable<InsightTrainTrend | null> = sources.train
      ? this.api.get<InsightTrainTrend>(TODAY_TRAIN_TREND_PATH, { period: TODAY_TRAIN_TREND_PERIOD }).pipe(catchError(() => of(null)))
      : of(null);
    const wallet$: Observable<InsightWalletTrend | null> = sources.wallet
      ? this.api.get<InsightWalletTrend>(TODAY_WALLET_TREND_PATH, { days: TODAY_WALLET_TREND_DAYS }).pipe(catchError(() => of(null)))
      : of(null);
    const rides$: Observable<InsightRidesTrendPoint[] | null> = sources.rides
      ? this.api.get<InsightRidesTrendPoint[]>(TODAY_RIDES_TREND_PATH, { days: TODAY_RIDES_TREND_DAYS }).pipe(catchError(() => of(null)))
      : of(null);
    return forkJoin({
      timeSeries: matrimony<TodayInsights['timeSeries'] & object>(TODAY_INSIGHTS_PATHS.timeSeries, { ...range, interval: 'DAY' }),
      funnel: matrimony<TodayInsights['funnel'] & object>(TODAY_INSIGHTS_PATHS.funnel, range),
      revenue: matrimony<TodayInsights['revenue'] & object>(TODAY_INSIGHTS_PATHS.revenue, range),
      demographics: matrimony<TodayInsights['demographics'] & object>(TODAY_INSIGHTS_PATHS.demographics),
      trainTrend: train$,
      walletTrend: wallet$,
      ridesTrend: rides$,
    });
  }
}
