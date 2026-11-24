/** One line of the line chart: values oldest first, its categorical slot and the translation key of its legend text. */
export interface QaLineSeries {
  key: string;
  labelKey: string;
  /** Categorical slot of the shared chart palette (1-based); the same entity keeps the same slot everywhere. */
  slot: number;
  values: Array<{ label: string; value: number }>;
}

/** One row of the status split's table view: a status (already translated) and how many tests ended with it. */
export interface QaDonutTableRow {
  status: string;
  count: number;
}

/** One row of the release chart's table view: a release and its count per status. */
export type QaReleaseTableRow = { release: string } & Record<'PASSED' | 'FAILED' | 'SKIPPED', number>;

/** One row of the line chart's table view: an x-axis label and each series' value there (null where a series is shorter). */
export interface QaLineTableRow {
  label: string;
  values: Record<string, number | null>;
}
