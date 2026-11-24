import {
  ChangeDetectorRef, Component, EventEmitter, forwardRef, Inject, Input, OnChanges,
  OnDestroy, Optional, Output, SimpleChanges, ViewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Observable, finalize, firstValueFrom } from 'rxjs';
import { HQ_CONFIG, HQ_DMS_ACCEPT, HQ_DMS_MAX_SIZE_MB, HQ_IMAGE_CROPPER, HqSharedLibConfig } from '../hq-common.config';
import { HqCloseDialogData, HqDocument, HqImageCropper } from '../hq-common.models';
import { HqDocumentService } from '../hq-document.service';
import { HqClose } from '../hq-close/hq-close';
import { HqOpenFile } from '../hq-open-file/hq-open-file';
import { formatSize, saveBlob } from '../hq-file.util';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXTS, HqUploadTexts } from '../hq-file-drop/hq-upload.texts';
import { HQ_DMS_MIN_FILE_BYTES, HQ_PREVIEW_IMAGE_TYPES } from '../hq-file-drop/hq-upload.constants';
import { hqFileProblem } from '../hq-file-drop/hq-upload.helpers';
import { HqFileDrop } from '../hq-file-drop/hq-file-drop';

export type HqDocPreview = 'none' | 'circle' | 'square';

/**
 * Single-document upload bound to Rider_DMS (tou-doc-upload). Form control value is the DMS
 * HqDocument (null when empty). Documents are filed under `category` + `ownerId`; with
 * `autoLoad` the latest document already filed under that pair is shown, which is how entity
 * screens (driver/vehicle/vendor documents) read back what was uploaded before.
 */
@Component({
  selector: 'hq-doc-upload',
  standalone: false,
  templateUrl: './hq-doc-upload.html',
  styleUrl: './hq-doc-upload.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqDocUpload), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqDocUpload), multi: true },
  ],
})
export class HqDocUpload implements ControlValueAccessor, Validator, OnChanges, OnDestroy {
  /** Title shown in the drop area, e.g. 'Driving licence'. */
  @Input() label = '';
  @Input() hints = 'PDF, JPEG, PNG';
  @Input() accept = HQ_DMS_ACCEPT;
  @Input() maxSizeMB = HQ_DMS_MAX_SIZE_MB;
  /** DMS category, e.g. 'driver-license', 'vehicle-rc', 'profile-photo'. */
  @Input() category = 'general';
  /** Document type code (Document types screen): when set, DMS applies that type's file rules to the upload. */
  @Input() documentType: string | null = null;
  /** Owning entity id. Defaults to the signed-in user; DMS only lets OPS/admin roles file
   *  documents under someone else's id. */
  @Input() ownerId = '';
  @Input() preview: HqDocPreview = 'none';
  @Input() isRequired = false;
  @Input() removable = true;
  @Input() disabled = false;
  /** Show the edit menu on circle/square previews (tou's isEdit). */
  @Input() isEdit = true;
  /** Load the latest document already filed under category+ownerId when no value is set. */
  @Input() autoLoad = false;
  /** Also delete the document in DMS on remove / replace. Use when DMS is the only record
   *  of the document (autoLoad screens); otherwise the owning form decides what to keep. */
  @Input() deleteOnRemove = false;
  /**
   * Stores the file somewhere other than DMS (an application's own upload endpoint) and answers the stored document; when set it is
   * used instead of DMS, and autoLoad and deleteOnRemove (which are about DMS) do nothing. Build the answer with
   * {@link hqDocumentFromFile} when the endpoint answers only an id.
   */
  @Input() uploader: ((file: File) => Observable<HqDocument>) | null = null;
  /** Reads a stored document back, for View and the image preview, when the files are not in DMS (pairs with uploader). */
  @Input() fetcher: ((doc: HqDocument) => Observable<Blob>) | null = null;
  /** Ask before replacing an existing document (tou's override confirmation). */
  @Input() confirmReplace = true;
  @Input() enableCrop = false;
  @Input() cropAspectRatio = 1;
  @Input() inputId = 'hqDocUpload';
  /** Single-row drop area, for forms and per-field uploads. */
  @Input() compact = false;

  @Input()
  set value(value: HqDocument | null | undefined) {
    this.setValue(value ?? null, false);
  }
  get value(): HqDocument | null {
    return this._value;
  }

  /** Fired after a successful upload (tou's (apiCall) callback). */
  @Output() uploaded = new EventEmitter<HqDocument>();
  @Output() removed = new EventEmitter<HqDocument | null>();
  @Output() rejected = new EventEmitter<string>();

  /** The invisible picker behind the "Change" button. */
  @ViewChild('picker') picker?: HqFileDrop;

  loading = false;
  errorMessage = '';
  previewUrl: string | null = null;

  private _value: HqDocument | null = null;
  private previewFor: string | null = null;
  private onChange: (value: HqDocument | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(
    private documents: HqDocumentService,
    @Inject(HQ_CONFIG) private config: HqSharedLibConfig,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    @Optional() @Inject(HQ_IMAGE_CROPPER) private cropper: HqImageCropper | null,
    @Optional() @Inject(HQ_UPLOAD_TEXTS) texts: HqUploadTexts | null,
  ) {
    this.texts = texts ?? HQ_UPLOAD_DEFAULT_TEXTS;
  }

  readonly texts: HqUploadTexts;

  get empty(): boolean {
    return !this._value;
  }

  get isImagePreview(): boolean {
    return this.preview !== 'none';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['autoLoad'] || changes['category'] || changes['ownerId']) && this.autoLoad && this.empty && !this.uploader) {
      this.loadExisting();
    }
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  // ── ControlValueAccessor / Validator ─────────────────────────────────────
  writeValue(value: HqDocument | null): void {
    this.setValue(value ?? null, false);
    if (!value && this.autoLoad) this.loadExisting();
  }
  registerOnChange(fn: (value: HqDocument | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
  validate(): ValidationErrors | null {
    return this.isRequired && this.empty ? { required: true } : null;
  }

  // ── Actions ──────────────────────────────────────────────────────────────
  pickFile(): void {
    if (!this.disabled && !this.loading) this.picker?.choose();
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    try {
      if (file) await this.handleFile(file, event);
    } finally {
      input.value = '';
    }
  }

  /** Files dropped on (or chosen through) the drop area of the empty state. */
  async onFilesPicked(files: File[]): Promise<void> {
    if (files[0]) await this.handleFile(files[0], new Event('drop'));
  }

  private async handleFile(picked: File, event: Event): Promise<void> {
    if (this.disabled) return;
    let file = picked;
    this.errorMessage = '';
    const problem = this.validateFile(file);
    if (problem) {
      this.reject(problem);
      return;
    }
    if (!this.empty && this.confirmReplace && !(await this.confirm(this.texts.replaceConfirm()))) {
      return;
    }
    if (this.enableCrop && this.cropper && file.type.startsWith('image/')) {
      const cropped = await this.cropper(event, file, this.cropAspectRatio);
      if (!cropped) return;
      file = cropped;
    }
    await this.uploadFile(file);
  }

  /**
   * Whether the stored file can be read back: always for DMS, and for a screen's own storage only when it gives a fetcher (a file
   * stored through the uploader is not in DMS, so DMS can never serve it).
   */
  get canOpen(): boolean {
    return !this.uploader || !!this.fetcher;
  }

  open(): void {
    const doc = this._value;
    if (!doc || !this.canOpen) return;
    this.read(doc).subscribe({
      next: (blob) => {
        if (blob.type.startsWith('image/') || blob.type === 'application/pdf') {
          this.dialog.open(HqOpenFile, {
            data: { blob, fileName: doc.originalFileName },
            maxWidth: '95vw',
            autoFocus: false,
          });
        } else {
          saveBlob(blob, doc.originalFileName);
        }
      },
      error: () => this.reject(this.texts.openFailed()),
    });
  }

  async remove(): Promise<void> {
    const doc = this._value;
    if (!doc || this.disabled || !this.removable) return;
    if (this.deleteOnRemove && !this.uploader) {
      if (!(await this.confirm(this.texts.deleteConfirm()))) return;
      try {
        await firstValueFrom(this.documents.delete(doc.id));
      } catch (err: any) {
        this.reject(err?.error?.message ?? this.texts.deleteFailed());
        return;
      }
    }
    this.setValue(null, true);
    this.removed.emit(doc);
  }

  // ── Internals ────────────────────────────────────────────────────────────
  private async uploadFile(file: File): Promise<void> {
    if (this.uploader) {
      await this.uploadThrough(this.uploader, file);
      return;
    }
    const ownerId = this.ownerId || this.config.currentUserId() || '';
    if (!ownerId) {
      this.reject(this.texts.noOwner());
      return;
    }
    const previous = this._value;
    this.loading = true;
    this.cdr.markForCheck();
    try {
      const checksum = await this.documents.sha256(file);
      const doc = await firstValueFrom(
        this.documents.upload(file, this.category, ownerId, checksum, this.documentType).pipe(finalize(() => (this.loading = false))),
      );
      this.setValue(doc, true);
      this.uploaded.emit(doc);
      if (previous && this.deleteOnRemove) {
        this.documents.delete(previous.id).subscribe({ error: () => {} });
      }
    } catch (err: any) {
      this.loading = false;
      this.reject(err?.error?.message ?? err?.error?.detail ?? this.texts.uploadFailed());
    }
    this.cdr.markForCheck();
  }

  /** Upload through the screen's own uploader (no DMS: no owner, checksum or DMS delete). */
  private async uploadThrough(uploader: (file: File) => Observable<HqDocument>, file: File): Promise<void> {
    this.loading = true;
    this.cdr.markForCheck();
    try {
      const doc = await firstValueFrom(uploader(file).pipe(finalize(() => (this.loading = false))));
      this.setValue(doc, true);
      this.uploaded.emit(doc);
    } catch (err: any) {
      this.loading = false;
      this.reject(err?.error?.message ?? err?.error?.detail ?? this.texts.uploadFailed());
    }
    this.cdr.markForCheck();
  }

  /** The stored file: through the screen's fetcher when it stores files itself, otherwise from DMS. */
  private read(doc: HqDocument): Observable<Blob> {
    return this.fetcher ? this.fetcher(doc) : this.documents.fetchFile(doc);
  }

  private loadExisting(): void {
    const ownerId = this.ownerId || this.config.currentUserId() || '';
    if (!ownerId || !this.category) return;
    this.documents.list(this.category, ownerId).subscribe({
      next: (docs) => {
        if (this.empty && docs.length) this.setValue(docs[0], true);
      },
      error: () => {},
    });
  }

  private setValue(value: HqDocument | null, emit: boolean): void {
    this._value = value;
    this.refreshPreview();
    if (emit) {
      this.onChange(value);
      this.onTouched();
    }
    this.cdr.markForCheck();
  }

  private refreshPreview(): void {
    const doc = this._value;
    if (!this.isImagePreview || !doc || !doc.contentType?.startsWith('image/') || !this.canOpen) {
      this.revokePreview();
      return;
    }
    if (this.previewFor === doc.id) return;
    this.revokePreview();
    this.previewFor = doc.id;
    this.read(doc).subscribe({
      next: (blob) => {
        if (this.previewFor !== doc.id) return; // value changed while loading
        this.previewUrl = URL.createObjectURL(blob);
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  private revokePreview(): void {
    if (this.previewUrl) URL.revokeObjectURL(this.previewUrl);
    this.previewUrl = null;
    this.previewFor = null;
  }

  private validateFile(file: File): string | null {
    if (this.isImagePreview && !HQ_PREVIEW_IMAGE_TYPES.includes(file.type)) {
      return this.texts.invalidImage();
    }
    const rules = { accept: this.accept, hints: this.hints, maxSizeMB: this.maxSizeMB, minBytes: HQ_DMS_MIN_FILE_BYTES };
    return hqFileProblem(file, rules, this.texts);
  }

  private confirm(message: string): Promise<boolean> {
    const data: HqCloseDialogData = { confirm: true, message };
    const ref = this.dialog.open<HqClose, HqCloseDialogData, boolean>(HqClose, { data, width: '420px', disableClose: true });
    return firstValueFrom(ref.afterClosed() as Observable<boolean | undefined>).then((yes) => !!yes);
  }

  private reject(message: string): void {
    this.errorMessage = message;
    this.rejected.emit(message);
    this.cdr.markForCheck();
  }

  readonly formatSize = formatSize;
}
