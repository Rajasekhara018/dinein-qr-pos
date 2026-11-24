/** How a tab strip is drawn: underlined tabs, a segmented control, or separate pills. */
export type HqTabsAppearance = 'tabs' | 'segmented' | 'pills';

/** Tab strip sizes. */
export type HqTabsSize = 'sm' | 'md';

/** What a tab may link to (a `routerLink` value). */
export type HqTabLink = string | readonly unknown[] | null;

export const HQ_TABS_DEFAULT_APPEARANCE: HqTabsAppearance = 'segmented';
export const HQ_TABS_DEFAULT_SIZE: HqTabsSize = 'md';

/** Keys that move focus along the strip. */
export const HQ_TABS_NAV_KEYS = ['ArrowLeft', 'ArrowRight', 'Home', 'End'] as const;
