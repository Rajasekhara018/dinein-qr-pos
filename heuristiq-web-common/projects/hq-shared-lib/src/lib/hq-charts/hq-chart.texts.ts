import { InjectionToken } from '@angular/core';

/**
 * Words the chart set shows. The library has no translation dependency: an app provides HQ_CHART_TEXTS with translated texts;
 * every entry is optional and falls back to English.
 */
export interface HqChartTexts {
  /** "Chart" option of a card's Chart | Table toggle. */
  chart?(): string;
  /** "Table" option of a card's Chart | Table toggle. */
  table?(): string;
  noData?(): string;
  /** Label of the series that folds everything past the colour limit. */
  other?(): string;
  /** Funnel: conversion from the previous stage ("from previous stage"). */
  conversion?(): string;
  /** Funnel: share of the first stage ("of first stage"). */
  ofFirst?(): string;
  legend?(): string;
  // Date-range presets.
  today?(): string;
  last7Days?(): string;
  last30Days?(): string;
  lastMonth?(): string;
  currentMonth?(): string;
  currentYear?(): string;
  custom?(): string;
  from?(): string;
  to?(): string;
  dateRange?(): string;
}

export const HQ_CHART_TEXTS = new InjectionToken<HqChartTexts>('HQ_CHART_TEXTS');

/**
 * ApexCharts licence key. The premium chart types (unit, waffle, pictogram, raincloud) draw with the ApexCharts trial watermark
 * without one; provide the organisation's key here (from configuration, never hard-coded) to remove it.
 */
export const HQ_CHART_LICENSE = new InjectionToken<string>('HQ_CHART_LICENSE');

export const HQ_CHART_DEFAULT_TEXTS: Required<HqChartTexts> = {
  chart: () => 'Chart',
  table: () => 'Table',
  noData: () => 'No data for this period',
  other: () => 'Other',
  conversion: () => 'from previous stage',
  ofFirst: () => 'of first stage',
  legend: () => 'Legend',
  today: () => 'Today',
  last7Days: () => 'Last 7 days',
  last30Days: () => 'Last 30 days',
  lastMonth: () => 'Last month',
  currentMonth: () => 'Current month',
  currentYear: () => 'Current year',
  custom: () => 'Custom',
  from: () => 'From',
  to: () => 'To',
  dateRange: () => 'Date range',
};

export type HqChartTextKey = keyof HqChartTexts;

/** One chart text: the app's own when provided, else the English default. */
export function hqChartText(texts: HqChartTexts | null, key: HqChartTextKey): string {
  const own = texts?.[key];
  return own ? own() : HQ_CHART_DEFAULT_TEXTS[key]();
}
