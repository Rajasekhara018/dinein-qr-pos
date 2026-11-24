import { AdminFormGuide } from './admin-form.types';

const G = 'accessManagement.securityPolicies.guide';
const F = 'accessManagement.fields';

/** The help beside the security policy form: how policies combine and what every setting does, with examples. */
export const SECURITY_POLICY_FORM_GUIDE: AdminFormGuide = {
  title: `${G}.title`,
  intro: `${G}.intro`,
  sections: [
    {
      title: `${G}.combine.title`,
      text: `${G}.combine.text`,
      items: [
        { term: `${G}.combine.globalTerm`, text: `${G}.combine.global` },
        { term: `${G}.combine.orgTerm`, text: `${G}.combine.org` },
        { term: `${G}.combine.appTerm`, text: `${G}.combine.app` },
        { term: `${G}.combine.inheritTerm`, text: `${G}.combine.inherit` },
      ],
    },
    {
      title: `${G}.scope.title`,
      items: [
        { term: `${F}.name`, text: `${G}.scope.name` },
        { term: `${F}.description`, text: `${G}.scope.description` },
        { term: `${F}.tenantId`, text: `${G}.scope.tenant` },
        { term: `${F}.applicationId`, text: `${G}.scope.application` },
      ],
    },
    {
      title: `${G}.password.title`,
      items: [
        { term: `${F}.password.minLength`, text: `${G}.password.minLength` },
        { term: `${F}.password.maxLength`, text: `${G}.password.maxLength` },
        { term: `${F}.password.requireUppercase`, text: `${G}.password.upper` },
        { term: `${F}.password.requireLowercase`, text: `${G}.password.lower` },
        { term: `${F}.password.requireNumber`, text: `${G}.password.number` },
        { term: `${F}.password.requireSpecialCharacter`, text: `${G}.password.special` },
        { term: `${F}.password.preventUsername`, text: `${G}.password.username` },
        { term: `${F}.password.historyCount`, text: `${G}.password.history` },
        { term: `${F}.password.minimumAgeDays`, text: `${G}.password.minAge` },
        { term: `${F}.password.expiryDays`, text: `${G}.password.expiry` },
      ],
    },
    {
      title: `${G}.login.title`,
      items: [
        { term: `${F}.login.maxFailedAttempts`, text: `${G}.login.maxFailed` },
        { term: `${F}.login.lockDuration`, text: `${G}.login.lockDuration` },
        { term: `${F}.login.allowConcurrentSessions`, text: `${G}.login.allowConcurrent` },
        { term: `${F}.login.maxConcurrentSessions`, text: `${G}.login.maxConcurrent` },
      ],
    },
    {
      title: `${G}.session.title`,
      items: [
        { term: `${F}.session.idleTimeout`, text: `${G}.session.idle` },
        { term: `${F}.session.absoluteTimeout`, text: `${G}.session.absolute` },
        { term: `${F}.session.singleSessionOnly`, text: `${G}.session.single` },
        { term: `${F}.session.invalidateOnPasswordChange`, text: `${G}.session.onPassword` },
        { term: `${F}.session.invalidateOnRoleChange`, text: `${G}.session.onRole` },
      ],
    },
    {
      title: `${G}.mfa.title`,
      items: [
        { term: `${F}.mfa.enabled`, text: `${G}.mfa.enabled` },
        { term: `${F}.mfa.mandatory`, text: `${G}.mfa.mandatory` },
        { term: `${F}.mfa.emailOtpAllowed`, text: `${G}.mfa.email` },
        { term: `${F}.mfa.smsOtpAllowed`, text: `${G}.mfa.sms` },
        { term: `${F}.mfa.authenticatorAppAllowed`, text: `${G}.mfa.app` },
      ],
    },
    {
      title: `${G}.account.title`,
      items: [
        { term: `${F}.account.selfRegistrationAllowed`, text: `${G}.account.selfRegistration` },
        { term: `${F}.account.emailVerificationRequired`, text: `${G}.account.email` },
        { term: `${F}.account.phoneVerificationRequired`, text: `${G}.account.phone` },
        { term: `${F}.account.adminApprovalRequired`, text: `${G}.account.approval` },
      ],
    },
    {
      title: `${G}.activation.title`,
      items: [
        { term: `${F}.active`, text: `${G}.activation.active` },
        { term: 'accessManagement.securityPolicies.effectiveFrom', text: `${G}.activation.from` },
        { term: 'accessManagement.securityPolicies.effectiveUntil', text: `${G}.activation.until` },
      ],
    },
    { title: `${G}.example.title`, text: `${G}.example.text` },
  ],
};
