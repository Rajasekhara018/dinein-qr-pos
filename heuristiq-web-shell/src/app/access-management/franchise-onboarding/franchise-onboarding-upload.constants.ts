/**
 * The one set of file rules every onboarding upload uses (KYC documents, proof of bank account, signed agreement, agent
 * KYC). It matches the franchise document types of DMS (PDF, JPG and PNG; 5 MB is the smallest of their size limits), so a
 * file the page accepts is not refused afterwards by DMS.
 */

/** Allowed extension (lower case, with the dot) and the content type DMS expects for it. */
export const ONBOARDING_UPLOAD_TYPES: Readonly<Record<string, string>> = {
  '.pdf': 'application/pdf',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
};

/** The file picker's accept list: the extensions plus their content types. */
export const ONBOARDING_UPLOAD_ACCEPT = [
  ...Object.keys(ONBOARDING_UPLOAD_TYPES),
  ...new Set(Object.values(ONBOARDING_UPLOAD_TYPES)),
].join(',');

/** Largest onboarding file, in MB. */
export const ONBOARDING_UPLOAD_MAX_SIZE_MB = 5;

/** Translation keys of the upload messages (franchiseOnboarding.upload.*). */
export const ONBOARDING_UPLOAD_KEYS = {
  hints: 'franchiseOnboarding.upload.hints',
  invalidType: 'franchiseOnboarding.upload.invalidType',
  tooLarge: 'franchiseOnboarding.upload.tooLarge',
  empty: 'franchiseOnboarding.upload.empty',
  rejected: 'franchiseOnboarding.upload.rejected',
} as const;

/** Why an onboarding file is refused before it is sent. */
export type OnboardingUploadProblem = 'invalidType' | 'tooLarge' | 'empty';

/** DMS messages for a file its rules refuse (type, extension, size); shown as the translated rejection instead. */
export const DMS_FILE_RULE_MESSAGES: readonly string[] = [
  'Document content type is not allowed',
  'Document file extension is not allowed',
  'Document exceeds the configured maximum file size',
];
