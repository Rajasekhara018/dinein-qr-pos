import { InjectionToken, Type } from '@angular/core';

/** The slice of ngx-image-cropper's `ImageCroppedEvent` HqImageCropDialog reads. */
export interface HqImageCroppedEvent {
  blob?: Blob | null;
}

/**
 * The shape HqImageCropDialog expects of the cropper component it renders: an `imageCropped`
 * output (ngx-image-cropper's `ImageCropperComponent` satisfies it). The inputs set on it are
 * `imageChangedEvent`, `maintainAspectRatio`, `aspectRatio`, `resizeToWidth` and `format`.
 */
export interface HqImageCropperComponentLike {
  imageCropped: { subscribe(next: (event: HqImageCroppedEvent) => void): { unsubscribe(): void } };
}

/**
 * The cropper component class HqImageCropDialog creates in its editor area. The library does not
 * depend on ngx-image-cropper; an app that bundles it provides
 * `{ provide: HQ_IMAGE_CROPPER_COMPONENT, useValue: ImageCropperComponent }`. Without it the
 * dialog renders no editor and "Use image" stays disabled.
 */
export const HQ_IMAGE_CROPPER_COMPONENT = new InjectionToken<Type<HqImageCropperComponentLike>>('HQ_IMAGE_CROPPER_COMPONENT');
