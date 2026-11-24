import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import type { Mock } from 'vitest';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HQ_CONFIG, HqSharedLibConfig } from '../hq-common.config';
import { HqDocument } from '../hq-common.models';
import { HqDocumentService } from '../hq-document.service';
import { HqClose } from '../hq-close/hq-close';
import { HqOpenFile } from '../hq-open-file/hq-open-file';
import { HqMultiDocUpload } from './hq-multi-doc-upload';

const DMS = 'http://gw/api/v1/dms/documents';
const FILE_URL = 'https://s3.example/file';

function doc(id: string, overrides: Partial<HqDocument> = {}): HqDocument {
  return {
    id, category: 'general', ownerId: 'u1', originalFileName: `${id}.pdf`, storedFileName: 's',
    publicUrl: null, provider: 'LOCAL', versionId: null, checksumSha256: null, contentType: 'application/pdf',
    sizeBytes: 4096, status: 'ACTIVE', scanStatus: 'CLEAN', scanMessage: null, retentionUntil: null,
    legalHold: false, createdAt: '2026-01-01T00:00:00Z', ...overrides,
  };
}

function file(name = 'a.pdf', type = 'application/pdf', size = 100): File {
  return new File(['x'.repeat(size)], name, { type });
}

function changeEvent(files: File[] | null): { event: Event; input: HTMLInputElement } {
  const input = document.createElement('input');
  input.type = 'file';
  Object.defineProperty(input, 'files', { value: files, configurable: true });
  return { event: { target: input } as unknown as Event, input };
}

interface Setup {
  fixture: ComponentFixture<HqMultiDocUpload>;
  comp: HqMultiDocUpload;
  http: HttpTestingController;
  dialog: { open: Mock };
  service: HqDocumentService;
}

function setup(opts: { userId?: string | null; dialogAnswer?: boolean } = {}): Setup {
  const config: HqSharedLibConfig = {
    gatewayUrl: 'http://gw/api',
    adminApiUrl: 'http://admin/api/v1',
    currentUserId: () => ('userId' in opts ? opts.userId : 'u1'),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(opts.dialogAnswer ?? true) })) };
  TestBed.configureTestingModule({
    imports: [HqSharedLibModule],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: HQ_CONFIG, useValue: config },
      { provide: MatDialog, useValue: dialog },
    ],
  });
  const service = TestBed.inject(HqDocumentService);
  vi.spyOn(service, 'sha256').mockResolvedValue('sum');
  const fixture = TestBed.createComponent(HqMultiDocUpload);
  return { fixture, comp: fixture.componentInstance, http: TestBed.inject(HttpTestingController), dialog, service };
}

async function nextUpload(http: HttpTestingController) {
  return vi.waitFor(() => http.expectOne({ method: 'POST', url: DMS }));
}

function flushFetch(http: HttpTestingController, id: string): void {
  http.expectOne({ method: 'POST', url: `${DMS}/metadata/${id}/download-url` })
    .flush({ success: true, responseObject: { url: FILE_URL, expiresAt: '' } });
  http.expectOne(FILE_URL).flush(new Blob(['f']));
}

function el(fixture: ComponentFixture<unknown>, selector: string): HTMLElement | null {
  return fixture.nativeElement.querySelector(selector);
}

describe('HqMultiDocUpload', () => {
  beforeEach(() => {
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks();
  });

  describe('form control contract', () => {
    it('starts empty, validates required, and accepts null/undefined values', () => {
      const { comp } = setup();
      expect(comp.value).toEqual([]);
      expect(comp.validate()).toBeNull();
      comp.isRequired = true;
      expect(comp.validate()).toEqual({ required: true });
      comp.value = [doc('a')];
      expect(comp.validate()).toBeNull();
      comp.value = undefined;
      expect(comp.value).toEqual([]);
      comp.writeValue(null);
      expect(comp.value).toEqual([]);
    });

    it('writeValue does not notify the form; setDisabledState toggles disabled', () => {
      const { comp } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      comp.writeValue([doc('a')]);
      expect(comp.value.map((d) => d.id)).toEqual(['a']);
      expect(onChange).not.toHaveBeenCalled();
      comp.setDisabledState(true);
      expect(comp.disabled).toBe(true);
    });

    it('works as a reactive form control with the required validator', () => {
      @Component({
        standalone: true,
        imports: [HqSharedLibModule, ReactiveFormsModule],
        template: '<hq-multi-doc-upload [formControl]="ctrl" [isRequired]="true"></hq-multi-doc-upload>',
      })
      class Host {
        ctrl = new FormControl<HqDocument[]>([], { nonNullable: true });
      }
      TestBed.configureTestingModule({
        imports: [Host],
        providers: [
          provideHttpClient(), provideHttpClientTesting(),
          { provide: HQ_CONFIG, useValue: { gatewayUrl: 'http://gw/api', adminApiUrl: '', currentUserId: () => 'u1' } },
        ],
      });
      const host = TestBed.createComponent(Host);
      host.detectChanges();
      expect(host.componentInstance.ctrl.errors).toEqual({ required: true });
      host.componentInstance.ctrl.setValue([doc('a')]);
      host.detectChanges();
      expect(host.componentInstance.ctrl.valid).toBe(true);
      expect(host.nativeElement.querySelector('.hq-multi__file')?.textContent).toContain('a.pdf');
    });

    it('atLimit only applies when maxFiles is set', () => {
      const { comp } = setup();
      comp.writeValue([doc('a'), doc('b')]);
      expect(comp.atLimit).toBe(false);
      comp.maxFiles = 3;
      expect(comp.atLimit).toBe(false);
      comp.maxFiles = 2;
      expect(comp.atLimit).toBe(true);
    });

    it('customNameInvalid flags names outside letters, digits, - and _', () => {
      const { comp } = setup();
      expect(comp.customNameInvalid).toBe(false);
      comp.customName = 'Front_side-1';
      expect(comp.customNameInvalid).toBe(false);
      comp.customName = 'bad name!';
      expect(comp.customNameInvalid).toBe(true);
    });
  });

  describe('auto load', () => {
    it('lists every document filed under category + owner', () => {
      const { fixture, comp, http } = setup();
      const onChange = vi.fn();
      const onTouched = vi.fn();
      comp.registerOnChange(onChange);
      comp.registerOnTouched(onTouched);
      fixture.componentRef.setInput('ownerId', 'veh-1');
      fixture.componentRef.setInput('category', 'vehicle-photos');
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      expect(req.request.params.get('ownerId')).toBe('veh-1');
      expect(req.request.params.get('category')).toBe('vehicle-photos');
      req.flush({ success: true, responseObject: [doc('a'), doc('b')] });
      expect(comp.value.map((d) => d.id)).toEqual(['a', 'b']);
      expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ id: 'a' }), expect.objectContaining({ id: 'b' })]);
      expect(onTouched).toHaveBeenCalled();
    });

    it('does not load when autoLoad is off or an unrelated input changes', () => {
      const { fixture, comp, http } = setup();
      fixture.componentRef.setInput('category', 'x');
      fixture.detectChanges();
      comp.autoLoad = true;
      fixture.componentRef.setInput('label', 'y');
      fixture.detectChanges();
      http.expectNone((r) => r.url === `${DMS}/metadata`);
    });

    it('writeValue with an empty list loads; with documents it does not', () => {
      const { comp, http } = setup();
      comp.autoLoad = true;
      comp.writeValue([doc('a')]);
      http.expectNone((r) => r.url === `${DMS}/metadata`);
      comp.writeValue([]);
      http.expectOne((r) => r.url === `${DMS}/metadata`).flush({ success: true, responseObject: [] });
      expect(comp.value).toEqual([]);
    });

    it('keeps a value that arrived before the list, and swallows load errors', () => {
      const { comp, http } = setup();
      comp.autoLoad = true;
      comp.writeValue(null);
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      comp.writeValue([doc('mine')]);
      req.flush({ success: true, responseObject: [doc('server')] });
      expect(comp.value.map((d) => d.id)).toEqual(['mine']);

      comp.writeValue([]);
      http.expectOne((r) => r.url === `${DMS}/metadata`).flush('x', { status: 500, statusText: 'e' });
      expect(comp.value).toEqual([]);
      expect(comp.errorMessage).toBe('');
    });

    it('skips loading without an owner or a category', () => {
      const { comp, http } = setup({ userId: null });
      comp.autoLoad = true;
      comp.writeValue(null);
      comp.ownerId = 'o';
      comp.category = '';
      comp.writeValue(null);
      http.expectNone((r) => r.url === `${DMS}/metadata`);
    });
  });

  describe('pickFile', () => {
    it('opens the picker only when enabled, idle, under the limit and with a valid name', () => {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      comp.pickFile();
      expect(click).toHaveBeenCalledTimes(1);

      comp.disabled = true;
      comp.pickFile();
      comp.disabled = false;
      comp.loading = true;
      comp.pickFile();
      comp.loading = false;
      comp.customName = 'bad name';
      comp.pickFile();
      comp.customName = '';
      comp.maxFiles = 1;
      comp.writeValue([doc('a')]);
      comp.pickFile();
      expect(click).toHaveBeenCalledTimes(1);
    });

    it('is safe before the view exists', () => {
      const { comp } = setup();
      expect(() => comp.pickFile()).not.toThrow();
    });
  });

  describe('uploading', () => {
    it('uploads each selected file in turn, appending and emitting each', async () => {
      const { comp, http } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);
      comp.category = 'docs';
      const { event, input } = changeEvent([file('one.pdf'), file('two.png', 'image/png')]);
      const done = comp.onFilesSelected(event);
      expect(input.value).toBe('');
      expect(comp.loading).toBe(true);

      const first = await nextUpload(http);
      const body = first.request.body as FormData;
      expect((body.get('file') as File).name).toBe('one.pdf');
      expect(body.get('category')).toBe('docs');
      expect(body.get('ownerId')).toBe('u1');
      expect(body.get('checksum')).toBe('sum');
      first.flush({ success: true, responseObject: doc('one') });

      const second = await nextUpload(http);
      expect(((second.request.body as FormData).get('file') as File).name).toBe('two.png');
      second.flush({ success: true, responseObject: doc('two') });
      await done;

      expect(comp.value.map((d) => d.id)).toEqual(['one', 'two']);
      expect(uploaded).toHaveBeenCalledTimes(2);
      expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ id: 'one' }), expect.objectContaining({ id: 'two' })]);
      expect(comp.loading).toBe(false);
    });

    it('ignores an empty selection, a missing file list, and selections while disabled', async () => {
      const { comp, http } = setup();
      await comp.onFilesSelected(changeEvent([]).event);
      await comp.onFilesSelected(changeEvent(null).event);
      comp.disabled = true;
      await comp.onFilesSelected(changeEvent([file()]).event);
      http.expectNone(DMS);
      expect(comp.loading).toBe(false);
    });

    it('rejects when no owner is available', async () => {
      const { comp, http } = setup({ userId: undefined });
      const rejected = vi.fn();
      comp.rejected.subscribe(rejected);
      await comp.onFilesSelected(changeEvent([file()]).event);
      expect(comp.errorMessage).toBe('No owner available for these documents.');
      expect(rejected).toHaveBeenCalledWith('No owner available for these documents.');
      expect(comp.loading).toBe(false);
      http.expectNone(DMS);
    });

    it('skips invalid files with a message and still uploads the valid ones', async () => {
      const { comp, http } = setup();
      comp.writeValue([doc('x', { originalFileName: 'Dup.PDF' })]);
      const big = file('big.pdf');
      Object.defineProperty(big, 'size', { value: 20 * 1024 * 1024 });
      const rejected = vi.fn();
      comp.rejected.subscribe(rejected);
      const done = comp.onFilesSelected(changeEvent([
        file('a.exe', 'application/x-msdownload'), file('tiny.pdf', 'application/pdf', 5), big, file('dup.pdf'), file('ok.pdf'),
      ]).event);
      (await nextUpload(http)).flush({ success: true, responseObject: doc('ok') });
      await done;
      expect(rejected.mock.calls.map((c) => c[0])).toEqual([
        'a.exe: please upload PDF, JPEG, PNG files.',
        'tiny.pdf is too small (minimum 50 bytes).',
        'big.pdf exceeds the 9.9 MB limit.',
        'dup.pdf is already attached.',
      ]);
      expect(comp.value.map((d) => d.id)).toEqual(['x', 'ok']);
    });

    it('has no size limit when maxSizeMB is 0', async () => {
      const { comp, http } = setup();
      comp.maxSizeMB = 0;
      const big = file('big.pdf');
      Object.defineProperty(big, 'size', { value: 50 * 1024 * 1024 });
      const done = comp.onFilesSelected(changeEvent([big]).event);
      (await nextUpload(http)).flush({ success: true, responseObject: doc('big') });
      await done;
      expect(comp.errorMessage).toBe('');
    });

    it('stops at maxFiles with a message', async () => {
      const { comp, http } = setup();
      comp.maxFiles = 2;
      comp.writeValue([doc('a')]);
      const done = comp.onFilesSelected(changeEvent([file('b.pdf'), file('c.pdf')]).event);
      (await nextUpload(http)).flush({ success: true, responseObject: doc('b') });
      await done;
      http.expectNone(DMS);
      expect(comp.errorMessage).toBe('At most 2 documents can be attached.');
      expect(comp.value.map((d) => d.id)).toEqual(['a', 'b']);
    });

    it('renames with the custom name, suffixing later files, then clears the name', async () => {
      const { comp, http } = setup();
      comp.customName = 'rc_front';
      const done = comp.onFilesSelected(changeEvent([file('scan.PDF'), file('noext', '', 100)]).event);
      const first = await nextUpload(http);
      const renamed = (first.request.body as FormData).get('file') as File;
      expect(renamed.name).toBe('rc_front.PDF');
      expect(renamed.type).toBe('application/pdf');
      first.flush({ success: true, responseObject: doc('1') });
      await done; // second file has no extension and an empty type -> rejected by accept
      expect(comp.errorMessage).toBe('rc_front_2: please upload PDF, JPEG, PNG files.');
      expect(comp.customName).toBe('');
    });

    it('suffixes the second file with _2 and keeps its extension', async () => {
      const { comp, http } = setup();
      comp.customName = 'pic';
      const done = comp.onFilesSelected(changeEvent([file('a.png', 'image/png'), file('b.jpg', 'image/jpeg')]).event);
      (await nextUpload(http)).flush({ success: true, responseObject: doc('1', { originalFileName: 'pic.png' }) });
      const second = await nextUpload(http);
      expect(((second.request.body as FormData).get('file') as File).name).toBe('pic_2.jpg');
      second.flush({ success: true, responseObject: doc('2') });
      await done;
    });

    it('ignores an invalid custom name', async () => {
      const { comp, http } = setup();
      comp.customName = 'bad name';
      const done = comp.onFilesSelected(changeEvent([file('orig.pdf')]).event);
      const req = await nextUpload(http);
      expect(((req.request.body as FormData).get('file') as File).name).toBe('orig.pdf');
      req.flush({ success: true, responseObject: doc('1') });
      await done;
    });

    it.each([
      [{ message: 'Infected' }, 'Infected'],
      [null, 'Upload of a.pdf failed.'],
    ])('reports a failed upload (%j) and continues', async (body, message) => {
      const { comp, http } = setup();
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);
      const done = comp.onFilesSelected(changeEvent([file('a.pdf'), file('b.pdf')]).event);
      (await nextUpload(http)).flush(body, { status: 400, statusText: 'bad' });
      await vi.waitFor(() => expect(comp.errorMessage).toBe(message));
      (await nextUpload(http)).flush({ success: true, responseObject: doc('b') });
      await done;
      expect(uploaded).toHaveBeenCalledTimes(1);
      expect(comp.value.map((d) => d.id)).toEqual(['b']);
    });
  });

  describe('open', () => {
    it.each(['image/jpeg', 'application/pdf'])('previews %s in the open-file dialog', (contentType) => {
      const { comp, http, dialog } = setup();
      comp.open(doc('a', { contentType }));
      flushFetch(http, 'a');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({
        data: { blob: expect.any(Blob), fileName: 'a.pdf' }, maxWidth: '95vw', autoFocus: false,
      }));
    });

    it('downloads other types', () => {
      const { comp, http, dialog } = setup();
      comp.open(doc('a', { contentType: 'text/csv' }));
      flushFetch(http, 'a');
      expect(dialog.open).not.toHaveBeenCalled();
      expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
    });

    it('reports a fetch failure', () => {
      const { comp, http } = setup();
      comp.open(doc('a'));
      http.expectOne(`${DMS}/metadata/a/download-url`).flush('x', { status: 500, statusText: 'e' });
      expect(comp.errorMessage).toBe('Unable to open the document.');
    });
  });

  describe('remove', () => {
    it('removes from the list and emits without touching DMS by default', async () => {
      const { comp, http, dialog } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      comp.writeValue([doc('a'), doc('b')]);
      await comp.remove(doc('a'));
      expect(comp.value.map((d) => d.id)).toEqual(['b']);
      expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ id: 'b' })]);
      expect(removed).toHaveBeenCalledWith(expect.objectContaining({ id: 'a' }));
      expect(dialog.open).not.toHaveBeenCalled();
      http.expectNone(`${DMS}/metadata/a`);
    });

    it('does nothing when disabled or not removable', async () => {
      const { comp } = setup();
      comp.writeValue([doc('a')]);
      comp.disabled = true;
      await comp.remove(doc('a'));
      comp.disabled = false;
      comp.removable = false;
      await comp.remove(doc('a'));
      expect(comp.value).toHaveLength(1);
    });

    it('asks first and keeps the document on No', async () => {
      const { comp, http, dialog } = setup({ dialogAnswer: false });
      comp.deleteOnRemove = true;
      comp.writeValue([doc('a')]);
      await comp.remove(doc('a'));
      expect(dialog.open).toHaveBeenCalledWith(HqClose, expect.objectContaining({
        data: { confirm: true, message: 'Delete a.pdf permanently?' }, disableClose: true,
      }));
      http.expectNone(`${DMS}/metadata/a`);
      expect(comp.value).toHaveLength(1);
    });

    it('deletes in DMS on Yes', async () => {
      const { comp, http } = setup();
      comp.deleteOnRemove = true;
      comp.writeValue([doc('a')]);
      const done = comp.remove(doc('a'));
      (await vi.waitFor(() => http.expectOne({ method: 'DELETE', url: `${DMS}/metadata/a` }))).flush(null, { status: 204, statusText: 'ok' });
      await done;
      expect(comp.value).toEqual([]);
    });

    it.each([
      [{ message: 'Legal hold' }, 'Legal hold'],
      [null, 'Unable to delete the document.'],
    ])('keeps the document when the delete fails (%j)', async (body, message) => {
      const { comp, http } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      comp.deleteOnRemove = true;
      comp.writeValue([doc('a')]);
      const done = comp.remove(doc('a'));
      (await vi.waitFor(() => http.expectOne(`${DMS}/metadata/a`))).flush(body, { status: 409, statusText: 'c' });
      await done;
      expect(comp.errorMessage).toBe(message);
      expect(comp.value).toHaveLength(1);
      expect(removed).not.toHaveBeenCalled();
    });
  });

  describe('with its own storage (uploader, fetcher and remover instead of DMS)', () => {
    it('uploads each file through the uploader without an owner and never calls DMS', async () => {
      const { fixture, comp, http } = setup({ userId: null });
      const uploader = vi.fn((f: File) => of(doc(f.name, { originalFileName: f.name, contentType: f.type })));
      fixture.componentRef.setInput('uploader', uploader);
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);

      await comp.onFilesPicked([file('a.png', 'image/png'), file('b.png', 'image/png')]);

      expect(uploader).toHaveBeenCalledTimes(2);
      expect(comp.value.map((d) => d.id)).toEqual(['a.png', 'b.png']);
      expect(uploaded).toHaveBeenCalledTimes(2);
      expect(comp.errorMessage).toBe('');
      http.expectNone(() => true);
    });

    it('reports an uploader failure and keeps going', async () => {
      const { fixture, comp } = setup();
      let calls = 0;
      fixture.componentRef.setInput('uploader', (f: File) =>
        ++calls === 1 ? throwError(() => ({ error: { message: 'Too many photos' } })) : of(doc('ok', { originalFileName: f.name })));
      fixture.detectChanges();

      await comp.onFilesPicked([file('a.png', 'image/png'), file('b.png', 'image/png')]);

      expect(comp.value.map((d) => d.id)).toEqual(['ok']);
      expect(comp.errorMessage).toBe('Too many photos');
    });

    it('opens through the fetcher', () => {
      const { fixture, comp, dialog } = setup();
      const fetcher = vi.fn(() => of(new Blob(['i'], { type: 'image/png' })));
      fixture.componentRef.setInput('fetcher', fetcher);
      fixture.detectChanges();

      comp.open(doc('p1', { originalFileName: 'p1.png' }));

      expect(fetcher).toHaveBeenCalled();
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'p1.png' }) }));
    });

    it('asks, then deletes through the remover', async () => {
      const { fixture, comp, dialog, http } = setup({ dialogAnswer: true });
      const remover = vi.fn(() => of(null));
      fixture.componentRef.setInput('remover', remover);
      comp.writeValue([doc('p1'), doc('p2')]);
      fixture.detectChanges();

      await comp.remove(comp.value[0]);

      expect(dialog.open).toHaveBeenCalledWith(HqClose, expect.anything());
      expect(remover).toHaveBeenCalledWith(expect.objectContaining({ id: 'p1' }));
      expect(comp.value.map((d) => d.id)).toEqual(['p2']);
      http.expectNone(() => true);
    });

    it('never reads a file it stored itself from DMS: without a fetcher there is no View and no picture', () => {
      const { fixture, comp, http, dialog } = setup();
      fixture.componentRef.setInput('uploader', () => of(doc('p1')));
      fixture.componentRef.setInput('thumbnails', true);
      comp.writeValue([doc('p1', { contentType: 'image/png' })]);
      fixture.detectChanges();

      expect(comp.canOpen).toBe(false);
      expect(fixture.nativeElement.querySelector('.hq-multi__view')).toBeNull();
      expect((fixture.nativeElement.querySelector('.hq-multi__thumb') as HTMLButtonElement).disabled).toBe(true);
      comp.open(comp.value[0]);
      expect(dialog.open).not.toHaveBeenCalled();
      http.expectNone(() => true);

      fixture.componentRef.setInput('fetcher', () => of(new Blob(['i'], { type: 'image/png' })));
      fixture.detectChanges();
      expect(comp.canOpen).toBe(true);
      expect(fixture.nativeElement.querySelector('.hq-multi__view')).not.toBeNull();
    });

    it('keeps the document when the remover fails', async () => {
      const { fixture, comp } = setup({ dialogAnswer: true });
      fixture.componentRef.setInput('remover', () => throwError(() => ({ error: { message: 'Cannot remove' } })));
      comp.writeValue([doc('p1')]);

      await comp.remove(comp.value[0]);

      expect(comp.value.map((d) => d.id)).toEqual(['p1']);
      expect(comp.errorMessage).toBe('Cannot remove');
    });
  });

  describe('thumbnails', () => {
    it('shows a picture for each image, a file icon for anything else, and releases pictures of removed documents', () => {
      const { fixture, comp } = setup();
      const create = vi.spyOn(URL, 'createObjectURL').mockImplementation(() => 'blob:thumb');
      const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
      fixture.componentRef.setInput('fetcher', () => of(new Blob(['i'], { type: 'image/png' })));
      fixture.componentRef.setInput('thumbnails', true);
      comp.writeValue([doc('p1', { contentType: 'image/png' }), doc('d1')]);
      fixture.detectChanges();

      const host: HTMLElement = fixture.nativeElement;
      expect(host.querySelector('.hq-multi__list--gallery')).not.toBeNull();
      expect(host.querySelectorAll('.hq-multi__thumb img').length).toBe(1);
      expect(host.querySelectorAll('.hq-multi__thumb mat-icon').length).toBe(1);
      expect(create).toHaveBeenCalledTimes(1);

      comp.writeValue([doc('d1')]);
      expect(revoke).toHaveBeenCalledWith('blob:thumb');
      expect(comp.thumbs).toEqual({});

      comp.writeValue([doc('p2', { contentType: 'image/png' })]);
      comp.ngOnDestroy();
      expect(revoke).toHaveBeenCalledTimes(2);
      create.mockRestore();
      revoke.mockRestore();
    });

    it('keeps the icon when a picture cannot be read, and loads nothing without thumbnails', () => {
      const { fixture, comp } = setup();
      const fetcher = vi.fn(() => throwError(() => new Error('down')));
      fixture.componentRef.setInput('fetcher', fetcher);
      comp.writeValue([doc('p1', { contentType: 'image/png' })]);
      fixture.detectChanges();
      expect(fetcher).not.toHaveBeenCalled();

      fixture.componentRef.setInput('thumbnails', true);
      fixture.detectChanges();
      expect(fetcher).toHaveBeenCalledTimes(1);
      expect(comp.thumbs).toEqual({});
      expect(fixture.nativeElement.querySelector('.hq-multi__thumb mat-icon')).not.toBeNull();
    });
  });

  describe('template', () => {
    it('renders the picker with label, required marker, hint and the file-name field', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('label', 'Photos');
      fixture.componentRef.setInput('isRequired', true);
      fixture.componentRef.setInput('maxFiles', 4);
      fixture.componentRef.setInput('inputId', 'ph');
      fixture.detectChanges();
      expect(el(fixture, '#phBtn')?.textContent).toContain('Choose files');
      expect(el(fixture, '.hq-drop__label')?.textContent).toContain('Photos');
      expect(el(fixture, '.hq-drop__required')).not.toBeNull();
      expect(el(fixture, '.hq-drop__hint')?.textContent).toContain('PDF, JPEG, PNG · up to 9.9 MB each · at most 4 files');
      expect(el(fixture, '.hq-multi__name input')).not.toBeNull();
      expect((el(fixture, 'input[type=file]') as HTMLInputElement).multiple).toBe(true);
    });

    it('omits the limit text, the name field and multiple when not configured', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('allowCustomName', false);
      fixture.componentRef.setInput('multiple', false);
      fixture.detectChanges();
      expect(el(fixture, '.hq-drop__hint')?.textContent?.trim()).toBe('PDF, JPEG, PNG · up to 9.9 MB');
      expect(el(fixture, '.hq-multi__name')).toBeNull();
      expect(el(fixture, '.hq-drop__required')).toBeNull();
      expect((el(fixture, 'input[type=file]') as HTMLInputElement).multiple).toBe(false);
    });

    it('shows the invalid-name hint and disables the button for a bad name', async () => {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      const nameInput = el(fixture, '.hq-multi__name input') as HTMLInputElement;
      nameInput.value = 'bad name';
      nameInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(comp.customName).toBe('bad name');
      expect(el(fixture, '.hq-multi__invalid')?.textContent).toContain('Letters, digits, - and _ only');
      expect((el(fixture, '#hqMultiDocUploadBtn') as HTMLButtonElement).disabled).toBe(true);
    });

    it('the button opens the picker and the change event uploads, with a spinner meanwhile', async () => {
      const { fixture, comp, http } = setup();
      fixture.detectChanges();
      const input = el(fixture, 'input[type=file]') as HTMLInputElement;
      const click = vi.spyOn(input, 'click').mockImplementation(() => {});
      (el(fixture, '#hqMultiDocUploadBtn') as HTMLButtonElement).click();
      expect(click).toHaveBeenCalled();

      Object.defineProperty(input, 'files', { value: [file('x.pdf')], configurable: true });
      input.dispatchEvent(new Event('change'));
      const req = await nextUpload(http);
      fixture.detectChanges();
      expect(el(fixture, '.hq-drop__spinner')).not.toBeNull();
      req.flush({ success: true, responseObject: doc('x', { sizeBytes: 2 * 1024 * 1024 }) });
      await vi.waitFor(() => expect(comp.loading).toBe(false));
      fixture.detectChanges();
      expect(el(fixture, '.hq-drop__spinner')).toBeNull();
      expect(el(fixture, '.hq-multi__file')?.textContent).toContain('x.pdf');
      expect(el(fixture, '.hq-multi__size')?.textContent).toContain('2.0 MB');
    });

    it('chips open on click and remove through their remove button', async () => {
      const { fixture, comp, http, dialog } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      fixture.componentRef.setInput('value', [doc('a'), doc('b')]);
      fixture.detectChanges();
      const chips = fixture.nativeElement.querySelectorAll('.hq-multi__chip');
      expect(chips.length).toBe(2);
      (chips[1] as HTMLElement).click();
      flushFetch(http, 'b');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.anything());

      (el(fixture, 'button[aria-label="Remove a.pdf"]') as HTMLButtonElement).click();
      await vi.waitFor(() => expect(removed).toHaveBeenCalledWith(expect.objectContaining({ id: 'a' })));
      http.expectNone((r) => r.url.includes('download-url'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.hq-multi__chip').length).toBe(1);
    });

    it('gives every document a View button that opens it in the viewer, also when read-only', () => {
      const { fixture, http, dialog } = setup();
      fixture.componentRef.setInput('value', [doc('a'), doc('b')]);
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      const views = fixture.nativeElement.querySelectorAll('.hq-multi__view');
      expect(views.length).toBe(2);
      expect(el(fixture, 'button[aria-label="View b.pdf"]')).toBe(views[1]);

      (views[1] as HTMLButtonElement).click();
      flushFetch(http, 'b');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'b.pdf' }) }));
    });

    it('when disabled hides the picker and remove buttons, and says "No documents" when empty', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-multi__picker')).toBeNull();
      expect(el(fixture, '.hq-multi__hint')?.textContent).toContain('No documents');
      fixture.componentRef.setInput('value', [doc('a')]);
      fixture.detectChanges();
      expect(el(fixture, '.hq-multi__chip')).not.toBeNull();
      expect(el(fixture, 'button[aria-label="Remove a.pdf"]')).toBeNull();
    });

    it('shows the error message', async () => {
      const { fixture, comp } = setup({ userId: null });
      fixture.detectChanges();
      await comp.onFilesSelected(changeEvent([file()]).event);
      fixture.detectChanges();
      expect(el(fixture, '.hq-multi__error')?.textContent).toContain('No owner available for these documents.');
    });
  });
  describe('drop area', () => {
    it('uploads every file dropped on it', async () => {
      const { fixture, comp, http } = setup();
      fixture.detectChanges();
      const zone = el(fixture, '.hq-drop') as HTMLElement;
      const event = new Event('drop') as DragEvent;
      Object.defineProperty(event, 'dataTransfer', { value: { files: [file('one.pdf'), file('two.pdf')] } });
      zone.dispatchEvent(event);
      const first = await nextUpload(http);
      expect(((first.request.body as FormData).get('file') as File).name).toBe('one.pdf');
      first.flush({ success: true, responseObject: doc('one') });
      const second = await nextUpload(http);
      second.flush({ success: true, responseObject: doc('two') });
      await vi.waitFor(() => expect(comp.value.length).toBe(2));
    });

    it('is disabled once the file limit is reached', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('maxFiles', 1);
      fixture.componentRef.setInput('value', [doc('a')]);
      fixture.detectChanges();
      expect((el(fixture, '#hqMultiDocUploadBtn') as HTMLButtonElement).disabled).toBe(true);
    });
  });
  describe('main document (primaryId, primarySetter)', () => {
    it('marks the main document with the badge text and offers "make main" on the others only', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('value', [doc('a'), doc('b')]);
      fixture.componentRef.setInput('primaryId', 'a');
      fixture.componentRef.setInput('primaryLabel', 'Main photo');
      fixture.componentRef.setInput('makePrimaryLabel', 'Make main photo');
      fixture.componentRef.setInput('primarySetter', () => of(null));
      fixture.detectChanges();
      const items = fixture.nativeElement.querySelectorAll('.hq-multi__item');
      expect(items[0].querySelector('.hq-multi__badge')?.textContent).toContain('Main photo');
      expect(items[0].querySelector('.hq-multi__primary')).toBeNull();
      expect(items[1].querySelector('.hq-multi__badge')).toBeNull();
      expect(items[1].querySelector('.hq-multi__primary')?.getAttribute('aria-label')).toBe('Make main photo');
    });

    it('shows no badge without a label and no button without a setter or when disabled', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('value', [doc('a'), doc('b')]);
      fixture.componentRef.setInput('primaryId', 'a');
      fixture.detectChanges();
      expect(el(fixture, '.hq-multi__badge')).toBeNull();
      expect(el(fixture, '.hq-multi__primary')).toBeNull();
      fixture.componentRef.setInput('primarySetter', () => of(null));
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-multi__primary')).toBeNull();
      expect(comp.canMakePrimary(doc('b'))).toBe(false);
    });

    it('makes a document the main one through the setter, then moves the badge and tells the screen', async () => {
      const { fixture, comp } = setup();
      const setter = vi.fn(() => of(null));
      const changed = vi.fn();
      comp.primaryChanged.subscribe(changed);
      fixture.componentRef.setInput('value', [doc('a'), doc('b')]);
      fixture.componentRef.setInput('primaryId', 'a');
      fixture.componentRef.setInput('primarySetter', setter);
      fixture.detectChanges();
      await comp.makePrimary(doc('b'));
      expect(setter).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
      expect(comp.primaryId).toBe('b');
      expect(changed).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
      expect(comp.loading).toBe(false);
    });

    it('does nothing for the main document itself or without a setter', async () => {
      const { comp } = setup();
      const setter = vi.fn(() => of(null));
      comp.primaryId = 'a';
      await comp.makePrimary(doc('b'));
      comp.primarySetter = setter;
      await comp.makePrimary(doc('a'));
      expect(setter).not.toHaveBeenCalled();
    });

    it('keeps the main document when the setter fails, showing the server reason when there is one', async () => {
      const { comp } = setup();
      const changed = vi.fn();
      comp.primaryChanged.subscribe(changed);
      comp.primaryId = 'a';
      comp.primarySetter = () => throwError(() => ({ error: { message: 'Not allowed' } }));
      await comp.makePrimary(doc('b'));
      expect(comp.primaryId).toBe('a');
      expect(comp.errorMessage).toBe('Not allowed');
      expect(changed).not.toHaveBeenCalled();
      comp.errorMessage = '';
      comp.primarySetter = () => throwError(() => new Error('down'));
      await comp.makePrimary(doc('b'));
      expect(comp.errorMessage).toBe('');
      expect(comp.loading).toBe(false);
    });
  });
});
