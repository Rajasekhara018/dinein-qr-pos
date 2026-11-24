import { HqAmount } from 'hq-shared-lib';

/** One line of a time-series response: its key, its unit and one value per bucket. */
export interface InsightSeries {
  key: string;
  unit: 'COUNT' | 'MONEY';
  values: number[];
}

export interface InsightTimeSeries {
  /** ISO dates, one per point; every series shares this axis. */
  buckets: string[];
  series: InsightSeries[];
}

export interface InsightFunnel {
  stages: { key: string; label: string; count: number }[];
}

/** Money is `{ code, value }` (money stage 3); read it through hqAmountValue / hqAmountCode, which also take an older bare number. */
export interface InsightRevenue {
  /** Older answers only: the currency of the bare-number amounts (it is now inside each amount). */
  currency?: string;
  netCollected: HqAmount | null;
  byProductType: { key: string; net: HqAmount | null }[];
}

export interface InsightDemographics {
  byGender: { key: string; total: number }[];
  byAgeBand: { key: string; total: number }[];
}

/** The train booking trend: one point per day (the label is a weekday or day/month). */
export interface InsightTrainTrend {
  bookingTrend: { day: string; bookings: number; revenue: HqAmount | null }[];
}

/** The wallet ledger activity: one date axis and a series per journal type, each with a value and a count per day. */
export interface InsightWalletTrend {
  days: string[];
  series: { journalType: string; amounts: (HqAmount | null)[]; counts: number[] }[];
}

/** One day of the rides trend; the revenue is the fare of the completed rides (`{ code, value }`). */
export interface InsightRidesTrendPoint {
  /** ISO date (yyyy-MM-dd). */
  day: string;
  requested: number;
  completed: number;
  cancelled: number;
  revenue: HqAmount | null;
}

/** What the analytics calls returned; a call that failed is null and its chart is left out. */
export interface TodayInsights {
  timeSeries: InsightTimeSeries | null;
  funnel: InsightFunnel | null;
  revenue: InsightRevenue | null;
  demographics: InsightDemographics | null;
  trainTrend: InsightTrainTrend | null;
  walletTrend: InsightWalletTrend | null;
  /** One row per day, oldest first. */
  ridesTrend: InsightRidesTrendPoint[] | null;
}
