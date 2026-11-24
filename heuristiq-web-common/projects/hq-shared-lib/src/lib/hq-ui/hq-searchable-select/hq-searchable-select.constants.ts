import { ConnectedPosition } from '@angular/cdk/overlay';

/** Where the list opens: under the field, or above it when there is no room below. */
export const HQ_SEARCHABLE_SELECT_POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
];

/** Narrowest the list is drawn, so long names stay readable under a narrow field. */
export const HQ_SEARCHABLE_SELECT_MIN_PANEL_PX = 220;

/** At or below this viewport width the list is drawn as a near full-width sheet. */
export const HQ_SEARCHABLE_SELECT_PHONE_MAX_PX = 480;

/** Space kept at each side of that sheet. */
export const HQ_SEARCHABLE_SELECT_PHONE_MARGIN_PX = 8;
