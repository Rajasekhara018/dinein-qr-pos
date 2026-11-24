import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ComponentRef,
  Inject,
  OnDestroy,
  ViewChild,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  HQ_IMAGE_CROP_DEFAULT_ASPECT_RATIO,
  HQ_IMAGE_CROP_DEFAULT_CONTENT_TYPE,
  HQ_IMAGE_CROP_DEFAULT_FILE_NAME,
  HQ_IMAGE_CROP_FORMAT,
  HQ_IMAGE_CROP_RESIZE_WIDTH,
} from './hq-image-crop-dialog.constants';
import { HQ_IMAGE_CROP_DIALOG_DEFAULT_TEXTS, HQ_IMAGE_CROP_DIALOG_TEXTS, HqImageCropDialogTexts } from './hq-image-crop-dialog.texts';
import { HQ_IMAGE_CROPPER_COMPONENT, HqImageCroppedEvent, HqImageCropperComponentLike } from './hq-image-cropper-component.token';

export interface HqImageCropDialogData {
  imageChangedEvent: Event;
  fileName: string;
  aspectRatio: number;
}

export interface HqImageCropDialogResult {
  finalImage: File;
  croppedImage: string;
}

/**
 * Crop dialog opened through MatDialog with HqImageCropDialogData; closes with
 * HqImageCropDialogResult or null. The cropper itself is the component class the app registers
 * under HQ_IMAGE_CROPPER_COMPONENT (ngx-image-cropper's ImageCropperComponent), created here
 * dynamically so the library has no dependency on that package.
 */
@Component({
  selector: 'hq-image-crop-dialog',
  standalone: false,
  templateUrl: './hq-image-crop-dialog.html',
  styleUrl: './hq-image-crop-dialog.scss',
})
export class HqImageCropDialog implements AfterViewInit, OnDestroy {
  readonly texts: HqImageCropDialogTexts =
    inject(HQ_IMAGE_CROP_DIALOG_TEXTS, { optional: true }) ?? HQ_IMAGE_CROP_DIALOG_DEFAULT_TEXTS;
  private readonly cropperType = inject(HQ_IMAGE_CROPPER_COMPONENT, { optional: true });

  @ViewChild('cropperHost', { read: ViewContainerRef, static: true }) private cropperHost!: ViewContainerRef;

  croppedImage = '';
  finalImage: File | null = null;

  private cropperRef: ComponentRef<HqImageCropperComponentLike> | null = null;
  private cropSubscription: { unsubscribe(): void } | null = null;

  constructor(
    public dialogRef: MatDialogRef<HqImageCropDialog>,
    @Inject(MAT_DIALOG_DATA) public data: HqImageCropDialogData,
    private cdr: ChangeDetectorRef,
  ) {}

  /** True when an app registered a cropper; otherwise the editor area stays empty. */
  get hasCropper(): boolean {
    return this.cropperType !== null;
  }

  ngAfterViewInit(): void {
    if (!this.cropperType) return;
    const ref = this.cropperHost.createComponent(this.cropperType);
    ref.setInput('imageChangedEvent', this.data.imageChangedEvent);
    ref.setInput('maintainAspectRatio', true);
    ref.setInput('aspectRatio', this.data.aspectRatio || HQ_IMAGE_CROP_DEFAULT_ASPECT_RATIO);
    ref.setInput('resizeToWidth', HQ_IMAGE_CROP_RESIZE_WIDTH);
    ref.setInput('format', HQ_IMAGE_CROP_FORMAT);
    this.cropSubscription = ref.instance.imageCropped.subscribe((event) => void this.imageCropped(event));
    ref.changeDetectorRef.detectChanges();
    this.cropperRef = ref;
  }

  ngOnDestroy(): void {
    this.cropSubscription?.unsubscribe();
    this.cropperRef?.destroy();
  }

  async imageCropped(event: HqImageCroppedEvent): Promise<void> {
    const blob = event.blob;
    if (!blob) return;
    try {
      const base64DataUrl = await this.blobToDataUrl(blob);
      const safeFileName = this.data?.fileName || HQ_IMAGE_CROP_DEFAULT_FILE_NAME;
      const contentType = blob.type || HQ_IMAGE_CROP_DEFAULT_CONTENT_TYPE;
      const file = new File([blob], safeFileName, { type: contentType });

      this.croppedImage = base64DataUrl;
      this.finalImage = file;
      // The cropper's (imageCropped) output fires outside a change-detection pass this component's
      // view participates in -- force a check so the preview and the "Use Image" button's disabled
      // state actually reflect the new value.
      this.cdr.detectChanges();
    } catch {
      this.finalImage = null;
      this.cdr.detectChanges();
    }
  }

  onSave(): void {
    if (!this.finalImage) {
      this.dialogRef.close(null);
      return;
    }
    const result: HqImageCropDialogResult = {
      finalImage: this.finalImage,
      croppedImage: this.croppedImage,
    };
    this.dialogRef.close(result);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  private blobToDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }
}
