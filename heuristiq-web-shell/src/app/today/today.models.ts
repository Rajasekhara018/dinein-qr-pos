
/** Where a tile's numbers come from; each source is one existing summary endpoint (or the signed-in user's own counters). */
export type TodaySource = 'waiting' | 'agentDesk' | 'earnings' | 'workspace' | 'matrimony' | 'train' | 'operations' | 'wallet';

/** How a raw number is shown. */
export type MetricFormat = 'count' | 'currency' | 'percent';

/** What a non-zero value means for the colour of the figure: a queue is a warning, a failure is bad, the rest is plain. */
export type MetricTone = 'plain' | 'pending' | 'bad';

/** One figure on the screen. `path` is a dotted path into the source's response (see readNumber). */
export interface MetricSpec {
  key: string;
  /** bootstrap-icons class of the figure's badge. */
  icon: string;
  /** i18n key of the label. */
  labelKey: string;
  path: string;
  format: MetricFormat;
  tone: MetricTone;
  /** Screen the figure belongs to (a router path); the tile is a link when present. */
  link?: string;
  /** Dotted path to a link in the source's response that, when present, replaces `link` (e.g. the one application concerned). */
  linkPath?: string;
  /** Privilege code(s) needed to see this figure; defaults to the section's. */
  privilege?: string | readonly string[];
}

/** A card: one application area with its figures. */
export interface SectionSpec {
  id: TodaySource;
  /** i18n key of the card title. */
  titleKey: string;
  icon: string;
  /** Where "Open" takes the user. */
  link: string;
  /** Privilege code(s) needed for the figures that name none of their own; null = every signed-in user. */
  privilege: string | readonly string[] | null;
  metrics: readonly MetricSpec[];
}

export type SectionState = 'loading' | 'ready' | 'error';

/** A figure ready to render. */
export interface MetricView {
  key: string;
  icon: string;
  labelKey: string;
  /** Formatted text; null when the source did not supply the figure. */
  text: string | null;
  /** The figure itself (what the charts add up); null when the source did not supply it. */
  value: number | null;
  tone: MetricTone;
  /** True when the figure is non-zero and its tone should show. */
  highlighted: boolean;
  link?: string;
}

/** A card ready to render. */
export interface SectionView {
  id: TodaySource;
  titleKey: string;
  icon: string;
  link: string;
  state: SectionState;
  /** How many figures are non-zero queues or failures: what the user has to act on in this card. */
  attention: number;
  metrics: MetricView[];
}

/** A highlighted figure lifted out of its card for the "needs attention" strip. */
export interface AttentionItem {
  sectionId: TodaySource;
  sectionTitleKey: string;
  metric: MetricView;
  /** Where the figure leads (its own screen, else its card's). */
  link: string;
}

/** Raw response of one source: any JSON object. */
export type SourceData = Readonly<Record<string, unknown>>;


/** What matrimonial-api's `GET /agent/dashboard` answers (the fields the Today card reads). */
export interface TodayAgentDesk {
  customers?: number | null;
  openLeads?: number | null;
  overdueRequests?: number | null;
  successes?: number | null;
  meetingsThisWeek?: readonly unknown[] | null;
}
