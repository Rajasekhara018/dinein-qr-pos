import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import type { Mock } from 'vitest';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HQ_CONFIG, HQ_IMAGE_CROPPER, HqSharedLibConfig } from '../hq-common.config';
import { HqDocument, HqImageCropper } from '../hq-common.models';
import { HqDocumentService } from '../hq-document.service';
import { HqClose } from '../hq-close/hq-close';
import { HqOpenFile } from '../hq-open-file/hq-open-file';
import { HqDocUpload } from './hq-doc-upload';
import { hqDocumentFromFile } from '../hq-file.util';
import { HQ_UPLOAD_DEFAULT_TEXTS, HQ_UPLOAD_TEXTS, HqUploadTexts } from '../hq-file-drop/hq-upload.texts';
import { HqFileDrop } from '../hq-file-drop/hq-file-drop';
import { By } from '@angular/platform-browser';

const DMS = 'http://gw/api/v1/dms/documents';
const FILE_URL = 'https://s3.example/file';

function doc(overrides: Partial<HqDocument> = {}): HqDocument {
  return {
    id: 'd1', category: 'general', ownerId: 'u1', originalFileName: 'license.pdf', storedFileName: 's',
    publicUrl: null, provider: 'LOCAL', versionId: null, checksumSha256: null, contentType: 'application/pdf',
    sizeBytes: 2048, status: 'ACTIVE', scanStatus: 'CLEAN', scanMessage: null, retentionUntil: null,
    legalHold: false, createdAt: '2026-01-01T00:00:00Z', ...overrides,
  };
}

function file(name = 'a.pdf', type = 'application/pdf', size = 100): File {
  return new File(['x'.repeat(size)], name, { type });
}

function changeEvent(files: File[]): { event: Event; input: HTMLInputElement } {
  const input = document.createElement('input');
  input.type = 'file';
  Object.defineProperty(input, 'files', { value: files, configurable: true });
  return { event: { target: input } as unknown as Event, input };
}

interface Setup {
  fixture: ComponentFixture<HqDocUpload>;
  comp: HqDocUpload;
  http: HttpTestingController;
  dialog: { open: Mock };
  service: HqDocumentService;
  config: HqSharedLibConfig;
}

function setup(opts: { userId?: string | null; cropper?: HqImageCropper | null; dialogAnswer?: boolean; texts?: HqUploadTexts } = {}): Setup {
  const config: HqSharedLibConfig = {
    gatewayUrl: 'http://gw/api/',
    adminApiUrl: 'http://admin/api/v1',
    currentUserId: () => ('userId' in opts ? opts.userId : 'u1'),
  };
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(opts.dialogAnswer ?? true) })) };
  const providers: unknown[] = [
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: HQ_CONFIG, useValue: config },
    { provide: MatDialog, useValue: dialog },
  ];
  if (opts.cropper !== undefined) providers.push({ provide: HQ_IMAGE_CROPPER, useValue: opts.cropper });
  if (opts.texts) providers.push({ provide: HQ_UPLOAD_TEXTS, useValue: opts.texts });
  TestBed.configureTestingModule({ imports: [HqSharedLibModule], providers: providers as never[] });
  const service = TestBed.inject(HqDocumentService);
  vi.spyOn(service, 'sha256').mockResolvedValue('sum');
  const fixture = TestBed.createComponent(HqDocUpload);
  return { fixture, comp: fixture.componentInstance, http: TestBed.inject(HttpTestingController), dialog, service, config };
}

function flushFetch(http: HttpTestingController, id: string, blob = new Blob(['img'])): void {
  http.expectOne({ method: 'POST', url: `${DMS}/metadata/${id}/download-url` })
    .flush({ success: true, responseObject: { url: FILE_URL, expiresAt: '' } });
  http.expectOne(FILE_URL).flush(blob);
}

function el(fixture: ComponentFixture<unknown>, selector: string): HTMLElement | null {
  return fixture.nativeElement.querySelector(selector);
}

describe('HqDocUpload', () => {
  let createUrl: Mock;
  let revokeUrl: Mock;

  beforeEach(() => {
    createUrl = vi.fn(() => 'blob:preview');
    revokeUrl = vi.fn();
    Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks();
  });

  describe('form control contract', () => {
    it('is empty and valid by default, and reports required only when isRequired', () => {
      const { comp } = setup();
      expect(comp.empty).toBe(true);
      expect(comp.value).toBeNull();
      expect(comp.validate()).toBeNull();
      comp.isRequired = true;
      expect(comp.validate()).toEqual({ required: true });
      comp.writeValue(doc());
      expect(comp.validate()).toBeNull();
    });

    it('writeValue sets the value without notifying the form', () => {
      const { comp } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      comp.writeValue(doc());
      expect(comp.value?.id).toBe('d1');
      expect(onChange).not.toHaveBeenCalled();
    });

    it('the value input accepts undefined as empty', () => {
      const { comp } = setup();
      comp.value = doc();
      expect(comp.empty).toBe(false);
      comp.value = undefined;
      expect(comp.value).toBeNull();
    });

    it('setDisabledState toggles disabled', () => {
      const { comp } = setup();
      comp.setDisabledState(true);
      expect(comp.disabled).toBe(true);
      comp.setDisabledState(false);
      expect(comp.disabled).toBe(false);
    });

    it('works as a reactive form control with the required validator', () => {
      @Component({
        standalone: true,
        imports: [HqSharedLibModule, ReactiveFormsModule],
        template: '<hq-doc-upload [formControl]="ctrl" [isRequired]="true"></hq-doc-upload>',
      })
      class Host {
        ctrl = new FormControl<HqDocument | null>(null);
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
      host.componentInstance.ctrl.setValue(doc());
      host.detectChanges();
      expect(host.componentInstance.ctrl.valid).toBe(true);
      expect(host.nativeElement.querySelector('.hq-doc__name')?.textContent).toContain('license.pdf');
    });

    it('formats sizes through the shared helper', () => {
      const { comp } = setup();
      expect(comp.formatSize(2048)).toBe('2 KB');
    });
  });

  describe('auto load', () => {
    it('loads the latest document for category + current user when autoLoad is set', () => {
      const { fixture, comp, http } = setup();
      const onChange = vi.fn();
      const onTouched = vi.fn();
      comp.registerOnChange(onChange);
      comp.registerOnTouched(onTouched);
      fixture.componentRef.setInput('category', 'driver-license');
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      expect(req.request.params.get('category')).toBe('driver-license');
      expect(req.request.params.get('ownerId')).toBe('u1');
      req.flush({ success: true, responseObject: [doc({ id: 'new' }), doc({ id: 'old' })] });
      expect(comp.value?.id).toBe('new');
      expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ id: 'new' }));
      expect(onTouched).toHaveBeenCalled();
    });

    it('uses an explicit ownerId over the signed-in user', () => {
      const { fixture, http } = setup();
      fixture.componentRef.setInput('ownerId', 'driver-9');
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      expect(req.request.params.get('ownerId')).toBe('driver-9');
      req.flush({ success: true, responseObject: [] });
      expect(fixture.componentInstance.empty).toBe(true);
    });

    it('does not load when there is already a value or autoLoad is off', () => {
      const { fixture, http } = setup();
      fixture.componentRef.setInput('category', 'x');
      fixture.detectChanges();
      http.expectNone(`${DMS}/metadata`);
      fixture.componentRef.setInput('value', doc());
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      http.expectNone((r) => r.url === `${DMS}/metadata`);
    });

    it('ignores changes to unrelated inputs', () => {
      const { fixture, comp, http } = setup();
      comp.autoLoad = true;
      fixture.componentRef.setInput('label', 'Upload');
      fixture.detectChanges();
      http.expectNone((r) => r.url === `${DMS}/metadata`);
    });

    it('skips loading without an owner or a category', () => {
      const { comp, http } = setup({ userId: null });
      comp.autoLoad = true;
      comp.writeValue(null);
      http.expectNone((r) => r.url === `${DMS}/metadata`);
      comp.ownerId = 'o1';
      comp.category = '';
      comp.writeValue(null);
      http.expectNone((r) => r.url === `${DMS}/metadata`);
    });

    it('writeValue(null) with autoLoad loads; a value arriving first is not overwritten', () => {
      const { comp, http } = setup();
      comp.autoLoad = true;
      comp.writeValue(null);
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      comp.writeValue(doc({ id: 'mine' }));
      req.flush({ success: true, responseObject: [doc({ id: 'server' })] });
      expect(comp.value?.id).toBe('mine');
    });

    it('swallows a failed load and stays empty', () => {
      const { comp, http } = setup();
      comp.autoLoad = true;
      comp.writeValue(null);
      http.expectOne((r) => r.url === `${DMS}/metadata`).flush('boom', { status: 500, statusText: 'err' });
      expect(comp.empty).toBe(true);
      expect(comp.errorMessage).toBe('');
    });
  });

  describe('pickFile', () => {
    it('opens the native picker only when enabled and idle', () => {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      comp.pickFile();
      expect(click).toHaveBeenCalledTimes(1);
      comp.loading = true;
      comp.pickFile();
      comp.loading = false;
      comp.disabled = true;
      comp.pickFile();
      expect(click).toHaveBeenCalledTimes(1);
    });

    it('does nothing before the view exists', () => {
      const { comp } = setup();
      expect(() => comp.pickFile()).not.toThrow();
    });
  });

  describe('file selection and upload', () => {
    it('uploads a valid file, emits uploaded and updates the form', async () => {
      const { comp, http, service } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);
      comp.category = 'vehicle-rc';
      comp.documentType = 'VEHICLE_RC';
      const f = file();
      const { event, input } = changeEvent([f]);
      const done = comp.onFileSelected(event);
      const req = await vi.waitFor(() => http.expectOne({ method: 'POST', url: DMS }));
      expect(comp.loading).toBe(true);
      const body = req.request.body as FormData;
      expect(body.get('category')).toBe('vehicle-rc');
      expect(body.get('documentType')).toBe('VEHICLE_RC');
      expect(body.get('ownerId')).toBe('u1');
      expect(body.get('checksum')).toBe('sum');
      expect((body.get('file') as File).name).toBe('a.pdf');
      expect(service.sha256).toHaveBeenCalledWith(f);
      req.flush({ success: true, responseObject: doc({ id: 'up' }) });
      await done;
      expect(comp.loading).toBe(false);
      expect(comp.value?.id).toBe('up');
      expect(uploaded).toHaveBeenCalledWith(expect.objectContaining({ id: 'up' }));
      expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ id: 'up' }));
      expect(input.value).toBe('');
    });

    it('ignores an empty selection and selections while disabled', async () => {
      const { comp, http } = setup();
      await comp.onFileSelected(changeEvent([]).event);
      const noFiles = document.createElement('input');
      await comp.onFileSelected({ target: noFiles } as unknown as Event);
      comp.disabled = true;
      await comp.onFileSelected(changeEvent([file()]).event);
      http.expectNone(DMS);
      expect(comp.errorMessage).toBe('');
    });

    it.each([
      ['a type outside accept', file('a.exe', 'application/x-msdownload'), {}, 'Please upload PDF, JPEG, PNG files.'],
      ['a file under 50 bytes', file('a.pdf', 'application/pdf', 10), {}, 'File is too small (minimum 50 bytes).'],
      ['a non-image on an image preview', file('a.pdf'), { preview: 'circle' }, 'Please upload a JPEG, PNG or WebP image.'],
    ])('rejects %s', async (_label, f, inputs, message) => {
      const { comp, http } = setup();
      Object.assign(comp, inputs);
      const rejected = vi.fn();
      comp.rejected.subscribe(rejected);
      const { event, input } = changeEvent([f]);
      await comp.onFileSelected(event);
      expect(comp.errorMessage).toBe(message);
      expect(rejected).toHaveBeenCalledWith(message);
      expect(input.value).toBe('');
      http.expectNone(DMS);
    });

    it('rejects a file over the size limit, and skips the limit when maxSizeMB is 0', async () => {
      const { comp, http } = setup();
      const big = file();
      Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 });
      await comp.onFileSelected(changeEvent([big]).event);
      expect(comp.errorMessage).toBe('File exceeds the 9.9 MB limit.');
      http.expectNone(DMS);

      comp.maxSizeMB = 0;
      const done = comp.onFileSelected(changeEvent([big]).event);
      const req = await vi.waitFor(() => http.expectOne(DMS));
      expect(comp.errorMessage).toBe('');
      req.flush({ success: true, responseObject: doc() });
      await done;
    });

    it('accepts an image on an image preview', async () => {
      const { comp, http } = setup();
      comp.preview = 'square';
      const done = comp.onFileSelected(changeEvent([file('p.png', 'image/png')]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: true, responseObject: doc({ contentType: 'application/pdf' }) });
      await done;
      expect(comp.value?.id).toBe('d1');
    });

    it('asks before replacing and keeps the current document on No', async () => {
      const { comp, http, dialog } = setup({ dialogAnswer: false });
      comp.writeValue(doc());
      await comp.onFileSelected(changeEvent([file()]).event);
      expect(dialog.open).toHaveBeenCalledWith(HqClose, expect.objectContaining({
        data: { confirm: true, message: 'Do you want to replace the current document?' }, disableClose: true,
      }));
      http.expectNone(DMS);
      expect(comp.value?.id).toBe('d1');
    });

    it('replaces on Yes and deletes the previous document when deleteOnRemove is set', async () => {
      const { comp, http } = setup({ dialogAnswer: true });
      comp.writeValue(doc({ id: 'old' }));
      comp.deleteOnRemove = true;
      const done = comp.onFileSelected(changeEvent([file()]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: true, responseObject: doc({ id: 'new' }) });
      await done;
      const del = http.expectOne({ method: 'DELETE', url: `${DMS}/metadata/old` });
      del.flush('x', { status: 500, statusText: 'err' }); // failure of the cleanup is ignored
      expect(comp.value?.id).toBe('new');
      expect(comp.errorMessage).toBe('');
    });

    it('replaces without asking when confirmReplace is off, keeping the previous document in DMS', async () => {
      const { comp, http, dialog } = setup();
      comp.writeValue(doc({ id: 'old' }));
      comp.confirmReplace = false;
      const done = comp.onFileSelected(changeEvent([file()]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: true, responseObject: doc({ id: 'new' }) });
      await done;
      expect(dialog.open).not.toHaveBeenCalled();
      http.expectNone(`${DMS}/metadata/old`);
      expect(comp.value?.id).toBe('new');
    });

    it('rejects when no owner is available', async () => {
      const { comp, http } = setup({ userId: undefined });
      await comp.onFileSelected(changeEvent([file()]).event);
      expect(comp.errorMessage).toBe('No owner available for this document.');
      expect(comp.loading).toBe(false);
      http.expectNone(DMS);
    });

    it.each([
      ['the server message', { message: 'Virus found' }, 400, 'Virus found'],
      ['the problem detail', { detail: 'Too large' }, 413, 'Too large'],
      ['a generic message', null, 500, 'Upload failed.'],
    ])('reports %s when the upload fails', async (_label, body, status, message) => {
      const { comp, http } = setup();
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);
      const done = comp.onFileSelected(changeEvent([file()]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush(body, { status, statusText: 'err' });
      await done;
      expect(comp.errorMessage).toBe(message);
      expect(comp.loading).toBe(false);
      expect(comp.empty).toBe(true);
      expect(uploaded).not.toHaveBeenCalled();
    });

    it('reports an unsuccessful DMS envelope as an error', async () => {
      const { comp, http } = setup();
      const done = comp.onFileSelected(changeEvent([file()]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: false, message: 'Duplicate' });
      await done;
      expect(comp.errorMessage).toBe('Duplicate');
    });

    it('reports a checksum failure with the generic message', async () => {
      const { comp, http, service } = setup();
      (service.sha256 as Mock).mockRejectedValueOnce(new Error('no crypto'));
      await comp.onFileSelected(changeEvent([file()]).event);
      expect(comp.errorMessage).toBe('Upload failed.');
      expect(comp.loading).toBe(false);
      http.expectNone(DMS);
    });
  });

  describe('cropping', () => {
    it('uploads the cropped image', async () => {
      const cropped = file('cropped.png', 'image/png');
      const cropper = vi.fn().mockResolvedValue(cropped);
      const { comp, http } = setup({ cropper });
      comp.enableCrop = true;
      comp.cropAspectRatio = 4 / 3;
      const original = file('o.png', 'image/png');
      const { event } = changeEvent([original]);
      const done = comp.onFileSelected(event);
      const req = await vi.waitFor(() => http.expectOne(DMS));
      expect(cropper).toHaveBeenCalledWith(event, original, 4 / 3);
      expect(((req.request.body as FormData).get('file') as File).name).toBe('cropped.png');
      req.flush({ success: true, responseObject: doc() });
      await done;
    });

    it('cancels the upload when cropping is dismissed', async () => {
      const cropper = vi.fn().mockResolvedValue(null);
      const { comp, http } = setup({ cropper });
      comp.enableCrop = true;
      await comp.onFileSelected(changeEvent([file('o.png', 'image/png')]).event);
      expect(cropper).toHaveBeenCalled();
      http.expectNone(DMS);
    });

    it('does not crop non-images, or when no cropper is provided', async () => {
      const cropper = vi.fn();
      const { comp, http } = setup({ cropper });
      comp.enableCrop = true;
      const done = comp.onFileSelected(changeEvent([file()]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: true, responseObject: doc() });
      await done;
      expect(cropper).not.toHaveBeenCalled();
    });

    it('enableCrop is a no-op without a cropper', async () => {
      const { comp, http } = setup({ cropper: null });
      comp.enableCrop = true;
      const done = comp.onFileSelected(changeEvent([file('o.png', 'image/png')]).event);
      (await vi.waitFor(() => http.expectOne(DMS))).flush({ success: true, responseObject: doc() });
      await done;
      expect(comp.value?.id).toBe('d1');
    });
  });

  describe('open', () => {
    it('does nothing without a document', () => {
      const { comp, http } = setup();
      comp.open();
      http.expectNone((r) => r.url.includes('download-url'));
    });

    it.each([
      ['an image', 'image/png'],
      ['a PDF', 'application/pdf'],
    ])('previews %s in the open-file dialog', (_label, contentType) => {
      const { comp, http, dialog } = setup();
      comp.writeValue(doc({ contentType }));
      comp.open();
      flushFetch(http, 'd1');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({
        data: { blob: expect.any(Blob), fileName: 'license.pdf' }, maxWidth: '95vw', autoFocus: false,
      }));
      const blob = dialog.open.mock.calls[0][1].data.blob as Blob;
      expect(blob.type).toBe(contentType);
    });

    it('downloads any other type', () => {
      const { comp, http, dialog } = setup();
      comp.writeValue(doc({ contentType: 'application/msword', originalFileName: 'cv.doc' }));
      comp.open();
      flushFetch(http, 'd1');
      expect(dialog.open).not.toHaveBeenCalled();
      expect(createUrl).toHaveBeenCalled();
      const anchorClick = HTMLAnchorElement.prototype.click as unknown as Mock;
      expect(anchorClick).toHaveBeenCalled();
    });

    it('reports a failure to fetch the file', () => {
      const { comp, http } = setup();
      const rejected = vi.fn();
      comp.rejected.subscribe(rejected);
      comp.writeValue(doc());
      comp.open();
      http.expectOne(`${DMS}/metadata/d1/download-url`).flush('x', { status: 404, statusText: 'nf' });
      expect(comp.errorMessage).toBe('Unable to open the document.');
      expect(rejected).toHaveBeenCalledWith('Unable to open the document.');
    });
  });

  describe('remove', () => {
    it('clears the value and emits removed without touching DMS by default', async () => {
      const { comp, http, dialog } = setup();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      comp.writeValue(doc());
      await comp.remove();
      expect(comp.value).toBeNull();
      expect(onChange).toHaveBeenCalledWith(null);
      expect(removed).toHaveBeenCalledWith(expect.objectContaining({ id: 'd1' }));
      expect(dialog.open).not.toHaveBeenCalled();
      http.expectNone(`${DMS}/metadata/d1`);
    });

    it('does nothing when empty, disabled or not removable', async () => {
      const { comp } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      await comp.remove();
      comp.writeValue(doc());
      comp.disabled = true;
      await comp.remove();
      comp.disabled = false;
      comp.removable = false;
      await comp.remove();
      expect(removed).not.toHaveBeenCalled();
      expect(comp.value?.id).toBe('d1');
    });

    it('keeps the document when the delete confirmation is declined', async () => {
      const { comp, http, dialog } = setup({ dialogAnswer: false });
      comp.deleteOnRemove = true;
      comp.writeValue(doc());
      await comp.remove();
      expect(dialog.open).toHaveBeenCalledWith(HqClose, expect.objectContaining({
        data: { confirm: true, message: 'Delete this document permanently?' },
      }));
      http.expectNone(`${DMS}/metadata/d1`);
      expect(comp.value?.id).toBe('d1');
    });

    it('deletes in DMS after confirmation', async () => {
      const { comp, http } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      comp.deleteOnRemove = true;
      comp.writeValue(doc());
      const done = comp.remove();
      (await vi.waitFor(() => http.expectOne({ method: 'DELETE', url: `${DMS}/metadata/d1` }))).flush(null, { status: 204, statusText: 'ok' });
      await done;
      expect(comp.value).toBeNull();
      expect(removed).toHaveBeenCalled();
    });

    it.each([
      [{ message: 'Legal hold' }, 'Legal hold'],
      [null, 'Unable to delete the document.'],
    ])('keeps the document and reports a failed delete (%j)', async (body, message) => {
      const { comp, http } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      comp.deleteOnRemove = true;
      comp.writeValue(doc());
      const done = comp.remove();
      (await vi.waitFor(() => http.expectOne(`${DMS}/metadata/d1`))).flush(body, { status: 409, statusText: 'c' });
      await done;
      expect(comp.errorMessage).toBe(message);
      expect(comp.value?.id).toBe('d1');
      expect(removed).not.toHaveBeenCalled();
    });
  });

  describe('image preview', () => {
    it('fetches an object URL for an image document and revokes it on destroy', () => {
      const { fixture, comp, http } = setup();
      comp.preview = 'circle';
      comp.writeValue(doc({ contentType: 'image/png' }));
      flushFetch(http, 'd1');
      expect(comp.previewUrl).toBe('blob:preview');
      // same document again: no second fetch
      comp.writeValue(doc({ contentType: 'image/png' }));
      http.expectNone(`${DMS}/metadata/d1/download-url`);
      fixture.destroy();
      expect(revokeUrl).toHaveBeenCalledWith('blob:preview');
      expect(comp.previewUrl).toBeNull();
    });

    it('drops a preview that arrives after the value changed', () => {
      const { comp, http } = setup();
      comp.preview = 'square';
      comp.writeValue(doc({ id: 'a', contentType: 'image/png' }));
      const first = http.expectOne(`${DMS}/metadata/a/download-url`);
      comp.writeValue(doc({ id: 'b', contentType: 'image/jpeg' }));
      first.flush({ success: true, responseObject: { url: FILE_URL, expiresAt: '' } });
      http.expectOne(FILE_URL).flush(new Blob(['a']));
      expect(createUrl).not.toHaveBeenCalled();
      flushFetch(http, 'b');
      expect(createUrl).toHaveBeenCalledTimes(1);
      expect(comp.previewUrl).toBe('blob:preview');
    });

    it('has no preview for non-image documents, without a preview mode, or when the fetch fails', () => {
      const { comp, http } = setup();
      comp.writeValue(doc({ contentType: 'image/png' }));
      http.expectNone((r) => r.url.includes('download-url'));
      comp.preview = 'circle';
      comp.writeValue(doc({ id: 'p', contentType: 'application/pdf' }));
      comp.writeValue(doc({ id: 'n', contentType: undefined as unknown as string }));
      http.expectNone((r) => r.url.includes('download-url'));
      comp.writeValue(doc({ id: 'img', contentType: 'image/png' }));
      http.expectOne(`${DMS}/metadata/img/download-url`).flush('x', { status: 500, statusText: 'e' });
      expect(comp.previewUrl).toBeNull();
      expect(comp.errorMessage).toBe('');
    });
  });

  describe('template (attachment mode)', () => {
    it('shows the drop area with label, required marker and hint when empty', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('label', 'License');
      fixture.componentRef.setInput('isRequired', true);
      fixture.componentRef.setInput('inputId', 'lic');
      fixture.detectChanges();
      const btn = el(fixture, '#licBtn') as HTMLButtonElement;
      expect(btn.textContent).toContain('Choose a file');
      expect(el(fixture, '.hq-drop__label')?.textContent).toContain('License');
      expect(el(fixture, '.hq-drop__required')).not.toBeNull();
      expect(el(fixture, '.hq-drop__text')?.textContent).toContain('Drop a file here, or');
      expect(el(fixture, '.hq-drop__hint')?.textContent).toContain('PDF, JPEG, PNG · up to 9.9 MB');
      expect(el(fixture, '#lic')).toBe(comp.picker!.fileInput!.nativeElement);
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      btn.click();
      expect(click).toHaveBeenCalled();
    });

    it('shows "No document" when empty and disabled', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__hint')?.textContent).toContain('No document');
      expect(el(fixture, 'button')).toBeNull();
    });

    it('uploads through the hidden input change event and renders the chip', async () => {
      const { fixture, comp, http } = setup();
      fixture.detectChanges();
      const input = el(fixture, 'input[type=file]') as HTMLInputElement;
      expect(input.accept).toBe(comp.accept);
      Object.defineProperty(input, 'files', { value: [file('rc.pdf')], configurable: true });
      input.dispatchEvent(new Event('change'));
      const req = await vi.waitFor(() => http.expectOne(DMS));
      fixture.detectChanges();
      expect(el(fixture, '.hq-drop__spinner')).not.toBeNull();
      req.flush({ success: true, responseObject: doc({ originalFileName: 'rc.pdf', sizeBytes: 3 * 1024 * 1024 }) });
      await vi.waitFor(() => expect(comp.value).not.toBeNull());
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__name')?.textContent).toContain('rc.pdf');
      expect(el(fixture, '.hq-doc__size')?.textContent).toContain('3.0 MB');
      expect(el(fixture, '.hq-drop__spinner')).toBeNull();
    });

    it('renders an existing document with remove and change actions', async () => {
      const { fixture, comp, http } = setup();
      const removed = vi.fn();
      comp.removed.subscribe(removed);
      fixture.componentRef.setInput('value', doc());
      fixture.detectChanges();
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      (el(fixture, '.hq-doc__change') as HTMLButtonElement).click();
      expect(click).toHaveBeenCalled();

      (el(fixture, '.hq-doc__chip') as HTMLElement).click();
      http.expectOne(`${DMS}/metadata/d1/download-url`).flush({ success: false });

      (el(fixture, 'button[aria-label="Remove document"]') as HTMLButtonElement).click();
      await vi.waitFor(() => expect(removed).toHaveBeenCalled());
      http.expectNone((r) => r.url.includes('download-url'));
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__chip')).toBeNull();
    });

    it('offers View, which opens the document in the viewer, also when the form is read-only', () => {
      const { fixture, http, dialog } = setup();
      fixture.componentRef.setInput('value', doc());
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      const view = el(fixture, '.hq-doc__view') as HTMLButtonElement;
      expect(view.textContent).toContain('View');
      expect(el(fixture, '.hq-doc__change')).toBeNull();

      view.click();
      flushFetch(http, 'd1');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'license.pdf' }) }));
    });

    it('has no View while there is no document, and disables it during an upload', () => {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__view')).toBeNull();
      fixture.componentRef.setInput('value', doc());
      comp.loading = true;
      fixture.detectChanges();
      expect((el(fixture, '.hq-doc__view') as HTMLButtonElement).disabled).toBe(true);
    });

    it('hides remove and change when disabled or not removable', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('value', doc());
      fixture.componentRef.setInput('removable', false);
      fixture.detectChanges();
      expect(el(fixture, 'button[aria-label="Remove document"]')).toBeNull();
      expect(el(fixture, '.hq-doc__change')).not.toBeNull();
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__change')).toBeNull();
    });

    it('shows the validation message', async () => {
      const { fixture, comp } = setup();
      fixture.detectChanges();
      await comp.onFileSelected(changeEvent([file('a.exe', 'x/y')]).event);
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__error')?.textContent).toContain('Please upload PDF, JPEG, PNG files.');
    });
  });

  describe('with its own storage (uploader and fetcher instead of DMS)', () => {
    it('uploads through the uploader, shows the file and opens it through the fetcher, never calling DMS', async () => {
      const { fixture, comp, http, dialog } = setup();
      const stored = hqDocumentFromFile(file('photo.png', 'image/png'), 'p1');
      const uploader = vi.fn(() => of(stored));
      const fetcher = vi.fn(() => of(new Blob(['img'], { type: 'image/png' })));
      fixture.componentRef.setInput('uploader', uploader);
      fixture.componentRef.setInput('fetcher', fetcher);
      fixture.componentRef.setInput('autoLoad', true);
      fixture.detectChanges();
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);

      await comp.onFilesPicked([file('photo.png', 'image/png')]);
      fixture.detectChanges();

      expect(uploader).toHaveBeenCalledTimes(1);
      expect(uploaded).toHaveBeenCalledWith(stored);
      expect(el(fixture, '.hq-doc__name')?.textContent).toContain('photo.png');
      (el(fixture, '.hq-doc__view') as HTMLButtonElement).click();
      expect(fetcher).toHaveBeenCalledWith(stored);
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'photo.png' }) }));
      http.expectNone(() => true);
    });

    it('shows the uploader\'s error and keeps the empty state', async () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('uploader', () => throwError(() => ({ error: { message: 'Photo rejected' } })));
      fixture.detectChanges();

      await comp.onFilesPicked([file('photo.png', 'image/png')]);
      fixture.detectChanges();

      expect(comp.value).toBeNull();
      expect(el(fixture, '.hq-doc__error')?.textContent).toContain('Photo rejected');
    });

    it('never reads a file it stored itself from DMS: without a fetcher there is no View', () => {
      const { fixture, comp, http, dialog } = setup();
      fixture.componentRef.setInput('uploader', () => of(hqDocumentFromFile(file('h.pdf', 'application/pdf'), 'h1')));
      comp.writeValue(hqDocumentFromFile(file('h.pdf', 'application/pdf'), 'h1'));
      fixture.detectChanges();

      expect(comp.canOpen).toBe(false);
      expect(el(fixture, '.hq-doc__view')).toBeNull();
      comp.open();
      expect(dialog.open).not.toHaveBeenCalled();
      http.expectNone(() => true);
    });

    it('builds a document record from a stored file', () => {
      setup();
      const doc = hqDocumentFromFile(file('h.pdf', 'application/pdf'), 'd9', 'HOROSCOPE', 'u1');
      expect(doc).toMatchObject({ id: 'd9', originalFileName: 'h.pdf', contentType: 'application/pdf', category: 'HOROSCOPE', ownerId: 'u1' });
      expect(doc.sizeBytes).toBeGreaterThan(0);
    });
  });

  describe('template (image mode)', () => {
    it('shows a circle placeholder and picks a file on frame click when empty', () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('preview', 'circle');
      fixture.detectChanges();
      expect(el(fixture, '.hq-img--circle')).not.toBeNull();
      expect(el(fixture, '.hq-img__placeholder')?.textContent).toContain('account_circle');
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      (el(fixture, '.hq-img__frame') as HTMLElement).click();
      expect(click).toHaveBeenCalled();
    });

    it('shows the fetched image and opens it on frame click; loading shows a spinner', () => {
      const { fixture, comp, http, dialog } = setup();
      fixture.componentRef.setInput('preview', 'square');
      fixture.componentRef.setInput('label', 'Photo');
      fixture.componentRef.setInput('value', doc({ contentType: 'image/png' }));
      flushFetch(http, 'd1');
      comp.loading = true;
      fixture.detectChanges();
      expect(el(fixture, '.hq-img--square')).not.toBeNull();
      const img = el(fixture, 'img') as HTMLImageElement;
      expect(img.getAttribute('src')).toBe('blob:preview');
      expect(img.alt).toBe('Photo');
      expect(el(fixture, '.hq-img__spinner')).not.toBeNull();
      (el(fixture, '.hq-img__frame') as HTMLElement).click();
      flushFetch(http, 'd1');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.anything());
    });

    it('square placeholder icon is "image"', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('preview', 'square');
      fixture.detectChanges();
      expect(el(fixture, '.hq-img__placeholder')?.textContent).toContain('image');
    });

    it('edit menu offers Upload when empty, and Change + Remove with a document', async () => {
      const { fixture, comp } = setup();
      fixture.componentRef.setInput('preview', 'circle');
      fixture.detectChanges();
      const click = vi.spyOn(comp.picker!.fileInput!.nativeElement, 'click').mockImplementation(() => {});
      (el(fixture, '.hq-img__edit') as HTMLButtonElement).click();
      fixture.detectChanges();
      let items = Array.from(document.querySelectorAll<HTMLButtonElement>('[mat-menu-item]'));
      expect(items).toHaveLength(1);
      expect(items[0].textContent).toContain('Upload');
      items[0].click();
      expect(click).toHaveBeenCalled();
      fixture.detectChanges();
      await fixture.whenStable();

      const removed = vi.fn();
      comp.removed.subscribe(removed);
      fixture.componentRef.setInput('value', doc({ contentType: 'application/pdf' }));
      fixture.detectChanges();
      (el(fixture, '.hq-img__edit') as HTMLButtonElement).click();
      fixture.detectChanges();
      items = Array.from(document.querySelectorAll<HTMLButtonElement>('[mat-menu-item]'));
      expect(items.some((b) => b.textContent?.includes('Change'))).toBe(true);
      expect(items.some((b) => b.textContent?.includes('Remove'))).toBe(true);
      items.find((b) => b.textContent?.includes('Remove'))!.click();
      await vi.waitFor(() => expect(removed).toHaveBeenCalled());
    });

    it('edit menu offers View first once there is a document, and View opens it in the viewer', () => {
      const { fixture, http, dialog } = setup();
      fixture.componentRef.setInput('preview', 'square');
      fixture.componentRef.setInput('value', doc({ contentType: 'application/pdf' }));
      fixture.detectChanges();
      (el(fixture, '.hq-img__edit') as HTMLButtonElement).click();
      fixture.detectChanges();
      const items = Array.from(document.querySelectorAll<HTMLButtonElement>('[mat-menu-item]'));
      expect(items[0].textContent).toContain('View');

      items[0].click();
      flushFetch(http, 'd1');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'license.pdf' }) }));
    });

    it('hides the edit button when disabled or not editable', () => {
      const { fixture } = setup();
      fixture.componentRef.setInput('preview', 'circle');
      fixture.componentRef.setInput('isEdit', false);
      fixture.detectChanges();
      expect(el(fixture, '.hq-img__edit')).toBeNull();
      fixture.componentRef.setInput('isEdit', true);
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-img__edit')).toBeNull();
    });
  });
  describe('drop area and translated texts', () => {
    it('uploads a file dropped on the empty drop area', async () => {
      const { fixture, comp, http } = setup();
      const uploaded = vi.fn();
      comp.uploaded.subscribe(uploaded);
      fixture.detectChanges();
      const drop = fixture.debugElement.query(By.directive(HqFileDrop)).componentInstance as HqFileDrop;
      const zone = el(fixture, '.hq-drop') as HTMLElement;
      const event = new Event('drop') as DragEvent;
      Object.defineProperty(event, 'dataTransfer', { value: { files: [file('dropped.pdf')] } });
      zone.dispatchEvent(event);
      expect(drop.dragOver).toBe(false);
      const req = await vi.waitFor(() => http.expectOne(DMS));
      expect(((req.request.body as FormData).get('file') as File).name).toBe('dropped.pdf');
      req.flush({ success: true, responseObject: doc({ originalFileName: 'dropped.pdf' }) });
      await vi.waitFor(() => expect(uploaded).toHaveBeenCalled());
    });

    it('checks a dropped file like a picked one', async () => {
      const { comp, http } = setup();
      await comp.onFilesPicked([file('tiny.pdf', 'application/pdf', 10)]);
      expect(comp.errorMessage).toBe('File is too small (minimum 50 bytes).');
      await comp.onFilesPicked([]);
      http.expectNone(DMS);
    });

    it('uses the app'+String.fromCharCode(39)+'s translated texts when provided', async () => {
      const texts: HqUploadTexts = { ...HQ_UPLOAD_DEFAULT_TEXTS, noDocument: () => 'Kein Dokument', tooLarge: (max) => 'Zu gross: ' + max, change: () => 'Ersetzen' };
      const { fixture, comp } = setup({ texts });
      fixture.componentRef.setInput('disabled', true);
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__hint')?.textContent).toContain('Kein Dokument');
      fixture.componentRef.setInput('disabled', false);
      fixture.componentRef.setInput('value', doc());
      fixture.detectChanges();
      expect(el(fixture, '.hq-doc__change')?.textContent).toContain('Ersetzen');
      await comp.onFileSelected(changeEvent([file('big.pdf', 'application/pdf', 11 * 1024 * 1024)]).event);
      expect(comp.errorMessage).toBe('Zu gross: 9.9');
    });
  });
});
