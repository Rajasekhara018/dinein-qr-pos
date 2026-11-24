/** Translation namespace of the analytics charts on the Today screen. */
export const TODAY_INSIGHTS_I18N = 'today.insights';

/** How far back the activity and revenue charts look. */
export const TODAY_INSIGHTS_DAYS = 30;

/** Matrimony analytics endpoints the charts read (matrimonial-api, no response envelope). */
export const TODAY_INSIGHTS_PATHS = {
  timeSeries: '/ops/analytics/timeseries',
  funnel: '/ops/analytics/funnel',
  revenue: '/ops/analytics/revenue',
  demographics: '/ops/analytics/demographics',
} as const;

/** Train booking trend (a stored daily rollup behind it, so 30 days is one indexed read). Goes through the gateway. */
export const TODAY_TRAIN_TREND_PATH = '/v1/train/admin/dashboard/chart';
export const TODAY_TRAIN_TREND_PERIOD = '30d';
export const TODAY_TRAIN_TREND_PRIVILEGE = 'TRAIN_ADMIN_DASHBOARD_CHART_READ';

/** Wallet ledger activity per day (read from stored daily numbers on the server). Goes through the gateway. */
export const TODAY_WALLET_TREND_PATH = '/v1/wallet/admin/trend';
export const TODAY_WALLET_TREND_DAYS = 30;
export const TODAY_WALLET_TREND_PRIVILEGE = 'WALLET_ADMIN_TREND_READ';

/** Rides per day (requested, completed, cancelled) and their revenue, read from stored daily numbers by RideService. Goes through the gateway. */
export const TODAY_RIDES_TREND_PATH = '/v1/admin/dashboard/rides-trend';
export const TODAY_RIDES_TREND_DAYS = 30;
export const TODAY_RIDES_TREND_PRIVILEGE = 'ADMIN_DASHBOARD_RIDES_TREND_READ';
/** The Today card of the rides product; a deployment profile that hides it also loses the rides charts. */
export const TODAY_RIDES_SECTION_ID = 'operations';

/** The privilege that lets the user see the matrimony figures; the charts are asked for only with it. */
export const TODAY_INSIGHTS_PRIVILEGE = 'MATRIMONY_ANALYTICS_SUMMARY_READ';

/** Chart sizes: the wide activity chart, and the smaller ones beside it. */
export const TODAY_INSIGHTS_HEIGHTS = { wide: 300, small: 280 } as const;

/** How many series, product types and funnel stages a chart shows at most. */
export const TODAY_INSIGHTS_LIMITS = { series: 3, products: 6, stages: 7, slices: 6 } as const;

/** Currency of a money chart whose amounts name none (older bare-number answers). */
export const TODAY_INSIGHTS_CURRENCY = 'INR';
