import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HqImageCropDialog, HqImageCropDialogData, HqImageCropDialogResult } from './hq-image-crop-dialog';
import { HQ_IMAGE_CROP_DIALOG_DEFAULT_TEXTS, HQ_IMAGE_CROP_DIALOG_TEXTS, HqImageCropDialogTexts } from './hq-image-crop-dialog.texts';
import { HQ_IMAGE_CROPPER_COMPONENT, HqImageCroppedEvent } from './hq-image-cropper-component.token';

/** Stands in for ngx-image-cropper's ImageCropperComponent: records its inputs and exposes the output. */
@Component({ selector: 'fake-cropper', template: '<div class="fake-cropper">cropper</div>' })
class FakeCropper implements OnDestroy {
  static instances: FakeCropper[] = [];
  static destroyed = 0;
  @Input() imageChangedEvent: Event | null = null;
  @Input() maintainAspectRatio = false;
  @Input() aspectRatio = 0;
  @Input() resizeToWidth = 0;
  @Input() format = '';
  @Output() imageCropped = new EventEmitter<HqImageCroppedEvent>();
  constructor() {
    FakeCropper.instances.push(this);
  }
  ngOnDestroy(): void {
    FakeCropper.destroyed++;
  }
}

interface Setup {
  fixture: ComponentFixture<HqImageCropDialog>;
  component: HqImageCropDialog;
  el: HTMLElement;
  close: ReturnType<typeof vi.fn>;
}

const EVENT = new Event('change');

function setup(options: { cropper?: boolean; data?: Partial<HqImageCropDialogData>; texts?: HqImageCropDialogTexts } = {}): Setup {
  const close = vi.fn();
  const data: HqImageCropDialogData = { imageChangedEvent: EVENT, fileName: 'avatar.png', aspectRatio: 4 / 3, ...options.data };
  TestBed.configureTestingModule({
    imports: [CommonModule],
    declarations: [HqImageCropDialog],
    providers: [
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
      ...(options.cropper === false ? [] : [{ provide: HQ_IMAGE_CROPPER_COMPONENT, useValue: FakeCropper }]),
      ...(options.texts ? [{ provide: HQ_IMAGE_CROP_DIALOG_TEXTS, useValue: options.texts }] : []),
    ],
  });
  const fixture = TestBed.createComponent(HqImageCropDialog);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement, close };
}

function saveButton(el: HTMLElement): HTMLButtonElement {
  return el.querySelector('.crop-btn--save') as HTMLButtonElement;
}

/**
 * Emits a crop from the fake cropper. The dialog reads the blob with FileReader asynchronously, so
 * when `settled` is given wait (condition-based, not a fixed delay) until it holds.
 */
async function emitCrop(blob: Blob | null | undefined, settled?: () => boolean): Promise<void> {
  const cropper = FakeCropper.instances.at(-1)!;
  cropper.imageCropped.emit({ blob });
  if (settled) {
    await vi.waitFor(() => {
      if (!settled()) throw new Error('crop not processed yet');
    }, { timeout: 10000, interval: 10 });
  }
}

describe('HqImageCropDialog', () => {
  beforeEach(() => {
    FakeCropper.instances = [];
    FakeCropper.destroyed = 0;
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders default texts, an empty preview and a disabled save button', () => {
    const { el } = setup();
    expect(el.querySelector('.crop-dialog__eyebrow')?.textContent).toBe('Image Upload');
    expect(el.querySelector('.crop-dialog__title')?.textContent).toBe('Adjust Image');
    expect(el.querySelector('.crop-dialog__subtitle')?.textContent).toBe('Move and resize to frame your image.');
    expect(el.querySelector('.crop-dialog__close')?.getAttribute('aria-label')).toBe('Close crop popup');
    expect(el.querySelector('.crop-dialog__preview-label')?.textContent).toBe('Preview');
    expect(el.querySelector('.crop-dialog__preview-note')?.textContent).toBe('This is how your image will be used.');
    expect(el.querySelector('.crop-dialog__tips')?.textContent).toContain('Drag image');
    expect(el.querySelector('.crop-dialog__tips')?.textContent).toContain('Keep subject centered');
    expect(el.querySelector('.crop-btn--cancel')?.textContent).toBe('Cancel');
    expect(saveButton(el).textContent).toBe('Use Image');
    expect(saveButton(el).disabled).toBe(true);
    expect(el.querySelector('.crop-dialog__preview-avatar img')).toBeNull();
    expect(el.querySelector('.crop-dialog__preview-avatar .bi-image')).not.toBeNull();
  });

  it('creates the registered cropper in the editor with the expected inputs', () => {
    const { el, component } = setup();
    expect(component.hasCropper).toBe(true);
    expect(el.querySelector('.crop-dialog__editor .fake-cropper')).not.toBeNull();
    const cropper = FakeCropper.instances[0];
    expect(cropper.imageChangedEvent).toBe(EVENT);
    expect(cropper.maintainAspectRatio).toBe(true);
    expect(cropper.aspectRatio).toBeCloseTo(4 / 3);
    expect(cropper.resizeToWidth).toBe(420);
    expect(cropper.format).toBe('png');
  });

  it('falls back to a square aspect ratio when none is given', () => {
    setup({ data: { aspectRatio: 0 } });
    expect(FakeCropper.instances[0].aspectRatio).toBe(1);
  });

  it('renders no editor when no cropper is registered, and saving closes with null', () => {
    const { el, component, close } = setup({ cropper: false });
    expect(component.hasCropper).toBe(false);
    expect(el.querySelector('.fake-cropper')).toBeNull();
    expect(saveButton(el).disabled).toBe(true);
    component.onSave();
    expect(close).toHaveBeenCalledWith(null);
    expect(() => component.ngOnDestroy()).not.toThrow();
  });

  it('on a crop shows the preview, enables save and closes with the file and data URL', async () => {
    const { el, close, component } = setup();
    await emitCrop(new Blob(['abc'], { type: 'image/jpeg' }), () => component.finalImage !== null);
    const img = el.querySelector('.crop-dialog__preview-avatar img') as HTMLImageElement;
    expect(img).not.toBeNull();
    expect(img.getAttribute('src')).toMatch(/^data:image\/jpeg;base64,/);
    expect(img.alt).toBe('Cropped image preview');
    expect(saveButton(el).disabled).toBe(false);

    saveButton(el).click();
    const result = close.mock.calls[0][0] as HqImageCropDialogResult;
    expect(result.croppedImage).toBe(img.getAttribute('src'));
    expect(result.finalImage).toBeInstanceOf(File);
    expect(result.finalImage.name).toBe('avatar.png');
    expect(result.finalImage.type).toBe('image/jpeg');
  });

  it('defaults the file name and content type when missing', async () => {
    const { component } = setup({ data: { fileName: '' } });
    await emitCrop(new Blob(['abc']), () => component.finalImage !== null);
    expect(component.finalImage?.name).toBe('image.png');
    expect(component.finalImage?.type).toBe('image/png');
  });

  it('ignores a crop event without a blob', async () => {
    const { el, component } = setup();
    await emitCrop(null);
    await emitCrop(undefined);
    expect(component.finalImage).toBeNull();
    expect(component.croppedImage).toBe('');
    expect(saveButton(el).disabled).toBe(true);
  });

  it('clears the final image when reading the blob fails', async () => {
    const { el, component } = setup();
    await emitCrop(new Blob(['abc'], { type: 'image/png' }), () => component.finalImage !== null);
    expect(component.finalImage).not.toBeNull();

    class FailingReader {
      onload: (() => void) | null = null;
      onerror: ((e: unknown) => void) | null = null;
      readAsDataURL(): void {
        setTimeout(() => this.onerror?.(new Error('read failed')));
      }
    }
    vi.stubGlobal('FileReader', FailingReader);
    try {
      await emitCrop(new Blob(['def'], { type: 'image/png' }), () => component.finalImage === null);
    } finally {
      vi.unstubAllGlobals();
    }
    expect(component.finalImage).toBeNull();
    expect(saveButton(el).disabled).toBe(true);
  });

  it('closes with null on cancel and on the close (x) button', () => {
    const { el, close } = setup();
    (el.querySelector('.crop-btn--cancel') as HTMLButtonElement).click();
    (el.querySelector('.crop-dialog__close') as HTMLButtonElement).click();
    expect(close).toHaveBeenCalledTimes(2);
    expect(close.mock.calls.every((c) => c[0] === null)).toBe(true);
  });

  it('unsubscribes from the cropper and destroys it on destroy', async () => {
    const { fixture, component } = setup();
    const cropper = FakeCropper.instances[0];
    fixture.destroy();
    expect(FakeCropper.destroyed).toBe(1);
    expect(cropper.imageCropped.observed).toBe(false);
    expect(component.finalImage).toBeNull();
  });

  it('uses HQ_IMAGE_CROP_DIALOG_TEXTS when provided', () => {
    const { el } = setup({ texts: { ...HQ_IMAGE_CROP_DIALOG_DEFAULT_TEXTS, title: () => 'Photo theek karein', useImage: () => 'Upyog karein' } });
    expect(el.querySelector('.crop-dialog__title')?.textContent).toBe('Photo theek karein');
    expect(saveButton(el).textContent).toBe('Upyog karein');
  });
});
