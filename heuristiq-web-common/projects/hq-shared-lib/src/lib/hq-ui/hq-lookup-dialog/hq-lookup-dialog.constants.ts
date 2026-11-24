import { HqLookupOperator } from '../../hq-common.models';

/** Key of the leading checkbox column of a multi-select lookup table (its cells and header are projected templates). */
export const LOOKUP_SELECT_COLUMN = '__select';

/** Key of the trailing round pick column of a single-select lookup table. */
export const LOOKUP_PICK_COLUMN = '__pick';

/** Placeholder rows shown while a page loads. */
export const LOOKUP_SKELETON_ROWS = [0, 1, 2, 3, 4];

/** Every condition operator, in the order offered: equals, not equals, contains. */
export const LOOKUP_ALL_OPERATORS: readonly HqLookupOperator[] = ['EQ', 'NE', 'LIKE'];

/** The operators of a number, date or yes/no column (a "contains" match has no meaning there). */
export const LOOKUP_EXACT_OPERATORS: readonly HqLookupOperator[] = ['EQ', 'NE'];
