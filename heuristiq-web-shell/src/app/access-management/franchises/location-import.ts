import { ChangeDetectorRef, Component, EventEmitter, OnInit, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { LocationImportError, LocationImportResult } from './franchise.models';
import { IMPORT_FILE_ACCEPT, IMPORT_MAX_ROWS, IMPORT_TEMPLATE_FILE_NAME } from './franchise-locations.constants';
import { downloadTextFile, importInputError, importRowCount, importTemplateCsv, readFileText } from './franchise-locations.helpers';

/**
 * Imports places from CSV: choose a file or paste text, check it with a dry run (counts and a table of row errors), then
 * apply it. Applying is offered only after a dry run of exactly this text came back with no errors; the same file may be
 * run again safely (rows already stored are skipped).
 */
@Component({
  selector: 'app-location-import',
  standalone: false,
  templateUrl: './location-import.html',
  styleUrl: './franchises-list.scss',
})
export class LocationImport implements OnInit {
  @Output() imported = new EventEmitter<LocationImportResult>();

  readonly accept = IMPORT_FILE_ACCEPT;
  readonly maxRows = IMPORT_MAX_ROWS;

  /** The grid of row errors (headings translated when the component opens). */
  errorColumns: HqCommonTableColumn<LocationImportError>[] = [];

  csv = '';
  fileName = '';
  busy = false;
  result: LocationImportResult | null = null;
  /** The text the last dry run was made for: applying a text that was edited since needs a new check. */
  private checkedCsv: string | null = null;

  constructor(private api: FranchiseApi, private notify: NotificationService, private translate: TranslateService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchiseLocations.import.${key}`);
    this.errorColumns = [
      { key: 'row', label: heading('row') },
      { key: 'line', label: heading('line') },
      { key: 'field', label: heading('field') },
      { key: 'message', label: heading('message') },
    ];
  }

  get rows(): number {
    return importRowCount(this.csv);
  }

  /** The dry run found nothing wrong with exactly the text now in the box. */
  get canApply(): boolean {
    return !this.busy && !!this.result && this.result.dryRun && this.result.errorCount === 0 && this.checkedCsv === this.csv && this.csv !== '';
  }

  downloadTemplate(): void {
    downloadTextFile(IMPORT_TEMPLATE_FILE_NAME, importTemplateCsv());
  }

  onTextChange(): void {
    this.result = null;
    this.checkedCsv = null;
  }

  /** A CSV chosen or dropped on the shared drop area (which already checked its type): its text goes into the box. */
  onFilesPicked(files: File[]): void {
    const file = files[0];
    if (!file) {
      return;
    }
    this.fileName = file.name;
    readFileText(file).then(
      (text) => { this.csv = text; this.onTextChange(); this.cdr.detectChanges(); },
      () => this.notify.error(this.translate.instant('accessManagement.franchiseLocations.import.errors.unreadable')),
    );
  }

  dryRun(): void {
    this.run(true);
  }

  apply(): void {
    if (this.canApply) {
      this.run(false);
    }
  }

  private run(dryRun: boolean): void {
    const error = importInputError(this.csv);
    if (error) {
      this.notify.error(this.translate.instant(error, { max: this.maxRows }));
      return;
    }
    const sent = this.csv;
    this.busy = true;
    this.api.importLocations(sent, dryRun).subscribe({
      next: (result) => {
        this.busy = false;
        this.result = result;
        this.checkedCsv = dryRun && result.errorCount === 0 ? sent : null;
        if (!dryRun && result.applied) {
          this.notify.success(this.translate.instant('accessManagement.franchiseLocations.import.applied', { created: result.created, updated: result.updated }));
          this.imported.emit(result);
        }
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.busy = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseLocations.import.errors.failed')));
        this.cdr.detectChanges();
      },
    });
  }
}
