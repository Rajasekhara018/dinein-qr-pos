import { ConnectedPosition } from '@angular/cdk/overlay';

/** Where the tooltip sits around its element. `start` and `end` follow the reading direction (mirrored right to left). */
export type HqTooltipPosition = 'top' | 'bottom' | 'start' | 'end';

export const HQ_TOOLTIP_DEFAULT_POSITION: HqTooltipPosition = 'top';

/** Wait before showing on hover, so passing the pointer over a toolbar does not flash tooltips. */
export const HQ_TOOLTIP_SHOW_DELAY_MS = 350;
/** Short wait before hiding, so moving between an element and its neighbour does not flicker. */
export const HQ_TOOLTIP_HIDE_DELAY_MS = 60;
/** Holding a finger this long on a touch screen shows the tooltip; it hides again after HQ_TOOLTIP_TOUCH_HIDE_MS. */
export const HQ_TOOLTIP_LONG_PRESS_MS = 500;
export const HQ_TOOLTIP_TOUCH_HIDE_MS = 1500;

/** Gap between the element and the tooltip, in pixels. */
export const HQ_TOOLTIP_OFFSET_PX = 8;

const TOP: ConnectedPosition = { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -HQ_TOOLTIP_OFFSET_PX, panelClass: 'hq-tooltip-pane--top' };
const BOTTOM: ConnectedPosition = { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: HQ_TOOLTIP_OFFSET_PX, panelClass: 'hq-tooltip-pane--bottom' };
const START: ConnectedPosition = { originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -HQ_TOOLTIP_OFFSET_PX, panelClass: 'hq-tooltip-pane--start' };
const END: ConnectedPosition = { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: HQ_TOOLTIP_OFFSET_PX, panelClass: 'hq-tooltip-pane--end' };

/** The wanted position first, then the opposite side, then the rest: the first one that fits on screen is used. */
export const HQ_TOOLTIP_POSITIONS: Readonly<Record<HqTooltipPosition, ConnectedPosition[]>> = {
  top: [TOP, BOTTOM, END, START],
  bottom: [BOTTOM, TOP, END, START],
  start: [START, END, TOP, BOTTOM],
  end: [END, START, TOP, BOTTOM],
};
