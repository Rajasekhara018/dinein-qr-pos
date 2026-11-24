import { DocumentFormatKey, DocumentTypeLanguage, VerificationMethod, VerificationProviderKey } from './document-type.models';

/** Stable screen keys for the audit trail (never rename or translate once released). Lower-case: the audit service's key pattern forbids capitals. */
export const DOCUMENT_MANAGEMENT_SCREEN_KEYS = {
  documentTypes: 'document_management.document_types',
  documentTypeCreate: 'document_management.document_type_create',
  documentTypeEdit: 'document_management.document_type_edit',
} as const;

/** Gateway path (below environment.apiUrl) of the document types API. */
export const DOCUMENT_TYPES_PATH = '/v1/dms/document-types';

/** Where approvals happen (maker-checker). */
export const CHANGE_REQUESTS_ROUTE = '/admin/change-requests';
export const DOCUMENT_TYPES_ROUTE = '/document-management/document-types';

/** Screen permission key (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts). */
export const DOCUMENT_TYPES_PERMISSION = 'view_document_types';

export const DOCUMENT_TYPES_DEFAULT_PAGE_SIZE = 20;
/** The API caps a page at 100. */
export const DOCUMENT_TYPES_MAX_PAGE_SIZE = 100;
export const DOCUMENT_TYPES_PAGE_SIZE_OPTIONS: number[] = [10, 20, 50, 100];

export const DOCUMENT_TYPE_LIMITS = {
  codeMin: 3,
  codeMax: 60,
  categoryMax: 60,
  nameMax: 120,
  maxFileSizeMbMin: 1,
  maxFileSizeMbMax: 50,
  expiryYearsMin: 1,
  expiryYearsMax: 50,
  retentionDaysMin: 1,
  retentionDaysMax: 36500,
} as const;

export const DOCUMENT_TYPE_CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export const DEFAULT_MAX_FILE_SIZE_MB = 5;

/** Days in a year / month when a retention period is shown in whole years or months. */
export const RETENTION_DAYS_PER_YEAR = 365;
export const RETENTION_DAYS_PER_MONTH = 30;

export const DOCUMENT_TYPE_LANGUAGES: readonly DocumentTypeLanguage[] = ['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar'];

/** Language names are shown in their own script, so they are not translated. */
export const DOCUMENT_TYPE_LANGUAGE_NAMES: Readonly<Record<DocumentTypeLanguage, string>> = {
  en: 'English',
  hi: 'हिन्दी',
  ta: 'தமிழ்',
  te: 'తెలుగు',
  kn: 'ಕನ್ನಡ',
  ml: 'മലയാളം',
  ar: 'العربية',
};

export interface DocumentFormatDef {
  key: DocumentFormatKey;
  /** i18n key of the friendly label. */
  labelKey: string;
  extensions: readonly string[];
}

export const DOCUMENT_FORMATS: readonly DocumentFormatDef[] = [
  { key: 'PDF', labelKey: 'documentManagement.formats.PDF', extensions: ['.pdf'] },
  { key: 'JPG', labelKey: 'documentManagement.formats.JPG', extensions: ['.jpg', '.jpeg'] },
  { key: 'PNG', labelKey: 'documentManagement.formats.PNG', extensions: ['.png'] },
  { key: 'WEBP', labelKey: 'documentManagement.formats.WEBP', extensions: ['.webp'] },
  { key: 'DOC', labelKey: 'documentManagement.formats.DOC', extensions: ['.doc'] },
  { key: 'DOCX', labelKey: 'documentManagement.formats.DOCX', extensions: ['.docx'] },
  { key: 'XLS', labelKey: 'documentManagement.formats.XLS', extensions: ['.xls'] },
  { key: 'XLSX', labelKey: 'documentManagement.formats.XLSX', extensions: ['.xlsx'] },
  { key: 'CSV', labelKey: 'documentManagement.formats.CSV', extensions: ['.csv'] },
  { key: 'TXT', labelKey: 'documentManagement.formats.TXT', extensions: ['.txt'] },
];

/** HTTP statuses the editor reacts to. */
export const HTTP_STATUS = { badRequest: 400, conflict: 409, unavailable: 503 } as const;

export const BYTES_PER_MB = 1024 * 1024;

/** The verification methods a type can name, in the order offered (labels: documentManagement.verification.methods.<METHOD>). */
export const VERIFICATION_METHODS: readonly VerificationMethod[] = [
  'MANUAL_REVIEW', 'AADHAAR_DIGILOCKER', 'PAN_VERIFICATION', 'DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN', 'BANK_PENNY_DROP',
];
export const VERIFICATION_METHOD_KEY_PREFIX = 'documentManagement.verification.methods.';
export const NO_VERIFICATION: VerificationMethod = 'NONE';

/** Candidate choices, not active vendor connections. One provider is stored per document type. */
export const VERIFICATION_PROVIDERS: readonly { key: VerificationProviderKey; label: string; methods: readonly VerificationMethod[] }[] = [
  { key: 'SIGNZY', label: 'Signzy', methods: ['AADHAAR_DIGILOCKER', 'PAN_VERIFICATION', 'DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN'] },
  { key: 'IDFY', label: 'IDfy', methods: ['PAN_VERIFICATION', 'DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN'] },
  { key: 'KARZA', label: 'Karza / Perfios', methods: ['AADHAAR_DIGILOCKER', 'PAN_VERIFICATION', 'DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN', 'BANK_PENNY_DROP'] },
  { key: 'EKO', label: 'Eko', methods: ['DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN'] },
  { key: 'EKYCNOW', label: 'Message Central eKYCNow', methods: ['AADHAAR_DIGILOCKER', 'PAN_VERIFICATION'] },
  { key: 'CASHFREE', label: 'Cashfree', methods: ['AADHAAR_DIGILOCKER', 'PAN_VERIFICATION', 'DRIVING_LICENCE_SARATHI', 'VEHICLE_RC_VAHAN', 'BANK_PENNY_DROP'] },
  { key: 'RAZORPAY', label: 'Razorpay', methods: ['BANK_PENNY_DROP'] },
];
