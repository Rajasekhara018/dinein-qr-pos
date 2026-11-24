import { AdminFormGuide, AdminFormGuideItem } from './admin-form.types';

const F = 'accessManagement.fields';

/** One guide line per backend code (shown as-is), explained under `<base>.<code>`. */
const codeItems = (base: string, codes: readonly string[]): AdminFormGuideItem[] =>
  codes.map((code) => ({ term: code, text: `${base}.${code}` }));

const U = 'accessManagement.users.guide';
/** The help beside the user form: what each field does, what the codes mean, and an example. */
export const USER_FORM_GUIDE: AdminFormGuide = {
  title: `${U}.title`,
  intro: `${U}.intro`,
  sections: [
    { title: `${U}.before.title`, text: `${U}.before.text` },
    {
      title: `${U}.fields.title`,
      items: [
        { term: 'accessManagement.users.fields.username', text: `${U}.fields.username` },
        { term: 'accessManagement.users.fields.password.label', text: `${U}.fields.password` },
        { term: 'profile.form.fullName.label', text: `${U}.fields.fullName` },
        { term: 'accessManagement.users.fields.userType', text: `${U}.fields.userType` },
        { term: 'profile.form.email.label', text: `${U}.fields.email` },
        { term: 'profile.form.phoneNumber.label', text: `${U}.fields.phone` },
        { term: 'accessManagement.users.fields.roles.label', text: `${U}.fields.roles` },
      ],
    },
    { title: `${U}.types.title`, items: codeItems(`${U}.types`, ['SUPER_ADMIN', 'ADMIN', 'OPS', 'BUSINESS', 'SECURITY', 'CUSTOMER', 'DRIVER', 'VENDOR']) },
    { title: `${U}.statuses.title`, items: codeItems(`${U}.statuses`, ['ACTIVE', 'INACTIVE', 'PENDING', 'LOCKED', 'SUSPENDED', 'DISABLED']) },
    { title: `${U}.practice.title`, text: `${U}.practice.text` },
    { title: `${U}.example.title`, text: `${U}.example.text` },
  ],
};

const O = 'accessManagement.organizations.guide';
const R = 'accessManagement.organizations.regional';
/** The help beside the organization form. */
export const ORGANIZATION_FORM_GUIDE: AdminFormGuide = {
  title: `${O}.title`,
  intro: `${O}.intro`,
  sections: [
    {
      title: `${O}.fields.title`,
      items: [
        { term: `${F}.code`, text: `${O}.fields.code` },
        { term: `${F}.name`, text: `${O}.fields.name` },
        { term: `${F}.description`, text: `${O}.fields.description` },
        { term: `${F}.supportEmail`, text: `${O}.fields.supportEmail` },
        { term: `${F}.supportPhone`, text: `${O}.fields.supportPhone` },
        { term: `${F}.maxUsers`, text: `${O}.fields.maxUsers` },
        { term: `${F}.maxApplications`, text: `${O}.fields.maxApplications` },
        { term: 'accessManagement.organizations.openForSelfSignup', text: `${O}.fields.selfSignup` },
      ],
    },
    { title: `${O}.statuses.title`, items: codeItems(`${O}.statuses`, ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED']) },
    { title: `${O}.dataScope.title`, items: codeItems(`${O}.dataScope`, ['ORGANIZATION', 'DEPARTMENT']) },
    {
      title: `${O}.regional.title`,
      text: `${O}.regional.text`,
      items: [
        { term: `${R}.currency`, text: `${O}.regional.currency` },
        { term: `${R}.country`, text: `${O}.regional.country` },
        { term: `${R}.timeZone`, text: `${O}.regional.timeZone` },
        { term: `${R}.defaultLanguage`, text: `${O}.regional.language` },
      ],
    },
    { title: `${O}.after.title`, text: `${O}.after.text` },
    { title: `${O}.example.title`, text: `${O}.example.text` },
  ],
};

const A = 'accessManagement.applications.guide';
/** The help beside the application form. */
export const APPLICATION_FORM_GUIDE: AdminFormGuide = {
  title: `${A}.title`,
  intro: `${A}.intro`,
  sections: [
    {
      title: `${A}.fields.title`,
      items: [
        { term: `${F}.code`, text: `${A}.fields.code` },
        { term: `${F}.name`, text: `${A}.fields.name` },
        { term: `${F}.baseUrl`, text: `${A}.fields.baseUrl` },
        { term: `${F}.landingPage`, text: `${A}.fields.landingPage` },
        { term: `${F}.supportEmail`, text: `${A}.fields.supportEmail` },
        { term: `${F}.type`, text: `${A}.fields.type` },
      ],
    },
    {
      title: `${A}.auth.title`,
      text: `${A}.auth.text`,
      items: codeItems(`${A}.auth`, ['LOCAL', 'KEYCLOAK', 'OAUTH2', 'OIDC', 'SAML', 'LDAP', 'AZURE_AD', 'FIREBASE', 'CUSTOM']),
    },
    { title: `${A}.example.title`, text: `${A}.example.text` },
  ],
};

const N = 'accessManagement.notificationTemplates.guide';
/** The help beside the notification template form. */
export const NOTIFICATION_TEMPLATE_FORM_GUIDE: AdminFormGuide = {
  title: `${N}.title`,
  intro: `${N}.intro`,
  sections: [
    {
      title: `${N}.fields.title`,
      items: [
        { term: `${F}.code`, text: `${N}.fields.code` },
        { term: `${F}.name`, text: `${N}.fields.name` },
        { term: `${F}.tenantId`, text: `${N}.fields.tenant` },
        { term: `${F}.applicationId`, text: `${N}.fields.application` },
        { term: `${F}.language`, text: `${N}.fields.language` },
        { term: `${F}.subject`, text: `${N}.fields.subject` },
        { term: `${F}.body`, text: `${N}.fields.body` },
        { term: `${F}.variables`, text: `${N}.fields.variables` },
      ],
    },
    { title: `${N}.channels.title`, items: codeItems(`${N}.channels`, ['EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'IN_APP']) },
    { title: `${N}.engines.title`, text: `${N}.engines.text`, items: codeItems(`${N}.engines`, ['PLAIN_TEXT', 'HTML', 'THYMELEAF', 'FREEMARKER', 'MUSTACHE']) },
    { title: `${N}.example.title`, text: `${N}.example.text` },
  ],
};

const RG = 'accessManagement.roles.guide';
const RL = 'accessManagement.roles.access';
/** The help beside the role form: what a role is, the access levels, the quick starts, the advanced view and an example. */
export const ROLE_FORM_GUIDE: AdminFormGuide = {
  title: `${RG}.title`,
  intro: `${RG}.intro`,
  sections: [
    {
      title: `${RG}.details.title`,
      items: [
        { term: 'accessManagement.roles.fields.code', text: `${RG}.details.code` },
        { term: 'accessManagement.roles.fields.name', text: `${RG}.details.name` },
        { term: 'accessManagement.roles.fields.description', text: `${RG}.details.description` },
        { term: 'accessManagement.roles.form.systemRoleCheckbox', text: `${RG}.details.system` },
      ],
    },
    {
      title: `${RG}.levels.title`,
      text: `${RG}.levels.text`,
      items: [
        { term: `${RL}.levels.none`, text: `${RG}.levels.none` },
        { term: `${RL}.levels.view`, text: `${RG}.levels.view` },
        { term: `${RL}.levels.manage`, text: `${RG}.levels.manage` },
        { term: `${RL}.levels.custom`, text: `${RG}.levels.custom` },
      ],
    },
    {
      title: `${RL}.templates.label`,
      text: `${RG}.quick.text`,
      items: [
        { term: `${RL}.templates.readOnly`, text: `${RG}.quick.readOnly` },
        { term: `${RL}.templates.fullAccess`, text: `${RG}.quick.fullAccess` },
        { term: `${RL}.templates.clear`, text: `${RG}.quick.clear` },
      ],
    },
    { title: `${RL}.useAdvanced`, text: `${RG}.advanced.text` },
    { title: `${RG}.practice.title`, text: `${RG}.practice.text` },
    { title: `${RG}.example.title`, text: `${RG}.example.text` },
  ],
};
