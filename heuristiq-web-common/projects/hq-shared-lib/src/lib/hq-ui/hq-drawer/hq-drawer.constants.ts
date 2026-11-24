/** Preset drawer widths. */
export type HqDrawerSize = 'sm' | 'md' | 'lg' | 'xl';

/** Which edge the drawer slides from; `end` is the right edge in left-to-right pages and the left edge in RTL. */
export type HqDrawerPosition = 'start' | 'end';

/** Width in px of each preset (capped at the viewport width on small screens). `md` is the old app drawers' 480px. */
export const HQ_DRAWER_WIDTHS: Readonly<Record<HqDrawerSize, number>> = { sm: 360, md: 480, lg: 640, xl: 860 };

export const HQ_DRAWER_DEFAULT_SIZE: HqDrawerSize = 'md';
export const HQ_DRAWER_DEFAULT_POSITION: HqDrawerPosition = 'end';

/** CDK backdrop class (dark, part of the CDK overlay styles every app already loads). */
export const HQ_DRAWER_BACKDROP_CLASS = 'cdk-overlay-dark-backdrop';

/** Class on the overlay pane, so a screen can target an open drawer from global styles. */
export const HQ_DRAWER_PANE_CLASS = 'hq-drawer-pane';
