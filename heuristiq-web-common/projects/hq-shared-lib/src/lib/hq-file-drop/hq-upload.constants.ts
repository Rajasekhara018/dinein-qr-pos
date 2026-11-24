import { HqUploadTexts } from './hq-upload.texts';

/** Smallest file DMS accepts; anything smaller is almost certainly empty or broken. */
export const HQ_DMS_MIN_FILE_BYTES = 50;

/** Letters, digits, '_' and '-': the optional custom file name of hq-multi-doc-upload. */
export const HQ_CUSTOM_FILE_NAME_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

/** Image types an image-preview upload (profile photo, logo) accepts. */
export const HQ_PREVIEW_IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * The translation parameter name of each argument of each HqUploadTexts member, in argument order. A member
 * missing here takes no parameters. Used by hqUploadTextsFrom to call the app's translate lookup.
 */
export const HQ_UPLOAD_TEXT_PARAMS: Partial<Record<keyof HqUploadTexts, readonly string[]>> = {
  limits: ['hints', 'max'],
  limitsEach: ['hints', 'max'],
  maxFiles: ['max'],
  maxSize: ['max'],
  removeNamed: ['name'],
  viewNamed: ['name'],
  deleteNamedConfirm: ['name'],
  invalidType: ['hints'],
  invalidTypeNamed: ['name', 'hints'],
  tooSmallNamed: ['name'],
  tooLarge: ['max'],
  tooLargeNamed: ['name', 'max'],
  alreadyAttached: ['name'],
  tooMany: ['max'],
  uploadFailedNamed: ['name'],
};
