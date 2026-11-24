import {
  ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, Output, ViewChild, inject,
} from '@angular/core';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXTS, HqUploadTexts } from './hq-upload.texts';
import { HqFileRules, hqFileProblem, hqUploadLimits } from './hq-upload.helpers';

/** What a hq-file-drop draws: the drop area, only a button, or nothing (opened from code with `choose()`). */
export type HqFileDropAppearance = 'zone' | 'button' | 'none';

/**
 * The one file picker of every web app: a dashed drop area with a cloud icon, "Drop a file here, or [Choose a file]"
 * and the type/size hint. It only picks: it checks the files against `accept`, `maxSizeMB` and `minBytes` and hands
 * the good ones to the screen through `(picked)`, which decides where they go (DMS, an import API, read in the
 * browser). hq-doc-upload and hq-multi-doc-upload use it for their empty state, so DMS uploads look the same.
 *
 * When something listens to `(browse)`, the "Choose" button emits it instead of opening the picker itself, so a
 * wrapping component can keep its own file input.
 *
 * `appearance` picks what is drawn (the rules, `(picked)` and `(rejected)` are the same for all three):
 * - `'zone'` (default): the drop area above.
 * - `'button'`: only a button (an upload button in a toolbar, a paperclip in a chat box). Its content is what the screen
 *   puts between the tags, else the "Choose a file" text; `buttonClass` adds the screen's own button classes, and
 *   `ariaLabel` / `title` describe an icon-only button. A rejected file is shown under it unless `showError` is false.
 * - `'none'`: nothing visible; the screen opens the picker with `choose()` (e.g. from a row action), through a
 *   template ref or `@ViewChild(HqFileDrop)`.
 * The hidden input is cleared after every pick, so choosing the same file again is reported again.
 */
@Component({
  selector: 'hq-file-drop',
  standalone: false,
  templateUrl: './hq-file-drop.html',
  styleUrl: './hq-file-drop.scss',
  host: { '[class.hq-file-drop--inline]': "appearance !== 'zone'" },
})
export class HqFileDrop {
  /** Bold title above the prompt, e.g. 'Ladders and positions file (CSV)'. Optional. */
  @Input() label = '';
  /** Accepted types in words, e.g. 'PDF, JPEG, PNG'; shown in the hint and in the "wrong type" message. */
  @Input() hints = '';
  /** Accept list like '.csv,text/csv'; empty accepts any type. */
  @Input() accept = '';
  /** Largest size in MB; 0 means no limit. */
  @Input() maxSizeMB = 0;
  /** Smallest size in bytes; 0 means no lower limit. */
  @Input() minBytes = 0;
  /** Let several files be chosen or dropped at once. */
  @Input() multiple = false;
  /** With `multiple`, the size limit applies to each file and this many files at most may be picked (0 = no cap). */
  @Input() maxFiles = 0;
  @Input() disabled = false;
  /** Shows a spinner and "Uploading…" while the screen sends the file. */
  @Input() loading = false;
  @Input() isRequired = false;
  /** A single-row drop area for forms and dense layouts. */
  @Input() compact = false;
  /** Check files against the rules before emitting them. Off when the wrapping component checks them itself. */
  @Input() validate = true;
  /** Id of the hidden file input (for a `<label for>`); the button gets `<inputId>Btn`. */
  @Input() inputId = '';
  /** Id of the "Choose" button when it must differ from `<inputId>Btn` (a wrapper that owns the file input). */
  @Input() chooseId = '';
  /** What is drawn: the drop area, only a button, or nothing (opened with `choose()`). */
  @Input() appearance: HqFileDropAppearance = 'zone';
  /** With `appearance="button"`: extra CSS classes for the button (the screen's own button style). */
  @Input() buttonClass = '';
  /** With `appearance="button"`: accessible name of the button (needed when it only shows an icon). */
  @Input() ariaLabel = '';
  /** With `appearance="button"`: tooltip of the button. */
  @Input() title = '';
  /** With `appearance="button"`: show a rejected file's reason under the button. */
  @Input() showError = true;

  /** The accepted files (one, unless `multiple`). */
  @Output() picked = new EventEmitter<File[]>();
  /** A file that broke the rules, with the reason in the user's language. */
  @Output() rejected = new EventEmitter<string>();
  /** "Choose" was pressed; only used when the wrapping component opens its own picker. */
  @Output() browse = new EventEmitter<void>();

  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  readonly texts: HqUploadTexts = inject(HQ_UPLOAD_TEXTS, { optional: true }) ?? HQ_UPLOAD_DEFAULT_TEXTS;
  errorMessage = '';
  dragOver = false;

  private dragDepth = 0;
  private readonly cdr = inject(ChangeDetectorRef);

  get inactive(): boolean {
    return this.disabled || this.loading;
  }

  get prompt(): string {
    if (this.loading) return this.texts.uploading();
    if (this.dragOver) return this.texts.release();
    return this.multiple ? this.texts.dropMany() : this.texts.dropOne();
  }

  get chooseText(): string {
    return this.multiple ? this.texts.chooseMany() : this.texts.chooseOne();
  }

  get limits(): string {
    const line = hqUploadLimits(this.hints, this.maxSizeMB, this.texts, this.multiple);
    if (this.multiple && this.maxFiles > 0) {
      const cap = this.texts.maxFiles(this.maxFiles);
      return line ? `${line} · ${cap}` : cap;
    }
    return line;
  }

  get buttonId(): string | null {
    if (this.chooseId) return this.chooseId;
    return this.inputId ? `${this.inputId}Btn` : null;
  }

  /** Opens the browser's file picker (or asks the wrapping component to). */
  choose(): void {
    if (this.inactive) return;
    if (this.browse.observed) {
      this.browse.emit();
      return;
    }
    this.fileInput?.nativeElement.click();
  }

  onInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    this.handleFiles(files);
  }

  onDragEnter(event: DragEvent): void {
    event.preventDefault();
    if (this.inactive) return;
    this.dragDepth++;
    this.dragOver = true;
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = this.inactive ? 'none' : 'copy';
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragDepth = Math.max(0, this.dragDepth - 1);
    if (!this.dragDepth) this.dragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragDepth = 0;
    this.dragOver = false;
    if (this.inactive) return;
    this.handleFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  /** Checks the picked files and emits the good ones; the first problem is shown and emitted. */
  private handleFiles(files: File[]): void {
    if (!files.length || this.inactive) return;
    const chosen = this.multiple ? files : files.slice(0, 1);
    this.errorMessage = '';
    if (!this.validate) {
      this.picked.emit(chosen);
      return;
    }
    const good: File[] = [];
    let problem: string | null = null;
    for (const file of chosen) {
      if (this.multiple && this.maxFiles > 0 && good.length >= this.maxFiles) {
        problem ??= this.texts.tooMany(this.maxFiles);
        break;
      }
      const reason = hqFileProblem(file, this.rules, this.texts);
      if (reason) problem ??= this.multiple ? `${file.name}: ${reason}` : reason;
      else good.push(file);
    }
    if (problem) {
      this.errorMessage = problem;
      this.rejected.emit(problem);
    }
    if (good.length) this.picked.emit(good);
    this.cdr.markForCheck();
  }

  private get rules(): HqFileRules {
    return { accept: this.accept, hints: this.hints, maxSizeMB: this.maxSizeMB, minBytes: this.minBytes };
  }
}
