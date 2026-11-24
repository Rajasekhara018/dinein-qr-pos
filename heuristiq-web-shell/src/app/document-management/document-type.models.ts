/** Typed models for the Document types screen (Rider_DMS /v1/dms/document-types). */

export type DocumentFormatKey = 'PDF' | 'JPG' | 'PNG' | 'WEBP' | 'DOC' | 'DOCX' | 'XLS' | 'XLSX' | 'CSV' | 'TXT';

export type DocumentTypeLanguage = 'en' | 'hi' | 'ta' | 'te' | 'kn' | 'ml' | 'ar';

/** How a document of this type is verified beyond the file checks (configuration only for now: no provider is called yet). */
export type VerificationMethod =
  | 'NONE' | 'MANUAL_REVIEW' | 'AADHAAR_DIGILOCKER' | 'PAN_VERIFICATION' | 'DRIVING_LICENCE_SARATHI' | 'VEHICLE_RC_VAHAN' | 'BANK_PENNY_DROP';

export type VerificationProviderKey = 'SIGNZY' | 'IDFY' | 'KARZA' | 'EKO' | 'EKYCNOW' | 'CASHFREE' | 'RAZORPAY';

/** The document's name per language; only English is required. */
export type DocumentTypeNames = Record<DocumentTypeLanguage, string>;

/** Body of POST / PUT /v1/dms/document-types. */
export interface DocumentTypeRequest {
  code: string;
  category: string;
  flow: string | null;
  names: Partial<DocumentTypeNames>;
  allowedFormats: DocumentFormatKey[];
  maxFileSizeMb: number;
  required: boolean;
  expiryYears: number | null;
  /** Days to keep uploaded documents; null = platform default. */
  retentionDays: number | null;
  /** When true, uploads are rejected if virus scanning cannot give a verdict. */
  scanRequired: boolean;
  /** Whether a document of this type must be verified (by the method below) before it is accepted. */
  verificationRequired: boolean;
  /** NONE unless verification is required. */
  verificationMethod: VerificationMethod;
  verificationProvider?: VerificationProviderKey | null;
  active: boolean;
  version: number;
}

export interface DocumentTypeResponse extends Omit<DocumentTypeRequest, 'retentionDays' | 'scanRequired' | 'verificationRequired' | 'verificationMethod'> {
  /** Missing is read as false / NONE (types saved before verification existed). */
  verificationRequired?: boolean;
  verificationMethod?: VerificationMethod;
  /** Missing is read as null (platform default). */
  retentionDays?: number | null;
  /** Missing is read as true. */
  scanRequired?: boolean;
  id: string;
  tenantId?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
  lastChangeRequestId?: string | null;
  summary?: string;
}

export interface DocumentTypePage {
  content: DocumentTypeResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DocumentTypeFilter {
  category: string;
  flow: string;
  /** '' = any, 'true' / 'false'. */
  active: '' | 'true' | 'false';
  page: number;
  size: number;
}

/** 202 answer of a create / update: the change waits for another person's approval. */
export interface ChangeRequestAck {
  changeRequestId: string | null;
  status: string;
}

/** What the editor binds to. Numbers are null while the field is empty. */
export interface DocumentTypeFormModel {
  code: string;
  category: string;
  flow: string;
  names: DocumentTypeNames;
  allowedFormats: DocumentFormatKey[];
  maxFileSizeMb: number | null;
  required: boolean;
  expiryYears: number | null;
  retentionDays: number | null;
  scanRequired: boolean;
  verificationRequired: boolean;
  verificationMethod: VerificationMethod;
  verificationProvider?: VerificationProviderKey | null;
  active: boolean;
  version: number;
}

/** Translation keys of the problems found in a form, by field. */
export type DocumentTypeFormErrors = Partial<Record<'code' | 'category' | 'nameEn' | 'formats' | 'maxFileSizeMb' | 'expiryYears' | 'retentionDays' | 'verificationMethod' | 'verificationProvider', string>>;

export interface FileCheckReason {
  code: string;
  message: string;
}

export interface FileCheckRule {
  maxFileSizeMb: number;
  allowedFormats: DocumentFormatKey[];
  allowedExtensions: string[];
}

export interface FileCheckResult {
  accepted: boolean;
  reasons: FileCheckReason[];
  rule?: FileCheckRule;
}

/** Translate function shape used by pure helpers (TranslateService.instant). */
export type TranslateFn = (key: string, params?: Record<string, unknown>) => string;
