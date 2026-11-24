import {
  ChangeDetectorRef, Component, EventEmitter, forwardRef, Inject, Input, OnChanges, OnDestroy, Optional, Output,
  SimpleChanges, ViewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom, Observable } from 'rxjs';
import { HQ_CONFIG, HQ_DMS_ACCEPT, HQ_DMS_MAX_SIZE_MB, HqSharedLibConfig } from '../hq-common.config';
import { HqCloseDialogData, HqDocument } from '../hq-common.models';
import { HqDocumentService } from '../hq-document.service';
import { HqClose } from '../hq-close/hq-close';
import { HqOpenFile } from '../hq-open-file/hq-open-file';
import { formatSize, saveBlob } from '../hq-file.util';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXTS, HqUploadTexts } from '../hq-file-drop/hq-upload.texts';
import { HQ_CUSTOM_FILE_NAME_PATTERN, HQ_DMS_MIN_FILE_BYTES } from '../hq-file-drop/hq-upload.constants';
import { hqFileProblem } from '../hq-file-drop/hq-upload.helpers';
import { HqFileDrop } from '../hq-file-drop/hq-file-drop';

/**
 * Multi-document upload bound to Rider_DMS (tou-multi-doc-upload). Form control value is the
 * list of HqDocuments. DMS accepts one file per request, so each selected file is uploaded on
 * its own under the same category + ownerId; `autoLoad` lists everything already filed there.
 */
@Component({
  selector: 'hq-multi-doc-upload',
  standalone: false,
  templateUrl: './hq-multi-doc-upload.html',
  styleUrl: './hq-multi-doc-upload.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqMultiDocUpload), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqMultiDocUpload), multi: true },
  ],
})
export class HqMultiDocUpload implements ControlValueAccessor, Validator, OnChanges, OnDestroy {
  /** Title shown in the drop area, e.g. 'Vehicle photos'. */
  @Input() label = '';
  @Input() hints = 'PDF, JPEG, PNG';
  @Input() accept = HQ_DMS_ACCEPT;
  @Input() maxSizeMB = HQ_DMS_MAX_SIZE_MB;
  @Input() category = 'general';
  /** Document type code (Document types screen): when set, DMS applies that type's file rules to the upload. */
  @Input() documentType: string | null = null;
  /** Owning entity id; defaults to the signed-in user. */
  @Input() ownerId = '';
  /** Allow choosing several files in the picker at once. */
  @Input() multiple = true;
  /** Show the optional "File name" field that renames the next upload (tou's docmentName). */
  @Input() allowCustomName = true;
  @Input() maxFiles = 0;
  @Input() isRequired = false;
  @Input() removable = true;
  @Input() disabled = false;
  @Input() autoLoad = false;
  /** Also delete in DMS when a document is removed from the list. */
  @Input() deleteOnRemove = false;
  @Input() inputId = 'hqMultiDocUpload';
  /** Single-row drop area, for forms. */
  @Input() compact = false;
  /** Show images as a gallery of pictures (each read back through fetcher or DMS) instead of file chips. */
  @Input() thumbnails = false;
  /**
   * Stores each file somewhere other than DMS (an application's own upload endpoint) and answers the stored document; when set it is
   * used instead of DMS, and autoLoad (which lists DMS) does nothing. Build the answer with {@link hqDocumentFromFile} when the endpoint
   * answers only an id.
   */
  @Input() uploader: ((file: File) => Observable<HqDocument>) | null = null;
  /** Reads a stored document back, for View and the thumbnails, when the files are not in DMS (pairs with uploader). */
  @Input() fetcher: ((doc: HqDocument) => Observable<Blob>) | null = null;
  /** Deletes a stored document when it is removed, when the files are not in DMS; asks first, like deleteOnRemove. */
  @Input() remover: ((doc: HqDocument) => Observable<unknown>) | null = null;
  /** Id of the list's main document (for example a profile's main photo); its tile carries {@link primaryLabel}. */
  @Input() primaryId: string | null = null;
  /** Badge text on the main document's tile (already translated); no badge when empty. */
  @Input() primaryLabel = '';
  /**
   * Makes a document the main one (the screen's own call); when set, every other document gets a "make main" button whose
   * accessible name and tooltip is {@link makePrimaryLabel}. On success primaryId follows and primaryChanged fires.
   */
  @Input() primarySetter: ((doc: HqDocument) => Observable<unknown>) | null = null;
  /** Text of the "make main" button (already translated). */
  @Input() makePrimaryLabel = '';

  @Input()
  set value(value: HqDocument[] | null | undefined) {
    this.setValue(value ?? [], false);
  }
  get value(): HqDocument[] {
    return this._value;
  }

  @Output() uploaded = new EventEmitter<HqDocument>();
  @Output() removed = new EventEmitter<HqDocument>();
  @Output() rejected = new EventEmitter<string>();
  /** The document that just became the main one (after primarySetter succeeded). */
  @Output() primaryChanged = new EventEmitter<HqDocument>();

  /** The invisible picker opened by pickFile(). */
  @ViewChild('picker') picker?: HqFileDrop;

  customName = '';
  loading = false;
  errorMessage = '';
  readonly formatSize = formatSize;
  /** Picture of each image document, by document id (object URLs, released when the document leaves the list). */
  thumbs: Record<string, string> = {};

  private _value: HqDocument[] = [];
  private onChange: (value: HqDocument[]) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(
    private documents: HqDocumentService,
    @Inject(HQ_CONFIG) private config: HqSharedLibConfig,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    @Optional() @Inject(HQ_UPLOAD_TEXTS) texts: HqUploadTexts | null,
  ) {
    this.texts = texts ?? HQ_UPLOAD_DEFAULT_TEXTS;
  }

  readonly texts: HqUploadTexts;

  get customNameInvalid(): boolean {
    return !!this.customName && !HQ_CUSTOM_FILE_NAME_PATTERN.test(this.customName);
  }

  get atLimit(): boolean {
    return this.maxFiles > 0 && this._value.length >= this.maxFiles;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['autoLoad'] || changes['category'] || changes['ownerId']) && this.autoLoad && !this.uploader) {
      this.loadExisting();
    }
    if (changes['thumbnails'] || changes['fetcher'] || changes['uploader']) this.refreshThumbs();
  }

  ngOnDestroy(): void {
    Object.values(this.thumbs).forEach((url) => URL.revokeObjectURL(url));
    this.thumbs = {};
  }

  /**
   * Whether stored files can be read back: always for DMS, and for a screen's own storage only when it gives a fetcher (a file
   * stored through the uploader is not in DMS, so DMS can never serve it).
   */
  get canOpen(): boolean {
    return !this.uploader || !!this.fetcher;
  }

  /** Whether the document is the list's main one. */
  isPrimary(doc: HqDocument): boolean {
    return !!this.primaryId && doc.id === this.primaryId;
  }

  /** Whether the "make main" button is offered for the document. */
  canMakePrimary(doc: HqDocument): boolean {
    return !!this.primarySetter && !this.disabled && !this.isPrimary(doc);
  }

  /** Makes the document the main one through the screen's primarySetter; the list itself is left as it is. */
  async makePrimary(doc: HqDocument): Promise<void> {
    if (!this.primarySetter || !this.canMakePrimary(doc) || this.loading) return;
    this.errorMessage = '';
    this.loading = true;
    this.cdr.markForCheck();
    try {
      await firstValueFrom(this.primarySetter(doc));
      this.primaryId = doc.id;
      this.primaryChanged.emit(doc);
    } catch (err: any) {
      // The screen's setter reports its own failure; show the server's reason when there is one.
      const message = err?.error?.message;
      if (message) this.reject(message);
    } finally {
      this.loading = false;
      this.cdr.markForCheck();
    }
  }

  /** Whether the document is a picture (shown as one in the gallery). */
  isImage(doc: HqDocument): boolean {
    return (doc.contentType ?? '').startsWith('image/');
  }

  writeValue(value: HqDocument[] | null): void {
    this.setValue(value ?? [], false);
    if (!value?.length && this.autoLoad && !this.uploader) this.loadExisting();
  }
  registerOnChange(fn: (value: HqDocument[]) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled = isDisabled; }
  validate(): ValidationErrors | null {
    return this.isRequired && !this._value.length ? { required: true } : null;
  }

  pickFile(): void {
    if (this.disabled || this.loading || this.atLimit || this.customNameInvalid) return;
    this.picker?.choose();
  }

  async onFilesSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    await this.onFilesPicked(files);
  }

  /** Files chosen through the picker or dropped on the drop area; each is checked and uploaded on its own. */
  async onFilesPicked(files: File[]): Promise<void> {
    if (!files.length || this.disabled) return;
    this.errorMessage = '';

    const ownerId = this.ownerId || this.config.currentUserId() || '';
    if (!ownerId && !this.uploader) {
      this.reject(this.texts.noOwnerMany());
      return;
    }

    this.loading = true;
    this.cdr.markForCheck();
    for (let i = 0; i < files.length; i++) {
      if (this.atLimit) {
        this.reject(this.texts.tooMany(this.maxFiles));
        break;
      }
      // A custom name applies to the first file only, with a numeric suffix for the rest.
      const file = this.renamed(files[i], i);
      const problem = this.validateFile(file);
      if (problem) {
        this.reject(problem);
        continue;
      }
      try {
        const doc = await this.store(file, ownerId);
        this.setValue([...this._value, doc], true);
        this.uploaded.emit(doc);
      } catch (err: any) {
        this.reject(err?.error?.message ?? this.texts.uploadFailedNamed(file.name));
      }
    }
    this.customName = '';
    this.loading = false;
    this.cdr.markForCheck();
  }

  open(doc: HqDocument): void {
    if (!this.canOpen) return;
    this.read(doc).subscribe({
      next: (blob) => {
        if (blob.type.startsWith('image/') || blob.type === 'application/pdf') {
          this.dialog.open(HqOpenFile, { data: { blob, fileName: doc.originalFileName }, maxWidth: '95vw', autoFocus: false });
        } else {
          saveBlob(blob, doc.originalFileName);
        }
      },
      error: () => this.reject(this.texts.openFailed()),
    });
  }

  async remove(doc: HqDocument): Promise<void> {
    if (this.disabled || !this.removable) return;
    if (this.deleteOnRemove || this.remover) {
      const data: HqCloseDialogData = { confirm: true, message: this.texts.deleteNamedConfirm(doc.originalFileName) };
      const yes = await firstValueFrom(this.dialog.open(HqClose, { data, width: '420px', disableClose: true }).afterClosed());
      if (!yes) return;
      try {
        await firstValueFrom(this.remover ? this.remover(doc) : this.documents.delete(doc.id));
      } catch (err: any) {
        this.reject(err?.error?.message ?? this.texts.deleteFailed());
        return;
      }
    }
    this.setValue(this._value.filter((d) => d.id !== doc.id), true);
    this.removed.emit(doc);
  }

  /** Stores one file: through the screen's uploader when it stores files itself, otherwise in DMS. */
  private async store(file: File, ownerId: string): Promise<HqDocument> {
    if (this.uploader) return firstValueFrom(this.uploader(file));
    const checksum = await this.documents.sha256(file);
    return firstValueFrom(this.documents.upload(file, this.category, ownerId, checksum, this.documentType));
  }

  /** The stored file: through the screen's fetcher when it stores files itself, otherwise from DMS. */
  private read(doc: HqDocument): Observable<Blob> {
    return this.fetcher ? this.fetcher(doc) : this.documents.fetchFile(doc);
  }

  /** Loads a picture for each image in the list that has none yet, and releases those of documents no longer listed. */
  private refreshThumbs(): void {
    const ids = new Set(this._value.map((d) => d.id));
    for (const [id, url] of Object.entries(this.thumbs)) {
      if (!this.thumbnails || !ids.has(id)) {
        URL.revokeObjectURL(url);
        delete this.thumbs[id];
      }
    }
    if (!this.thumbnails || !this.canOpen) return;
    for (const doc of this._value) {
      if (!this.isImage(doc) || this.thumbs[doc.id]) continue;
      this.read(doc).subscribe({
        next: (blob) => {
          if (!this._value.some((d) => d.id === doc.id) || this.thumbs[doc.id]) return; // removed meanwhile
          this.thumbs = { ...this.thumbs, [doc.id]: URL.createObjectURL(blob) };
          this.cdr.markForCheck();
        },
        error: () => {}, // the tile keeps its file icon
      });
    }
  }

  private loadExisting(): void {
    const ownerId = this.ownerId || this.config.currentUserId() || '';
    if (!ownerId || !this.category) return;
    this.documents.list(this.category, ownerId).subscribe({
      next: (docs) => {
        if (!this._value.length && docs.length) this.setValue(docs, true);
      },
      error: () => {},
    });
  }

  private renamed(file: File, index: number): File {
    if (!this.customName || this.customNameInvalid) return file;
    const dot = file.name.lastIndexOf('.');
    const ext = dot >= 0 ? file.name.substring(dot) : '';
    const name = index === 0 ? this.customName : `${this.customName}_${index + 1}`;
    return new File([file], name + ext, { type: file.type });
  }

  private validateFile(file: File): string | null {
    const rules = { accept: this.accept, hints: this.hints, maxSizeMB: 0, minBytes: 0 };
    if (hqFileProblem(file, rules, this.texts)) return this.texts.invalidTypeNamed(file.name, this.hints);
    if (file.size < HQ_DMS_MIN_FILE_BYTES) return this.texts.tooSmallNamed(file.name);
    if (this.maxSizeMB > 0 && file.size > this.maxSizeMB * 1024 * 1024) {
      return this.texts.tooLargeNamed(file.name, this.maxSizeMB);
    }
    const lower = file.name.toLowerCase();
    if (this._value.some((d) => d.originalFileName?.toLowerCase() === lower)) {
      return this.texts.alreadyAttached(file.name);
    }
    return null;
  }

  private setValue(value: HqDocument[], emit: boolean): void {
    this._value = value;
    this.refreshThumbs();
    if (emit) {
      this.onChange(value);
      this.onTouched();
    }
    this.cdr.markForCheck();
  }

  private reject(message: string): void {
    this.errorMessage = message;
    this.rejected.emit(message);
    this.cdr.markForCheck();
  }
}
