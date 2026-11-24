import { LEGAL_PROFILE_SUBJECTS } from '../legal-profile/legal-profile.constants';
import { ORGANIZATION_APPLICATION_PRIVILEGES } from '../organization-applications/organization-applications.constants';
import { ORGANIZATION_MEMBER_PRIVILEGES } from '../organization-members/organization-members.constants';
import { ORGANIZATION_OPERATING_MODEL_PRIVILEGES } from '../organization-operating-model/organization-operating-model.constants';

/** The steps of an organization's setup, in the order they are done; the id is also the last segment of the route. */
export type OrganizationSetupStepId = 'details' | 'legal' | 'operating-model' | 'applications' | 'members';

/** One step: its words (translation key), its icon, the privileges that show it (any one) and those that let it change something. */
export interface OrganizationSetupStep {
  id: OrganizationSetupStepId;
  labelKey: string;
  /** The one line under the title in the rail and the card heading (translation key). */
  summaryKey: string;
  icon: string;
  readPrivileges: readonly string[];
  writePrivileges: readonly string[];
}

/** What the shell needs from a step page that has a form: whether it holds unsaved edits and which required fields are still open. */
export interface OrganizationSetupStepForm {
  hasPendingChanges(): boolean;
  missingFields(): string[];
}

/** Where an organization's setup opens and which step it starts on. */
export const ORGANIZATION_SETUP_ROUTE = 'setup';
export const ORGANIZATION_SETUP_DEFAULT_STEP: OrganizationSetupStepId = 'details';

/** The organizations area's screen permission opens the setup too (same people, same screen family as the list). */
export const ORGANIZATION_SETUP_PERMISSION = 'view_organizations';
export const ORGANIZATION_SETUP_SCREEN_KEY = 'access_management.organization_setup';

/** Translation namespace of the screen's words. */
export const ORGANIZATION_SETUP_I18N = 'accessManagement.organizations.setup';

export const ORGANIZATION_SETUP_STEPS: readonly OrganizationSetupStep[] = [
  {
    id: 'details',
    labelKey: `${ORGANIZATION_SETUP_I18N}.steps.details`,
    summaryKey: `${ORGANIZATION_SETUP_I18N}.summaries.details`,
    icon: 'bi-building',
    readPrivileges: ['ORGANIZATION_READ'],
    writePrivileges: ['ORGANIZATION_UPDATE'],
  },
  {
    id: 'legal',
    labelKey: `${ORGANIZATION_SETUP_I18N}.steps.legal`,
    summaryKey: `${ORGANIZATION_SETUP_I18N}.summaries.legal`,
    icon: 'bi-file-earmark-text',
    readPrivileges: [LEGAL_PROFILE_SUBJECTS.organization.readPrivilege],
    writePrivileges: [LEGAL_PROFILE_SUBJECTS.organization.updatePrivilege],
  },
  {
    id: 'operating-model',
    labelKey: `${ORGANIZATION_SETUP_I18N}.steps.operatingModel`,
    summaryKey: `${ORGANIZATION_SETUP_I18N}.summaries.operatingModel`,
    icon: 'bi-diagram-3',
    readPrivileges: [ORGANIZATION_OPERATING_MODEL_PRIVILEGES.read],
    writePrivileges: [ORGANIZATION_OPERATING_MODEL_PRIVILEGES.update],
  },
  {
    id: 'applications',
    labelKey: `${ORGANIZATION_SETUP_I18N}.steps.applications`,
    summaryKey: `${ORGANIZATION_SETUP_I18N}.summaries.applications`,
    icon: 'bi-grid',
    readPrivileges: [ORGANIZATION_APPLICATION_PRIVILEGES.list],
    writePrivileges: [ORGANIZATION_APPLICATION_PRIVILEGES.subscribe, ORGANIZATION_APPLICATION_PRIVILEGES.update, ORGANIZATION_APPLICATION_PRIVILEGES.cancel],
  },
  {
    id: 'members',
    labelKey: `${ORGANIZATION_SETUP_I18N}.steps.members`,
    summaryKey: `${ORGANIZATION_SETUP_I18N}.summaries.members`,
    icon: 'bi-people',
    readPrivileges: [ORGANIZATION_MEMBER_PRIVILEGES.list],
    writePrivileges: [ORGANIZATION_MEMBER_PRIVILEGES.add, ORGANIZATION_MEMBER_PRIVILEGES.update],
  },
];
