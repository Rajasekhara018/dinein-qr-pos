import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  forwardRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subscription } from 'rxjs';
import { UploadResult } from '../../../../core/api/models';
import { BreakpointService } from '../../../../core/ui/breakpoint.service';
import { prepareImage, uploadErrorMessage } from './image-prep';
import { ImageUploadService } from './image-upload.service';

let nextId = 0;

/**
 * Image picker + uploader bound to an image id (`formControlName="imageId"`).
 *
 * Drag-and-drop, click-to-choose and (on touch devices) camera capture; instant local preview; client-side
 * pre-resize of big photos; progress bar; clear errors. `url` shows the current (server) image. Emits `uploaded`
 * with the server result so parents can update previews.
 */
@Component({
  selector: 'app-image-upload',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ImageUpload), multi: true },
  ],
  templateUrl: './image-upload.html',
})
export class ImageUpload implements ControlValueAccessor {
  private readonly uploads = inject(ImageUploadService);
  protected readonly coarsePointer = inject(BreakpointService).coarsePointer;

  readonly label = input('Photo');
  /** Current server image (thumb or full URL). */
  readonly url = input<string | null | undefined>(null);
  /** Keep transparency (PNG) — for logos. */
  readonly keepAlpha = input(false, { transform: booleanAttribute });
  readonly uploaded = output<UploadResult>();

  protected readonly inputId = `image-upload-${nextId++}`;
  protected readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
  protected readonly cameraInput = viewChild<ElementRef<HTMLInputElement>>('cameraInput');

  protected readonly imageId = signal<number | null>(null);
  protected readonly localPreview = signal<string | null>(null);
  private readonly uploadedUrl = signal<string | null>(null);
  private readonly cleared = signal(false);
  protected readonly progress = signal<number | null>(null);
  protected readonly status = signal<'idle' | 'preparing' | 'uploading'>('idle');
  protected readonly error = signal('');
  protected readonly dragging = signal(false);
  protected readonly disabled = signal(false);

  protected readonly previewUrl = computed(() => {
    if (this.localPreview()) return this.localPreview();
    if (this.uploadedUrl()) return this.uploadedUrl();
    if (this.cleared() || this.imageId() === null) return null;
    return this.url() ?? null;
  });
  protected readonly busy = computed(() => this.status() !== 'idle');

  private sub: Subscription | null = null;
  private onChange: (value: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.sub?.unsubscribe();
      this.revokePreview();
    });
  }

  protected choose(): void {
    this.fileInput().nativeElement.click();
  }

  protected capture(): void {
    this.cameraInput()?.nativeElement.click();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) void this.handleFile(file);
  }

  protected onDragOver(event: DragEvent): void {
    if (this.disabled() || this.busy()) return;
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
    this.dragging.set(true);
  }

  protected onDragLeave(): void {
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (this.disabled() || this.busy()) return;
    const file = event.dataTransfer?.files?.[0];
    if (file) void this.handleFile(file);
  }

  protected remove(): void {
    this.cancelUpload();
    this.revokePreview();
    this.uploadedUrl.set(null);
    this.cleared.set(true);
    this.imageId.set(null);
    this.error.set('');
    this.onChange(null);
    this.onTouched();
  }

  protected cancelUpload(): void {
    if (this.status() === 'uploading') {
      this.sub?.unsubscribe();
      this.sub = null;
      this.status.set('idle');
      this.progress.set(null);
      this.revokePreview();
    }
  }

  async handleFile(file: File): Promise<void> {
    this.error.set('');
    this.onTouched();
    this.status.set('preparing');
    let prepared;
    try {
      prepared = await prepareImage(file, { keepAlpha: this.keepAlpha() });
    } catch (error) {
      this.status.set('idle');
      this.error.set(uploadErrorMessage(error));
      return;
    }
    this.setPreview(URL.createObjectURL(prepared.blob));
    this.status.set('uploading');
    this.progress.set(0);
    this.sub?.unsubscribe();
    this.sub = this.uploads.upload(prepared.blob, prepared.fileName).subscribe({
      next: (event) => {
        if (event.kind === 'progress') {
          this.progress.set(event.percent);
          return;
        }
        this.progress.set(100);
        this.status.set('idle');
        this.cleared.set(false);
        this.imageId.set(event.result.imageId);
        this.uploadedUrl.set(event.result.thumbUrl || event.result.url);
        this.onChange(event.result.imageId);
        this.uploaded.emit(event.result);
        // Keep the local preview (already decoded) until the next change.
        setTimeout(() => this.progress.set(null), 600);
      },
      error: (error: unknown) => {
        this.status.set('idle');
        this.progress.set(null);
        this.revokePreview();
        this.error.set(uploadErrorMessage(error));
      },
    });
  }

  writeValue(value: unknown): void {
    this.imageId.set(typeof value === 'number' ? value : null);
    this.cleared.set(false);
    this.uploadedUrl.set(null);
    this.revokePreview();
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  private setPreview(url: string): void {
    this.revokePreview();
    this.localPreview.set(url);
  }

  private revokePreview(): void {
    const current = this.localPreview();
    if (current) URL.revokeObjectURL(current);
    this.localPreview.set(null);
  }
}
