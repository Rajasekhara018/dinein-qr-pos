import { HqStatusTone } from 'hq-shared-lib';
/** Whose onboarding a page shows: any franchise (administrators) or one the signed-in person owns. */
export type OnboardingSubject = 'franchise' | 'myFranchise';

/** The last segment of the two onboarding routes (`franchises/:id/onboarding`, `my-franchises/:id/onboarding`). */
export const ONBOARDING_ROUTE = 'onboarding';

export const FRANCHISE_ONBOARDING_PERMISSION = 'view_franchise_onboarding';
export const MY_FRANCHISE_ONBOARDING_PERMISSION = 'view_my_franchise_onboarding';
export const FRANCHISE_ONBOARDING_SCREEN_KEY = 'access_management.franchise_onboarding';
export const MY_FRANCHISE_ONBOARDING_SCREEN_KEY = 'access_management.my_franchise_onboarding';

/** The privileges behind each action, per kind of page (null: the action is not offered there). */
export interface OnboardingPrivileges {
  read: string;
  addDocument: string;
  withdrawDocument: string | null;
  reviewDocument: string | null;
  saveBank: string;
  reviewBank: string | null;
  saveAgreement: string | null;
  activate: string | null;
}

export interface OnboardingSubjectConfig {
  privileges: OnboardingPrivileges;
  backRoute: string;
  listKey: string;
  legalProfileRoute: string;
}

export const ONBOARDING_SUBJECTS: Readonly<Record<OnboardingSubject, OnboardingSubjectConfig>> = {
  franchise: {
    privileges: {
      read: 'FRANCHISE_ONBOARDING_READ',
      addDocument: 'FRANCHISE_KYC_DOCUMENT_CREATE',
      withdrawDocument: null,
      reviewDocument: 'FRANCHISE_KYC_DOCUMENT_REVIEW_UPDATE',
      saveBank: 'FRANCHISE_BANK_ACCOUNT_UPDATE',
      reviewBank: 'FRANCHISE_BANK_ACCOUNT_REVIEW_UPDATE',
      saveAgreement: 'FRANCHISE_AGREEMENT_UPDATE',
      activate: 'FRANCHISE_ACTIVATION_UPDATE',
    },
    backRoute: 'franchises',
    listKey: 'nav.items.franchises',
    legalProfileRoute: 'franchises',
  },
  myFranchise: {
    privileges: {
      read: 'FRANCHISE_MINE_ONBOARDING_READ',
      addDocument: 'FRANCHISE_MINE_KYC_DOCUMENT_CREATE',
      withdrawDocument: 'FRANCHISE_MINE_KYC_DOCUMENT_DELETE',
      reviewDocument: null,
      saveBank: 'FRANCHISE_MINE_BANK_ACCOUNT_UPDATE',
      reviewBank: null,
      saveAgreement: null,
      activate: null,
    },
    backRoute: 'my-franchises',
    listKey: 'nav.items.myFranchises',
    legalProfileRoute: 'my-franchises',
  },
};

/** The checklist items, in the order they are shown and listed as missing. */
export const ONBOARDING_CHECKLIST_ITEMS = ['legalProfile', 'kyc', 'bankAccount', 'agreement'] as const;

export const KYC_DOCUMENT_TYPES: readonly string[] = [
  'IDENTITY_PROOF', 'ADDRESS_PROOF', 'BUSINESS_REGISTRATION', 'TAX_CERTIFICATE', 'PHOTO', 'OTHER',
];

export const AGREEMENT_RENEWALS: readonly string[] = ['NONE', 'AUTO', 'BY_NOTICE'];

/** DMS category of every onboarding file; the franchise id is the DMS owner. It is also the category of the franchise document types. */
export const ONBOARDING_DMS_CATEGORY = 'franchise-onboarding';

/**
 * The DMS document type (Document types screen) each onboarding upload is checked against: its formats, size, scan and retention.
 * A KYC document of type OTHER names none, so the organization's default rules apply.
 */
export const KYC_DMS_DOCUMENT_TYPES: Readonly<Record<string, string>> = {
  IDENTITY_PROOF: 'FRANCHISE_ID_PROOF',
  ADDRESS_PROOF: 'FRANCHISE_ADDRESS_PROOF',
  BUSINESS_REGISTRATION: 'FRANCHISE_BUSINESS_REGISTRATION',
  TAX_CERTIFICATE: 'FRANCHISE_TAX_CERTIFICATE',
  PHOTO: 'FRANCHISE_PHOTO',
};
export const BANK_PROOF_DMS_DOCUMENT_TYPE = 'FRANCHISE_BANK_PROOF';
export const AGREEMENT_DMS_DOCUMENT_TYPE = 'FRANCHISE_AGREEMENT';

/** Countries where a bank account is given as an IBAN only (mirrors OnboardingConstants.IBAN_REQUIRED_COUNTRIES). */
export const IBAN_REQUIRED_COUNTRIES: readonly string[] = ['AE', 'SA'];

/**
 * Countries whose bank accounts may be given by IBAN (mirrors OnboardingConstants.IBAN_COUNTRIES): the service refuses an IBAN
 * for any other country.
 */
export const IBAN_COUNTRIES: readonly string[] = [
  'AE', 'SA', 'GB',
  'AT', 'BE', 'BG', 'HR', 'CY', 'CZ', 'DK', 'EE', 'FI', 'FR', 'DE', 'GR', 'HU', 'IE', 'IT', 'LV', 'LT', 'LU', 'MT', 'NL',
  'PL', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE',
  'IS', 'LI', 'NO',
];

/** When a routing code must be given: always (IFSC, ABA), unless an IBAN is given (sort code), or never (SWIFT/BIC). */
export type RoutingRequirement = 'always' | 'unlessIban' | 'never';

/** The bank routing code a country uses, its pattern (after removing spaces and hyphens) and when it is required. */
export interface RoutingRule {
  type: 'IFSC' | 'ABA' | 'SORT_CODE' | 'SWIFT';
  pattern: RegExp;
  required: RoutingRequirement;
}

/** Mirrors OnboardingRules.routingType / routingCode of the franchise service. */
export const ROUTING_RULES: Readonly<Record<string, RoutingRule>> = {
  IN: { type: 'IFSC', pattern: /^[A-Z]{4}0[A-Z0-9]{6}$/, required: 'always' },
  US: { type: 'ABA', pattern: /^[0-9]{9}$/, required: 'always' },
  GB: { type: 'SORT_CODE', pattern: /^[0-9]{6}$/, required: 'unlessIban' },
};

export const SWIFT_RULE: RoutingRule = { type: 'SWIFT', pattern: /^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$/, required: 'never' };

/** A yyyy-mm-dd date, as a date input gives it and the service parses it. */
export const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Prefix of the inline field messages of this page. */
export const ONBOARDING_VALIDATION_PREFIX = 'franchiseOnboarding.validation.';

/**
 * The form field a server validation error belongs to, by the field name the service reports (OnboardingConstants.FIELD_*).
 * A name missing here is the form field of the same name; file names and ids map to the upload they come from.
 */
export const ONBOARDING_SERVER_FIELDS: Readonly<Record<string, string>> = {
  dmsDocumentId: 'file',
  fileName: 'file',
  contentType: 'file',
  proofFileName: 'proofDmsDocumentId',
  signedCopyFileName: 'signedCopyDmsDocumentId',
  'securityDeposit.value': 'securityDeposit',
  'securityDeposit.code': 'securityDeposit',
};
export const IBAN_PATTERN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/;
export const ACCOUNT_NUMBER_PATTERN = /^[A-Z0-9]{4,34}$/;
export const DOCUMENT_NUMBER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 ./-]*$/;

export const ONBOARDING_LIMITS = { documentNumber: 50, holderName: 200, bankName: 100, reason: 500, agreementNumber: 50 } as const;
/** Input lengths of the IBAN / account number (34 characters plus the spaces people type) and of the routing code. */
export const BANK_INPUT_MAX = 40;
export const ROUTING_INPUT_MAX = 20;

/** Prefix of the message keys the service returns (translated under franchiseOnboarding.errors.*). */
export const ONBOARDING_ERROR_PREFIX = 'franchiseOnboarding.';

/** Chip colour per status. */
export const ONBOARDING_STATUS_TONE: Readonly<Record<string, HqStatusTone>> = {
  DONE: 'success', VERIFIED: 'success', ONBOARDED: 'success', SUBMITTED: 'warning', ONBOARDING: 'warning', REJECTED: 'danger', MISSING: 'neutral',
};
