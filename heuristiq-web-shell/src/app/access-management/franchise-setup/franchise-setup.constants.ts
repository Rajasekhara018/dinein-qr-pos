import { ONBOARDING_SUBJECTS, OnboardingSubject } from '../franchise-onboarding/franchise-onboarding.constants';
import { FRANCHISE_PRIVILEGES } from '../franchises/franchises.constants';
import { LEGAL_PROFILE_SUBJECTS } from '../legal-profile/legal-profile.constants';

/** The steps of a franchise's setup, in the order they are done; the id is also the last segment of the route. */
export type FranchiseSetupStepId = 'details' | 'legal' | 'kyc' | 'bank' | 'agreement' | 'activation';

/** The steps that are sections of the franchise's onboarding (one onboarding read model serves them all). */
export type FranchiseOnboardingSection = Extract<FranchiseSetupStepId, 'kyc' | 'bank' | 'agreement' | 'activation'>;
export const FRANCHISE_ONBOARDING_SECTIONS: readonly FranchiseOnboardingSection[] = ['kyc', 'bank', 'agreement', 'activation'];

/** One step: its words (translation keys), its icon, the privileges that show it (any one) and those that let it change something. */
export interface FranchiseSetupStep {
  id: FranchiseSetupStepId;
  labelKey: string;
  /** The one line under the title in the rail and the card heading (translation key). */
  summaryKey: string;
  icon: string;
  readPrivileges: readonly string[];
  writePrivileges: readonly string[];
}

/** Where a franchise's setup opens (`franchises/:id/setup/:step`, `my-franchises/:id/setup/:step`) and the step it starts on. */
export const FRANCHISE_SETUP_ROUTE = 'setup';
export const FRANCHISE_SETUP_DEFAULT_STEP: FranchiseSetupStepId = 'details';

/** Same screen permissions as the lists that open the setup; each step is then shown only to holders of its own read privilege. */
export const FRANCHISE_SETUP_PERMISSION = 'view_franchises';
export const MY_FRANCHISE_SETUP_PERMISSION = 'view_my_franchises';
/** Stable audit keys (area.screen): queryable history, never renamed. */
export const FRANCHISE_SETUP_SCREEN_KEY = 'access_management.franchise_setup';
export const MY_FRANCHISE_SETUP_SCREEN_KEY = 'access_management.my_franchise_setup';

/** Translation namespace of the screen's own words; the generic stepper words are shared with the organization setup. */
export const FRANCHISE_SETUP_I18N = 'accessManagement.franchises.setup';
export const SETUP_SHARED_I18N = 'accessManagement.organizations.setup';
/** The title while a franchise is being opened (the setup screen without a franchise yet). */
export const OPEN_FRANCHISE_TITLE_KEY = 'accessManagement.franchises.openTitle';

/** The list each kind of setup goes back to, and its menu label. */
export const FRANCHISE_SETUP_BACK: Readonly<Record<OnboardingSubject, { route: string; listKey: string }>> = {
  franchise: { route: 'franchises', listKey: 'nav.items.franchises' },
  myFranchise: { route: 'my-franchises', listKey: 'nav.items.myFranchises' },
};

function step(id: FranchiseSetupStepId, icon: string, readPrivileges: readonly string[], writePrivileges: readonly (string | null)[]): FranchiseSetupStep {
  return {
    id,
    labelKey: `${FRANCHISE_SETUP_I18N}.steps.${id}`,
    summaryKey: `${FRANCHISE_SETUP_I18N}.summaries.${id}`,
    icon,
    readPrivileges,
    writePrivileges: writePrivileges.filter((code): code is string => !!code),
  };
}

function onboardingSteps(subject: OnboardingSubject): FranchiseSetupStep[] {
  const p = ONBOARDING_SUBJECTS[subject].privileges;
  return [
    step('kyc', 'bi-person-vcard', [p.read], [p.addDocument, p.withdrawDocument, p.reviewDocument]),
    step('bank', 'bi-bank', [p.read], [p.saveBank, p.reviewBank]),
    step('agreement', 'bi-file-earmark-check', [p.read], [p.saveAgreement]),
    step('activation', 'bi-rocket-takeoff', [p.read], [p.activate]),
  ];
}

/**
 * The steps of each kind of setup. An administrator sets up any franchise: its applications and terms, legal and tax details, KYC,
 * payout bank account, agreement and activation. An owner sees their own franchise's legal details and onboarding (no terms, no
 * review, no activation: their privileges leave those actions out).
 */
export const FRANCHISE_SETUP_STEPS: Readonly<Record<OnboardingSubject, readonly FranchiseSetupStep[]>> = {
  franchise: [
    step('details', 'bi-shop', ['FRANCHISE_LIST'],
      [FRANCHISE_PRIVILEGES.updateApplication, FRANCHISE_PRIVILEGES.addApplication, FRANCHISE_PRIVILEGES.setStatus]),
    step('legal', 'bi-file-earmark-text', [LEGAL_PROFILE_SUBJECTS.franchise.readPrivilege], [LEGAL_PROFILE_SUBJECTS.franchise.updatePrivilege]),
    ...onboardingSteps('franchise'),
  ],
  myFranchise: [
    step('legal', 'bi-file-earmark-text', [LEGAL_PROFILE_SUBJECTS.myFranchise.readPrivilege], [LEGAL_PROFILE_SUBJECTS.myFranchise.updatePrivilege]),
    ...onboardingSteps('myFranchise'),
  ],
};

/** Checklist states that count a step as done. */
export const FRANCHISE_SETUP_DONE_STATES: readonly string[] = ['DONE', 'VERIFIED'];
