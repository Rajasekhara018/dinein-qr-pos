/** Shapes of a loading placeholder. */
export type HqSkeletonVariant = 'text' | 'rect' | 'circle' | 'table' | 'card';

export const HQ_SKELETON_DEFAULT_VARIANT: HqSkeletonVariant = 'text';
export const HQ_SKELETON_DEFAULT_LINES = 3;
export const HQ_SKELETON_DEFAULT_ROWS = 5;
export const HQ_SKELETON_DEFAULT_COLS = 4;
/** Upper bound for lines/rows/cols, so a bad binding cannot draw thousands of bars. */
export const HQ_SKELETON_MAX_COUNT = 50;
