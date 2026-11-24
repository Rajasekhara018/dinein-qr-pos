import { InjectionToken } from '@angular/core';

/**
 * The words HqImageCropDialog shows. The consuming app provides HQ_IMAGE_CROP_DIALOG_TEXTS with
 * its own translated text (each member is called when needed, so a language switch is picked up);
 * without it the English defaults below are used.
 */
export interface HqImageCropDialogTexts {
  eyebrow(): string;
  title(): string;
  subtitle(): string;
  /** Accessible label of the close (x) button. */
  close(): string;
  preview(): string;
  previewAlt(): string;
  previewNote(): string;
  tipDrag(): string;
  tipCenter(): string;
  cancel(): string;
  useImage(): string;
}

export const HQ_IMAGE_CROP_DIALOG_TEXTS = new InjectionToken<HqImageCropDialogTexts>('HQ_IMAGE_CROP_DIALOG_TEXTS');

export const HQ_IMAGE_CROP_DIALOG_DEFAULT_TEXTS: HqImageCropDialogTexts = {
  eyebrow: () => 'Image Upload',
  title: () => 'Adjust Image',
  subtitle: () => 'Move and resize to frame your image.',
  close: () => 'Close crop popup',
  preview: () => 'Preview',
  previewAlt: () => 'Cropped image preview',
  previewNote: () => 'This is how your image will be used.',
  tipDrag: () => 'Drag image',
  tipCenter: () => 'Keep subject centered',
  cancel: () => 'Cancel',
  useImage: () => 'Use Image',
};
