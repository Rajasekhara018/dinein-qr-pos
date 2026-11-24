import { HqLegalProfile } from 'hq-shared-lib';

/** Where a step stands: not looked at yet, done, or holding edits that are not saved. */
export type OrganizationSetupStepStatus = 'todo' | 'done' | 'unsaved';

/** What the stepper helpers need of a step: its id and the privileges that show it (the organization and franchise setups share them). */
export interface SetupStepLike<Id extends string = string> {
  id: Id;
  readPrivileges: readonly string[];
}

/** The steps this person may open: those whose main read they hold (any one of its privileges). */
export function visibleSteps<S extends SetupStepLike>(steps: readonly S[], can: (codes: readonly string[]) => boolean): S[] {
  return steps.filter((step) => can(step.readPrivileges));
}

/** The step a route segment names, when the person may open it; otherwise the first step they may open, or null when none. */
export function resolveStep<Id extends string>(param: string | null, steps: readonly SetupStepLike<Id>[]): Id | null {
  const wanted = steps.find((step) => step.id === param);
  return (wanted ?? steps[0])?.id ?? null;
}

/** The step before (-1) or after (+1) the given one among the visible steps, or null at either end. */
export function neighbourStep<Id extends string>(steps: readonly SetupStepLike<Id>[], current: Id | null, delta: -1 | 1): Id | null {
  const index = steps.findIndex((step) => step.id === current);
  if (index < 0) return null;
  return steps[index + delta]?.id ?? null;
}

/** Unsaved edits win; otherwise a step is done once it was opened and has what it needs. */
export function stepStatus(state: { unsaved: boolean; visited: boolean; complete: boolean }): OrganizationSetupStepStatus {
  if (state.unsaved) return 'unsaved';
  return state.visited && state.complete ? 'done' : 'todo';
}

/** Share of the steps that are done, 0..100, rounded (the rail's progress bar); 0 when there are none. */
export function setupProgressPercent(statuses: readonly OrganizationSetupStepStatus[]): number {
  if (!statuses.length) return 0;
  return Math.round((statuses.filter((status) => status === 'done').length / statuses.length) * 100);
}

/** A legal profile is complete once it names the legal entity and has a registered address with its street and city. */
export function legalProfileComplete(profile: HqLegalProfile | null | undefined): boolean {
  if (!profile) return false;
  const registered = profile.addresses?.find((address) => address.kind === 'REGISTERED');
  return !!profile.legalName?.trim() && !!registered?.line1?.trim() && !!registered?.city?.trim();
}
