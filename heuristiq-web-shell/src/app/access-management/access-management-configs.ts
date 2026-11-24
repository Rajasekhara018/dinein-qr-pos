import { AdminFormConfig, AdminFormField } from './admin-form.types';
import { APPLICATION_CODE_BOX, APPLICATION_CODE_PANEL, APPLICATION_TYPE_OPTIONS, ORGANIZATION_SELF_SIGNUP_PRIVILEGES } from './access-management.constants';
import { loadApplicationCatalogueOptions, loadApplicationScopeOptions, loadOrganizationOptions } from './uam-option-loaders';
import { loadCountryOptions, loadCurrencyOptions, loadTimeZoneOptions } from './regional-option-loaders';
import { ORGANIZATION_LANGUAGES } from '../shared/utils/regional-names.constants';
import { languageOptions } from '../shared/utils/regional-names.helpers';
import {
  CLIENT_ID_MAX_LENGTH,
  CLIENT_ID_PATTERN,
  POLICY_IDLE_TIMEOUT_MIN_MINUTES,
  POLICY_MAX_FAILED_ATTEMPTS_CEILING,
  POLICY_MAX_FAILED_ATTEMPTS_FLOOR,
  POLICY_NON_NEGATIVE_MIN,
  POLICY_PASSWORD_LENGTH_CEILING,
  POLICY_PASSWORD_MIN_LENGTH_FLOOR,
  REDIRECT_URI_MAX_LENGTH,
  RULE_ERROR_KEYS,
} from './access-management-rules.constants';
import { authorizationCodeRedirectError, passwordLengthOrderError, redirectUrisError } from './access-management.validators';
import { SECURITY_POLICY_FORM_GUIDE } from './security-policy-guide.constants';
import { APPLICATION_FORM_GUIDE, NOTIFICATION_TEMPLATE_FORM_GUIDE, ORGANIZATION_FORM_GUIDE } from './admin-form-guides.constants';
import { CLIENT_AUTH_METHOD_OPTIONS, CLIENT_FORM_GUIDE, CLIENT_GRANT_TYPE_OPTIONS, CLIENT_SECRET_METHOD, CLIENT_TYPE_OPTIONS } from './api-client.constants';

const F = 'accessManagement.fields'; // shared field-label keys reused across these configs
const C = 'accessManagement.clients';

// Users and Roles have dedicated screens (users-list/, roles-list/) instead of this generic config-driven list — see uam-option-loaders.ts for
// the entity-picker option loaders they (and this file's remaining configs) share.
//
// NOTE: entityName, column labels, and field label/hint are all *translation keys*, not display
// text. AdminFormService resolves field label/hint/entityName through TranslateService when the
// dialog opens; each list component (e.g. organizations-list.ts) resolves `columns[].label` the
// same way before handing the array to hq-common-table, since this config is a static object
// built once at module load, before TranslateService has an active language.

export const ORGANIZATION_CRUD_CONFIG: AdminFormConfig = {
  resourcePath: '/organizations',
  entityName: 'accessManagement.organizations.entityName',
  columns: [
    { key: 'code', label: `${F}.code` },
    { key: 'name', label: `${F}.name` },
    { key: 'type', label: `${F}.type` },
    { key: 'status', label: `${F}.status` },
    { key: 'supportEmail', label: `${F}.supportEmail` },
  ],
  fields: [
    { key: 'code', label: `${F}.code`, type: 'text', required: true, maxLength: 30, pattern: /^[A-Za-z0-9_]{2,30}$/, patternMessage: 'accessManagement.organizations.errors.code' },
    { key: 'name', label: `${F}.name`, type: 'text', required: true, maxLength: 100 },
    { key: 'description', label: `${F}.description`, type: 'textarea', maxLength: 500 },
    { key: 'status', label: `${F}.status`, type: 'select', options: ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'].map((v) => ({ value: v, label: v })) },
    { key: 'supportEmail', label: `${F}.supportEmail`, type: 'text', maxLength: 150, pattern: /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/, patternMessage: 'accessManagement.organizations.errors.email' },
    { key: 'supportPhone', label: `${F}.supportPhone`, type: 'phone' },
    { key: 'dataScope', readKey: 'settings.dataScope', label: `${F}.dataScope`, type: 'select', hint: 'accessManagement.organizations.dataScopeHint',
      options: ['ORGANIZATION', 'DEPARTMENT'].map((v) => ({ value: v, label: v })) },
    // Regional settings: the organization's money, phone numbers, dates and starting language follow them.
    { key: 'currency', label: 'accessManagement.organizations.regional.currency', type: 'select', searchable: true, loadOptions: loadCurrencyOptions,
      hint: 'accessManagement.organizations.regional.currencyHint' },
    { key: 'country', label: 'accessManagement.organizations.regional.country', type: 'select', searchable: true, loadOptions: loadCountryOptions,
      hint: 'accessManagement.organizations.regional.countryHint' },
    { key: 'timeZone', label: 'accessManagement.organizations.regional.timeZone', type: 'select', searchable: true, loadOptions: loadTimeZoneOptions,
      hint: 'accessManagement.organizations.regional.timeZoneHint' },
    { key: 'defaultLanguage', label: 'accessManagement.organizations.regional.defaultLanguage', type: 'select', searchable: true,
      options: languageOptions(ORGANIZATION_LANGUAGES), hint: 'accessManagement.organizations.regional.defaultLanguageHint' },
    { key: 'maxUsers', label: `${F}.maxUsers`, type: 'number', min: 1, minMessage: 'accessManagement.organizations.errors.min' },
    { key: 'maxApplications', label: `${F}.maxApplications`, type: 'number', min: 1, minMessage: 'accessManagement.organizations.errors.min' },
    // Opt-in to the public sign-up organization list; off by default. Only those who may update organizations see and send it.
    { key: 'openForSelfSignup', readKey: 'settings.openForSelfSignup', label: 'accessManagement.organizations.openForSelfSignup', type: 'checkbox',
      defaultValue: false, hint: 'accessManagement.organizations.openForSelfSignupHint', privileges: ORGANIZATION_SELF_SIGNUP_PRIVILEGES },
  ],
  guide: ORGANIZATION_FORM_GUIDE,
};

export const APPLICATION_CRUD_CONFIG: AdminFormConfig = {
  resourcePath: '/applications',
  entityName: 'accessManagement.applications.entityName',
  columns: [
    { key: 'code', label: `${F}.code` },
    { key: 'name', label: `${F}.name` },
    { key: 'type', label: `${F}.type` },
    { key: 'authenticationMode', label: `${F}.authMode` },
    { key: 'baseUrl', label: `${F}.baseUrl` },
  ],
  fields: [
    // The code comes from UAM's fixed catalogue (never typed): codes this organization already has are disabled, picking one
    // fills in the name, description and type, and the code cannot change once the application exists.
    { key: 'code', label: `${F}.code`, type: 'select', required: true, loadOptions: loadApplicationCatalogueOptions, readOnly: 'edit',
      optionPanel: APPLICATION_CODE_PANEL, codeBox: APPLICATION_CODE_BOX,
      hint: 'accessManagement.applications.codeHint', disabledOptionHint: 'accessManagement.applications.alreadyCreated',
      emptyOptionsMessage: 'accessManagement.applications.catalogueUnavailable', allDisabledMessage: 'accessManagement.applications.allCreated' },
    // An application several organizations share keeps one name and description: only a SUPER_ADMIN may change them (UAM
    // answers 403 to anyone else), which the hints say.
    { key: 'name', label: `${F}.name`, type: 'text', maxLength: 200, hint: 'accessManagement.applications.sharedTextHint' },
    { key: 'description', label: `${F}.description`, type: 'textarea', maxLength: 1000, hint: 'accessManagement.applications.sharedTextHint' },
    // Release versions belong to build/release records; preserve legacy metadata without asking admins to edit it.
    { key: 'appVersion', label: `${F}.version`, type: 'text', maxLength: 255, hidden: true },
    { key: 'baseUrl', label: `${F}.baseUrl`, type: 'text', maxLength: 255 },
    { key: 'landingPage', label: `${F}.landingPage`, type: 'text', maxLength: 255 },

    // No owner team on the application form: the shared application row has none (the organization's owner team is set elsewhere).
    { key: 'supportEmail', label: `${F}.supportEmail`, type: 'text', maxLength: 255 },
    // Fixed by the catalogue entry picked above.
    { key: 'type', label: `${F}.type`, type: 'select', options: APPLICATION_TYPE_OPTIONS, readOnly: 'always', hint: 'accessManagement.applications.typeHint' },
    { key: 'authenticationMode', label: `${F}.authenticationMode`, type: 'select', options: ['LOCAL', 'LDAP', 'OAUTH2', 'OIDC', 'SAML', 'KEYCLOAK', 'FIREBASE', 'AZURE_AD', 'CUSTOM'].map((v) => ({ value: v, label: v })) },
    // Platform settings an organization's admin does not set here: kept out of sight, but still loaded and saved as they are.
    { key: 'ssoEnabled', label: `${F}.ssoEnabled`, type: 'checkbox', hidden: true },
    { key: 'mfaRequired', label: `${F}.mfaRequired`, type: 'checkbox', hidden: true },
    // A new application starts active; an existing one without the flag counts as active (UAM's default).
    { key: 'active', label: `${F}.active`, type: 'checkbox', defaultValue: true, readValue: (entity) => entity['active'] !== false, hidden: true },
    { key: 'displayOrder', label: `${F}.displayOrder`, type: 'number', hidden: true },
  ],
  guide: APPLICATION_FORM_GUIDE,
};

export const CLIENT_CRUD_CONFIG: AdminFormConfig = {
  resourcePath: '/clients',
  entityName: 'accessManagement.clients.entityName',
  columns: [
    { key: 'clientId', label: `${F}.clientId` },
    { key: 'name', label: `${F}.name` },
    { key: 'clientType', label: `${F}.type` },
    { key: 'authenticationMethod', label: `${F}.authMethod` },
    { key: 'active', label: `${F}.active` },
  ],
  fields: [
    { key: 'clientId', label: `${F}.clientId`, type: 'text', required: true, maxLength: CLIENT_ID_MAX_LENGTH, pattern: CLIENT_ID_PATTERN, patternMessage: RULE_ERROR_KEYS.clientId },
    // Chosen from the organizations and applications that exist, never typed: a mistyped id would scope the client to nothing.
    { key: 'tenantId', label: `${F}.tenantId`, type: 'select', loadOptions: loadOrganizationOptions },
    { key: 'applicationId', label: `${F}.applicationId`, type: 'select', loadOptions: loadApplicationScopeOptions },
    { key: 'name', label: `${F}.name`, type: 'text' },
    { key: 'description', label: `${F}.description`, type: 'textarea' },
    // Picking a type fills in the authentication method and grant types that kind of client normally uses.
    { key: 'clientType', label: `${F}.clientType`, type: 'select', options: CLIENT_TYPE_OPTIONS, hint: `${C}.hints.clientType` },
    { key: 'authenticationMethod', label: `${F}.authenticationMethod`, type: 'select', options: CLIENT_AUTH_METHOD_OPTIONS,
      defaultValue: CLIENT_SECRET_METHOD, disabledOptionHint: `${C}.hints.notSupportedYet`, hint: `${C}.hints.authenticationMethod` },
    { key: 'grantTypes', label: `${F}.grantTypes`, type: 'multiselect', options: CLIENT_GRANT_TYPE_OPTIONS,
      rule: authorizationCodeRedirectError, hint: `${C}.hints.grantTypes` },
    { key: 'scopes', label: `${F}.scopes`, type: 'tags', hint: `${C}.hints.scopes` },
    { key: 'redirectUris', label: `${F}.redirectUris`, type: 'tags', rule: (values) => redirectUrisError(values), hint: `${C}.hints.redirectUris` },
    // A new client starts active: an inactive one is disabled in Keycloak and cannot get a token.
    { key: 'active', label: `${F}.active`, type: 'checkbox', defaultValue: true },
  ],
  guide: CLIENT_FORM_GUIDE,
};

function policyFields(fields: AdminFormField[]): AdminFormField[] {
  return fields.map(field => field.type === 'checkbox' && field.key.includes('.') ? {
    ...field, type: 'select', booleanSelect: true, defaultValue: '',
    options: [
      { value: '', label: 'accessManagement.securityPolicies.inherit' },
      { value: 'true', label: 'accessManagement.securityPolicies.yes' },
      { value: 'false', label: 'accessManagement.securityPolicies.no' },
    ],
  } : field);
}

export const SECURITY_POLICY_CRUD_CONFIG: AdminFormConfig = {
  resourcePath: '/security-policies',
  entityName: 'accessManagement.securityPolicies.entityName',
  columns: [
    { key: 'name', label: `${F}.name` },
    { key: 'tenantId', label: `${F}.tenantId` },
    { key: 'applicationId', label: `${F}.applicationId` },
    { key: 'active', label: `${F}.active` },
  ],
  fields: policyFields([
    { key: 'name', label: `${F}.name`, type: 'text', required: true },
    { key: 'description', label: `${F}.description`, type: 'textarea' },
    { key: 'tenantId', label: `${F}.tenantId`, type: 'select', loadOptions: loadOrganizationOptions, hint: `${F}.globalPolicyHint` },
    { key: 'applicationId', label: `${F}.applicationId`, type: 'select', loadOptions: loadApplicationScopeOptions, hint: `${F}.globalPolicyHint` },
    { key: 'passwordPolicy.minimumLength', label: `${F}.password.minLength`, type: 'number', min: POLICY_PASSWORD_MIN_LENGTH_FLOOR, max: POLICY_PASSWORD_LENGTH_CEILING,
      minMessage: RULE_ERROR_KEYS.passwordMinLength, maxMessage: RULE_ERROR_KEYS.passwordMinLength },
    { key: 'passwordPolicy.maximumLength', label: `${F}.password.maxLength`, type: 'number', min: POLICY_NON_NEGATIVE_MIN, minMessage: RULE_ERROR_KEYS.notNegative, rule: passwordLengthOrderError },
    { key: 'passwordPolicy.requireUppercase', label: `${F}.password.requireUppercase`, type: 'checkbox' },
    { key: 'passwordPolicy.requireLowercase', label: `${F}.password.requireLowercase`, type: 'checkbox' },
    { key: 'passwordPolicy.requireNumber', label: `${F}.password.requireNumber`, type: 'checkbox' },
    { key: 'passwordPolicy.requireSpecialCharacter', label: `${F}.password.requireSpecialCharacter`, type: 'checkbox' },
    { key: 'passwordPolicy.preventUsernameInPassword', label: `${F}.password.preventUsername`, type: 'checkbox' },
    { key: 'passwordPolicy.passwordHistoryCount', label: `${F}.password.historyCount`, type: 'number', min: 0, max: 100 },
    { key: 'passwordPolicy.minimumAgeDays', label: `${F}.password.minimumAgeDays`, type: 'number', min: 0 },
    { key: 'passwordPolicy.expiryDays', label: `${F}.password.expiryDays`, type: 'number', min: POLICY_NON_NEGATIVE_MIN, minMessage: RULE_ERROR_KEYS.notNegative },
    { key: 'loginPolicy.maxFailedAttempts', label: `${F}.login.maxFailedAttempts`, type: 'number', min: POLICY_MAX_FAILED_ATTEMPTS_FLOOR, max: POLICY_MAX_FAILED_ATTEMPTS_CEILING,
      minMessage: RULE_ERROR_KEYS.maxFailedAttempts, maxMessage: RULE_ERROR_KEYS.maxFailedAttempts },
    { key: 'loginPolicy.lockDurationMinutes', label: `${F}.login.lockDuration`, type: 'number', min: POLICY_NON_NEGATIVE_MIN, minMessage: RULE_ERROR_KEYS.notNegative },
    { key: 'loginPolicy.allowConcurrentSessions', label: `${F}.login.allowConcurrentSessions`, type: 'checkbox' },
    { key: 'loginPolicy.maxConcurrentSessions', label: `${F}.login.maxConcurrentSessions`, type: 'number', min: POLICY_NON_NEGATIVE_MIN, minMessage: RULE_ERROR_KEYS.notNegative },
    { key: 'sessionPolicy.idleTimeoutMinutes', label: `${F}.session.idleTimeout`, type: 'number', min: POLICY_IDLE_TIMEOUT_MIN_MINUTES, minMessage: RULE_ERROR_KEYS.idleTimeout },
    { key: 'sessionPolicy.absoluteTimeoutMinutes', label: `${F}.session.absoluteTimeout`, type: 'number', min: POLICY_NON_NEGATIVE_MIN, minMessage: RULE_ERROR_KEYS.notNegative },
    { key: 'sessionPolicy.singleSessionOnly', label: `${F}.session.singleSessionOnly`, type: 'checkbox' },
    { key: 'sessionPolicy.invalidateOnPasswordChange', label: `${F}.session.invalidateOnPasswordChange`, type: 'checkbox' },
    { key: 'sessionPolicy.invalidateOnRoleChange', label: `${F}.session.invalidateOnRoleChange`, type: 'checkbox' },
    { key: 'mfaPolicy.enabled', label: `${F}.mfa.enabled`, type: 'checkbox' },
    { key: 'mfaPolicy.mandatory', label: `${F}.mfa.mandatory`, type: 'checkbox' },
    { key: 'mfaPolicy.emailOtpAllowed', label: `${F}.mfa.emailOtpAllowed`, type: 'checkbox' },
    { key: 'mfaPolicy.smsOtpAllowed', label: `${F}.mfa.smsOtpAllowed`, type: 'checkbox' },
    { key: 'mfaPolicy.authenticatorAppAllowed', label: `${F}.mfa.authenticatorAppAllowed`, type: 'checkbox' },
    { key: 'accountPolicy.selfRegistrationAllowed', label: `${F}.account.selfRegistrationAllowed`, type: 'checkbox' },
    { key: 'accountPolicy.emailVerificationRequired', label: `${F}.account.emailVerificationRequired`, type: 'checkbox' },
    { key: 'accountPolicy.phoneVerificationRequired', label: `${F}.account.phoneVerificationRequired`, type: 'checkbox' },
    { key: 'accountPolicy.adminApprovalRequired', label: `${F}.account.adminApprovalRequired`, type: 'checkbox' },
    { key: 'active', label: `${F}.active`, type: 'checkbox', defaultValue: true },
    { key: 'effectiveFrom', label: 'accessManagement.securityPolicies.effectiveFrom', type: 'text', emptyAsNull: true, hint: 'accessManagement.securityPolicies.scheduleHint' },
    { key: 'effectiveUntil', label: 'accessManagement.securityPolicies.effectiveUntil', type: 'text', emptyAsNull: true, hint: 'accessManagement.securityPolicies.scheduleHint' },
    { key: 'version', label: `${F}.version`, type: 'number', hidden: true },
  ]),
  guide: SECURITY_POLICY_FORM_GUIDE,
};

export const NOTIFICATION_TEMPLATE_CRUD_CONFIG: AdminFormConfig = {
  resourcePath: '/notification-templates',
  entityName: 'accessManagement.notificationTemplates.entityName',
  columns: [
    { key: 'code', label: `${F}.code` },
    { key: 'name', label: `${F}.name` },
    { key: 'channel', label: `${F}.channel` },
    { key: 'language', label: `${F}.language` },
    { key: 'engine', label: `${F}.engine` },
  ],
  fields: [
    { key: 'code', label: `${F}.code`, type: 'text', required: true },
    { key: 'name', label: `${F}.name`, type: 'text' },
    { key: 'description', label: `${F}.description`, type: 'textarea' },
    { key: 'tenantId', label: `${F}.tenantId`, type: 'text' },
    { key: 'applicationId', label: `${F}.applicationId`, type: 'text' },
    { key: 'channel', label: `${F}.channel`, type: 'select', options: ['EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'IN_APP'].map((v) => ({ value: v, label: v })) },
    { key: 'language', label: `${F}.language`, type: 'select', options: ['TE', 'EN', 'HI', 'TA', 'ML', 'OD', 'KA', 'AR'].map((v) => ({ value: v, label: v })) },
    { key: 'subject', label: `${F}.subject`, type: 'text' },
    { key: 'body', label: `${F}.body`, type: 'textarea' },
    { key: 'engine', label: `${F}.templateEngine`, type: 'select', options: ['PLAIN_TEXT', 'HTML', 'THYMELEAF', 'FREEMARKER', 'MUSTACHE'].map((v) => ({ value: v, label: v })) },
    { key: 'variables', label: `${F}.variables`, type: 'tags', hint: `${F}.variablesHint` },
  ],
  guide: NOTIFICATION_TEMPLATE_FORM_GUIDE,
};
