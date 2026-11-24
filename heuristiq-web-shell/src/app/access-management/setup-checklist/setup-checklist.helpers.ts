import { SetupItemState } from './setup-checklist.models';

/** true: set up; false: missing; null or undefined: not needed for the organization's model. */
export function setupItemState(value: boolean | null | undefined): SetupItemState {
  if (value === true) {
    return 'done';
  }
  return value === false ? 'missing' : 'notNeeded';
}

/** How many of the given items are still missing. */
export function missingCount(values: ReadonlyArray<boolean | null | undefined>): number {
  return values.filter((value) => value === false).length;
}
