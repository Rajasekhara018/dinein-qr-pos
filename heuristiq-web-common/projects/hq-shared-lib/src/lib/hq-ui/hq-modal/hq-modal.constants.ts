/** Modal widths. */
export type HqModalSize = 'sm' | 'md' | 'lg' | 'xl';

export const HQ_MODAL_DEFAULT_SIZE: HqModalSize = 'md';

/** CDK backdrop class (dark, part of the CDK overlay styles every app already loads). */
export const HQ_MODAL_BACKDROP_CLASS = 'cdk-overlay-dark-backdrop';

/** Class on the overlay pane, so a screen can target an open modal from global styles. */
export const HQ_MODAL_PANE_CLASS = 'hq-modal-pane';
