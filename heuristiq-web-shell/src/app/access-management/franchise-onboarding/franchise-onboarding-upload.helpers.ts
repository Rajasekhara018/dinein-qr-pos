import { DMS_FILE_RULE_MESSAGES, ONBOARDING_UPLOAD_MAX_SIZE_MB, ONBOARDING_UPLOAD_TYPES, OnboardingUploadProblem } from './franchise-onboarding-upload.constants';

/** The file's extension in lower case with the dot ('' when it has none), so `.JPEG` and `.jpeg` are the same. */
export function fileExtension(name: string): string {
  const dot = (name ?? '').lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot).toLowerCase();
}

/** The content type DMS expects for the file's extension, or null when the extension is not allowed. */
export function onboardingContentType(name: string): string | null {
  return ONBOARDING_UPLOAD_TYPES[fileExtension(name)] ?? null;
}

/** Why the file cannot be uploaded (wrong type, too large, empty), or null when it is fine. Decided by extension, any case. */
export function onboardingUploadProblem(file: File, maxSizeMB = ONBOARDING_UPLOAD_MAX_SIZE_MB): OnboardingUploadProblem | null {
  if (!onboardingContentType(file.name)) return 'invalidType';
  if (file.size === 0) return 'empty';
  if (file.size > maxSizeMB * 1024 * 1024) return 'tooLarge';
  return null;
}

/**
 * The file with the content type of its extension. Browsers send JPEGs as `image/jpeg`, `image/jpg`, `image/pjpeg` or, on
 * Windows machines without the type registered, no type at all; DMS accepts only `image/jpeg`. Same file when already right.
 */
export function normalizedOnboardingFile(file: File): File {
  const type = onboardingContentType(file.name);
  if (!type || file.type === type) return file;
  return new File([file], file.name, { type, lastModified: file.lastModified });
}

/** True when a DMS error says the file broke its type or size rules (rather than e.g. the service being down). */
export function isDmsFileRuleRejection(err: unknown): boolean {
  const message = (err as { error?: { message?: unknown } } | null)?.error?.message;
  return typeof message === 'string' && DMS_FILE_RULE_MESSAGES.includes(message);
}
