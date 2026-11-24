import { InjectionToken } from '@angular/core';

/**
 * The words the upload components (hq-file-drop, hq-doc-upload, hq-multi-doc-upload) show. The consuming app
 * provides HQ_UPLOAD_TEXTS with its own translated text, usually through {@link hqUploadTextsFrom}; each member
 * is called when the text is needed, so a language switch is picked up. Without a provider the English defaults
 * below are used.
 */
export interface HqUploadTexts {
  dropOne(): string;
  dropMany(): string;
  release(): string;
  chooseOne(): string;
  chooseMany(): string;
  uploading(): string;
  limits(hints: string, maxSizeMb: number): string;
  limitsEach(hints: string, maxSizeMb: number): string;
  maxFiles(max: number): string;
  maxSize(maxSizeMb: number): string;
  noDocument(): string;
  noDocuments(): string;
  change(): string;
  /** The button that opens an uploaded document in the viewer. */
  view(): string;
  /** The view button's accessible name for one document of a list. */
  viewNamed(name: string): string;
  upload(): string;
  remove(): string;
  removeDocument(): string;
  removeNamed(name: string): string;
  editImage(): string;
  fileName(): string;
  fileNameInvalid(): string;
  replaceConfirm(): string;
  deleteConfirm(): string;
  deleteNamedConfirm(name: string): string;
  invalidType(hints: string): string;
  invalidTypeNamed(name: string, hints: string): string;
  invalidImage(): string;
  tooSmall(): string;
  tooSmallNamed(name: string): string;
  tooLarge(maxSizeMb: number): string;
  tooLargeNamed(name: string, maxSizeMb: number): string;
  alreadyAttached(name: string): string;
  tooMany(max: number): string;
  noOwner(): string;
  noOwnerMany(): string;
  uploadFailed(): string;
  uploadFailedNamed(name: string): string;
  openFailed(): string;
  deleteFailed(): string;
}

export const HQ_UPLOAD_TEXTS = new InjectionToken<HqUploadTexts>('HQ_UPLOAD_TEXTS');

export const HQ_UPLOAD_DEFAULT_TEXTS: HqUploadTexts = {
  dropOne: () => 'Drop a file here, or',
  dropMany: () => 'Drop files here, or',
  release: () => 'Release to add',
  chooseOne: () => 'Choose a file',
  chooseMany: () => 'Choose files',
  uploading: () => 'Uploading…',
  limits: (hints, maxSizeMb) => `${hints} · up to ${maxSizeMb} MB`,
  limitsEach: (hints, maxSizeMb) => `${hints} · up to ${maxSizeMb} MB each`,
  maxFiles: (max) => `at most ${max} files`,
  maxSize: (maxSizeMb) => `Up to ${maxSizeMb} MB`,
  noDocument: () => 'No document',
  noDocuments: () => 'No documents',
  change: () => 'Change',
  view: () => 'View',
  viewNamed: (name) => `View ${name}`,
  upload: () => 'Upload',
  remove: () => 'Remove',
  removeDocument: () => 'Remove document',
  removeNamed: (name) => `Remove ${name}`,
  editImage: () => 'Edit image',
  fileName: () => 'File name (optional)',
  fileNameInvalid: () => 'Letters, digits, - and _ only',
  replaceConfirm: () => 'Do you want to replace the current document?',
  deleteConfirm: () => 'Delete this document permanently?',
  deleteNamedConfirm: (name) => `Delete ${name} permanently?`,
  invalidType: (hints) => `Please upload ${hints} files.`,
  invalidTypeNamed: (name, hints) => `${name}: please upload ${hints} files.`,
  invalidImage: () => 'Please upload a JPEG, PNG or WebP image.',
  tooSmall: () => 'File is too small (minimum 50 bytes).',
  tooSmallNamed: (name) => `${name} is too small (minimum 50 bytes).`,
  tooLarge: (maxSizeMb) => `File exceeds the ${maxSizeMb} MB limit.`,
  tooLargeNamed: (name, maxSizeMb) => `${name} exceeds the ${maxSizeMb} MB limit.`,
  alreadyAttached: (name) => `${name} is already attached.`,
  tooMany: (max) => `At most ${max} documents can be attached.`,
  noOwner: () => 'No owner available for this document.',
  noOwnerMany: () => 'No owner available for these documents.',
  uploadFailed: () => 'Upload failed.',
  uploadFailedNamed: (name) => `Upload of ${name} failed.`,
  openFailed: () => 'Unable to open the document.',
  deleteFailed: () => 'Unable to delete the document.',
};
