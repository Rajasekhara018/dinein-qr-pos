import { FranchiseOnboarding } from '../franchise-onboarding/franchise-onboarding.models';
import {
  FRANCHISE_ONBOARDING_SECTIONS, FRANCHISE_SETUP_DONE_STATES, FranchiseOnboardingSection, FranchiseSetupStepId,
} from './franchise-setup.constants';

/** True when a checklist state counts as done (DONE or VERIFIED: submitted or rejected work is not done yet). */
export function checklistDone(state: string | null | undefined): boolean {
  return !!state && FRANCHISE_SETUP_DONE_STATES.includes(state);
}

/**
 * The steps the franchise service reports as done: the details (the franchise exists), and from the onboarding read model the
 * legal details, KYC, bank account and agreement items of its checklist, and activation once it is onboarded. Null onboarding
 * (not read, or no privilege to read it) leaves only the details.
 */
export function completedSteps(onboarding: FranchiseOnboarding | null): Set<FranchiseSetupStepId> {
  const done = new Set<FranchiseSetupStepId>(['details']);
  if (!onboarding) return done;
  const checklist = onboarding.checklist;
  if (checklistDone(checklist?.legalProfile)) done.add('legal');
  if (checklistDone(checklist?.kyc)) done.add('kyc');
  if (checklistDone(checklist?.bankAccount)) done.add('bank');
  if (checklistDone(checklist?.agreement)) done.add('agreement');
  if (onboarding.onboardingStatus === 'ONBOARDED') done.add('activation');
  return done;
}

/** The onboarding section a step shows, or null for a step that is not part of the onboarding. */
export function onboardingSectionOf(id: FranchiseSetupStepId | null): FranchiseOnboardingSection | null {
  return FRANCHISE_ONBOARDING_SECTIONS.find((section) => section === id) ?? null;
}
