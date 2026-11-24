import { HqStatusTone } from 'hq-shared-lib';
import { TableColumnSpec } from '../shared/table-columns.helpers';
import { FieldType, OnboardingSchemaResponse, SchemaStatus } from './onboarding-forms.models';

/** Tone of each schema version status badge (hq-status-badge). */
export const STATUS_BADGE_TONE: Record<SchemaStatus, HqStatusTone> = {
  DRAFT: 'neutral',
  PUBLISHED: 'success',
  ARCHIVED: 'danger',
};

export const FIELD_TYPES: FieldType[] = [
  'TEXT', 'NUMBER', 'DATE', 'SELECT', 'CHECKBOX', 'DOCUMENT', 'PHONE', 'EMAIL', 'CURRENCY',
];

export const FIELD_TYPE_ICONS: Record<FieldType, string> = {
  TEXT: 'bi-fonts',
  NUMBER: 'bi-123',
  DATE: 'bi-calendar3',
  SELECT: 'bi-list-ul',
  CHECKBOX: 'bi-check-square',
  DOCUMENT: 'bi-file-earmark-text',
  PHONE: 'bi-telephone',
  EMAIL: 'bi-envelope',
  CURRENCY: 'bi-currency-rupee',
};

export const FALLBACK_FIELD_TYPE_ICON = 'bi-question-circle';

/** Type codes the backend's DefaultOnboardingSeeder seeds a built-in default definition for. */
export const TYPES_WITH_DEFAULT_DEFINITION: readonly string[] = ['CUSTOMER', 'DRIVER'];

/** Onboarding type code and field/step key shape: starts with a letter, then letters/digits/underscores. */
export const IDENTIFIER_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;

export const STEP_KEY_PREFIX = 'step_';

export const DATE_LOCALE_FALLBACK = 'en';

/** Privileges (privilege.json names) that allow changing a type's review requirement; any one is enough. */
export const REQUIRES_REVIEW_PRIVILEGES: readonly string[] = ['ONBOARDING_ADMIN_TYPE_REQUIRES_REVIEW_UPDATE', 'RIDER_ONBOARDING_ADMIN_ACCESS'];

/** Longest custom pattern a field may have; the onboarding service enforces the same limit. */
export const MAX_FIELD_PATTERN_LENGTH = 500;

/** Columns of the version history: translation keys for the headings, cells are drawn by the screen's templates. */
export const SCHEMA_VERSION_COLUMNS: readonly TableColumnSpec<OnboardingSchemaResponse>[] = [
  { key: 'version', labelKey: 'onboardingForms.versions.columns.version' },
  { key: 'status', labelKey: 'onboardingForms.versions.columns.status' },
  { key: 'steps', labelKey: 'onboardingForms.versions.columns.steps' },
  { key: 'published', labelKey: 'onboardingForms.versions.columns.published' },
  { key: 'created', labelKey: 'onboardingForms.versions.columns.created' },
];
