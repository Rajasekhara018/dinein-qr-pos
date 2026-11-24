import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HqSharedLibModule } from 'hq-shared-lib';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { LocationImportResult } from './franchise.models';
import { LocationImport } from './location-import';
import { readFileText } from './franchise-locations.helpers';
import { IMPORT_CSV_HEADER, IMPORT_MAX_ROWS, IMPORT_TEMPLATE_FILE_NAME } from './franchise-locations.constants';

const api = { importLocations: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };

const CSV = `${IMPORT_CSV_HEADER}\nIN,TOWN,A,Alpha,DISTRICT,AP-1,true\nIN,TOWN,B,Beta,DISTRICT,AP-9,true\n`;
const KEY = 'accessManagement.franchiseLocations.import.';

const result = (over: Partial<LocationImportResult> = {}): LocationImportResult => ({
  dryRun: true, applied: false, totalRows: 2, created: 1, updated: 1, skipped: 0, errorCount: 0, errorsTruncated: false, errors: [], ...over,
});

function create(): { fixture: ComponentFixture<LocationImport>; component: LocationImport; el: HTMLElement } {
  const fixture = TestBed.createComponent(LocationImport);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('LocationImport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.importLocations.mockReturnValue(of(result()));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [LocationImport],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [{ provide: FranchiseApi, useValue: api }, { provide: NotificationService, useValue: notify }],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('downloads a template made of the header and one example line', async () => {
    const urls: Blob[] = [];
    const original = { create: URL.createObjectURL, revoke: URL.revokeObjectURL };
    URL.createObjectURL = (blob: Blob | MediaSource) => { urls.push(blob as Blob); return 'blob:x'; };
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const { component } = create();

    try {
      component.downloadTemplate();
    } finally {
      URL.createObjectURL = original.create;
      URL.revokeObjectURL = original.revoke;
    }

    expect(click).toHaveBeenCalled();
    const text = await readFileText(urls[0]);
    expect(text.trim().split('\n').length).toBe(2);
    expect(text.startsWith(IMPORT_CSV_HEADER)).toBe(true);
    expect(IMPORT_TEMPLATE_FILE_NAME).toMatch(/\.csv$/);
  });

  it('reads a chosen file into the text box', async () => {
    const { component } = create();
    const file = new File([CSV], 'places.csv', { type: 'text/csv' });

    component.onFilesPicked([]);
    expect(component.fileName).toBe('');
    component.onFilesPicked([file]);
    await vi.waitFor(() => expect(component.csv).toBe(CSV));

    expect(component.fileName).toBe('places.csv');
    expect(component.rows).toBe(2);
  });

  it('takes a CSV dropped on the shared drop area and ignores a wrong file type', async () => {
    const { fixture, component, el } = create();
    const zone = el.querySelector('hq-file-drop .hq-drop') as HTMLElement;
    const drop = (files: File[]): void => {
      const event = new Event('drop', { cancelable: true });
      Object.defineProperty(event, 'dataTransfer', { value: { files } });
      zone.dispatchEvent(event);
    };

    drop([new File(['%PDF'], 'places.pdf', { type: 'application/pdf' })]);
    fixture.detectChanges();
    expect(component.fileName).toBe('');
    expect(el.querySelector('.hq-drop__error')).not.toBeNull();

    drop([new File([CSV], 'dropped.csv', { type: 'text/csv' })]);
    await vi.waitFor(() => expect(component.csv).toBe(CSV));
    expect(component.fileName).toBe('dropped.csv');
  });

  it('says so when the chosen file cannot be read', async () => {
    const { component } = create();
    vi.spyOn(FileReader.prototype, 'readAsText').mockImplementation(function (this: FileReader) {
      this.onerror?.(new ProgressEvent('error') as ProgressEvent<FileReader>);
    });

    component.onFilesPicked([new File([CSV], 'broken.csv', { type: 'text/csv' })]);

    await vi.waitFor(() => expect(notify.error).toHaveBeenCalledWith(`${KEY}errors.unreadable`));
    expect(component.csv).toBe('');
  });

  it('does not call the service for text that is empty or over the limits', () => {
    const { component } = create();

    component.dryRun();
    expect(notify.error).toHaveBeenCalledWith(`${KEY}errors.empty`);

    component.csv = `${IMPORT_CSV_HEADER}\n${'x\n'.repeat(IMPORT_MAX_ROWS + 1)}`;
    component.dryRun();
    expect(notify.error).toHaveBeenCalledWith(`${KEY}errors.tooManyRows`);
    expect(api.importLocations).not.toHaveBeenCalled();
  });

  it('runs a dry run first, shows the counts, and then allows applying', () => {
    const { component, el } = create();
    component.csv = CSV;

    component.dryRun();

    expect(api.importLocations).toHaveBeenCalledWith(CSV, true);
    expect(component.canApply).toBe(true);
    expect(el.textContent).toContain(`${KEY}ready`);
  });

  it('shows the per-row errors table and does not allow applying while there are errors', () => {
    api.importLocations.mockReturnValue(of(result({
      errorCount: 3, errorsTruncated: true,
      errors: [
        { row: 2, line: 3, field: 'parent_code', message: 'The parent DISTRICT AP-9 is not in the file and not in the reference' },
        { row: 5, line: 6, field: null, message: 'Another problem' },
      ],
    })));
    const { fixture, component, el } = create();
    component.csv = CSV;

    component.dryRun();
    fixture.detectChanges();

    expect(component.canApply).toBe(false);
    const rows = Array.from(el.querySelectorAll('tbody tr')).map((row) => Array.from(row.querySelectorAll('td')).slice(0, 4).map((cell) => cell.textContent?.trim()));
    expect(rows).toEqual([
      ['2', '3', 'parent_code', 'The parent DISTRICT AP-9 is not in the file and not in the reference'],
      ['5', '6', '—', 'Another problem'],
    ]);
    expect(el.textContent).toContain(`${KEY}errorCount`);
    expect(el.textContent).toContain(`${KEY}truncated`);
    component.apply();
    expect(api.importLocations).toHaveBeenCalledTimes(1);
  });

  it('needs a new check once the text has been edited after the dry run', () => {
    const { component } = create();
    component.csv = CSV;
    component.dryRun();
    expect(component.canApply).toBe(true);

    component.csv = `${CSV}IN,TOWN,C,Gamma,DISTRICT,AP-1,true\n`;
    component.onTextChange();

    expect(component.canApply).toBe(false);
    component.apply();
    expect(api.importLocations).toHaveBeenCalledTimes(1);
  });

  it('applies the checked text for real, reports the result and tells the list to reload', () => {
    const { component } = create();
    const imported = vi.fn();
    component.imported.subscribe(imported);
    component.csv = CSV;
    component.dryRun();
    api.importLocations.mockReturnValue(of(result({ dryRun: false, applied: true })));

    component.apply();

    expect(api.importLocations).toHaveBeenLastCalledWith(CSV, false);
    expect(notify.success).toHaveBeenCalledWith(`${KEY}applied`);
    expect(imported).toHaveBeenCalledTimes(1);
    expect(component.canApply).toBe(false);
  });

  it('shows the service\'s refusal of a request, such as a file that is too big', () => {
    api.importLocations.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'The CSV is too big: split it' } })));
    const { component } = create();
    component.csv = CSV;

    component.dryRun();

    expect(notify.error).toHaveBeenCalledWith('The CSV is too big: split it');
    expect(component.busy).toBe(false);
    expect(component.canApply).toBe(false);
  });
});
