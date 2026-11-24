import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HQ_CONFIG } from './hq-common.config';
import { HqDocument } from './hq-common.models';
import { HqDocumentService } from './hq-document.service';

const DMS = 'http://gw/api/v1/dms/documents';

function doc(overrides: Partial<HqDocument> = {}): HqDocument {
  return {
    id: 'doc-1', category: 'KYC', ownerId: 'owner-1', originalFileName: 'licence.pdf', storedFileName: 's.pdf',
    publicUrl: null, provider: 'LOCAL', versionId: null, checksumSha256: null, contentType: 'application/pdf',
    sizeBytes: 10, status: 'ACTIVE', scanStatus: 'CLEAN', scanMessage: null, retentionUntil: null, legalHold: false,
    createdAt: '2026-10-01T00:00:00Z', ...overrides,
  };
}

describe('HqDocumentService', () => {
  let service: HqDocumentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HQ_CONFIG, useValue: { gatewayUrl: 'http://gw/api/', adminApiUrl: 'http://admin', currentUserId: () => 'u' } },
      ],
    });
    service = TestBed.inject(HqDocumentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  describe('upload', () => {
    it('posts the file, category, owner and checksum as multipart and returns the document', async () => {
      const file = new File(['abc'], 'licence.pdf', { type: 'application/pdf' });
      const result = firstValueFrom(service.upload(file, 'KYC', 'owner-1', 'abc123'));
      const req = http.expectOne(DMS);
      expect(req.request.method).toBe('POST');
      const form = req.request.body as FormData;
      expect(form.get('file')).toBeInstanceOf(File);
      expect((form.get('file') as File).name).toBe('licence.pdf');
      expect(form.get('category')).toBe('KYC');
      expect(form.get('ownerId')).toBe('owner-1');
      expect(form.get('checksum')).toBe('abc123');
      req.flush({ success: true, responseObject: doc() });
      expect((await result).id).toBe('doc-1');
    });

    it("sends the document type so DMS applies that type's rules, and leaves it out when none is given", () => {
      service.upload(new File(['abc'], 'dl.pdf'), 'driver-documents', 'owner-1', undefined, 'DRIVING_LICENCE').subscribe();
      const typed = http.expectOne(DMS);
      expect((typed.request.body as FormData).get('documentType')).toBe('DRIVING_LICENCE');
      typed.flush({ success: true, responseObject: doc() });

      service.upload(new File(['abc'], 'a.pdf'), 'general', 'owner-1').subscribe();
      const plain = http.expectOne(DMS);
      expect((plain.request.body as FormData).has('documentType')).toBe(false);
      plain.flush({ success: true, responseObject: doc() });
    });

    it('leaves out the checksum when none is given', () => {
      service.upload(new File(['abc'], 'a.png'), 'KYC', 'owner-1').subscribe();
      const req = http.expectOne(DMS);
      expect((req.request.body as FormData).has('checksum')).toBe(false);
      req.flush({ success: true, responseObject: doc() });
    });

    it('fails with the server message when DMS rejects the upload', async () => {
      const result = firstValueFrom(service.upload(new File(['x'], 'a.exe'), 'KYC', 'owner-1'));
      http.expectOne(DMS).flush({ success: false, message: 'File type not allowed' });
      await expect(result).rejects.toEqual({ error: { message: 'File type not allowed' } });
    });

    it('fails with a generic message when DMS gives none', async () => {
      const result = firstValueFrom(service.upload(new File(['x'], 'a.exe'), 'KYC', 'owner-1'));
      http.expectOne(DMS).flush({ success: false });
      await expect(result).rejects.toEqual({ error: { message: 'Document request failed' } });
    });

    it('fails with a generic message when the body is empty', async () => {
      const result = firstValueFrom(service.upload(new File(['x'], 'a.exe'), 'KYC', 'owner-1'));
      http.expectOne(DMS).flush(null);
      await expect(result).rejects.toEqual({ error: { message: 'Document request failed' } });
    });
  });

  describe('list', () => {
    it('gets the documents filed under category and owner', async () => {
      const result = firstValueFrom(service.list('KYC', 'owner-1'));
      const req = http.expectOne((r) => r.url === `${DMS}/metadata`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('category')).toBe('KYC');
      expect(req.request.params.get('ownerId')).toBe('owner-1');
      req.flush({ success: true, responseObject: [doc(), doc({ id: 'doc-2' })] });
      expect((await result).map((d) => d.id)).toEqual(['doc-1', 'doc-2']);
    });

    it('returns an empty list when DMS sends none', async () => {
      const result = firstValueFrom(service.list('KYC', 'owner-1'));
      http.expectOne((r) => r.url === `${DMS}/metadata`).flush({ success: true });
      expect(await result).toEqual([]);
    });
  });

  it('getMetadata reads one document', async () => {
    const result = firstValueFrom(service.getMetadata('doc-9'));
    const req = http.expectOne(`${DMS}/metadata/doc-9`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, responseObject: doc({ id: 'doc-9' }) });
    expect((await result).id).toBe('doc-9');
  });

  it('delete soft-deletes and completes with no value', async () => {
    const result = firstValueFrom(service.delete('doc-9'));
    const req = http.expectOne(`${DMS}/metadata/doc-9`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(await result).toBeUndefined();
  });

  describe('fetchFile', () => {
    function answerDownloadUrl(id: string, url: string): void {
      const req = http.expectOne(`${DMS}/metadata/${id}/download-url`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush({ success: true, responseObject: { url, expiresAt: '2026-10-24T00:00:00Z' } });
    }

    it('maps a backend-relative URL onto the gateway DMS route and re-types the bytes', async () => {
      const result = firstValueFrom(service.fetchFile(doc()));
      answerDownloadUrl('doc-1', '/api/v1/documents/files/doc-1');
      const file = http.expectOne(`${DMS}/files/doc-1`);
      expect(file.request.responseType).toBe('blob');
      file.flush(new Blob(['%PDF'], { type: 'application/octet-stream' }));
      const blob = await result;
      expect(blob.type).toBe('application/pdf');
      expect(await blob.text()).toBe('%PDF');
    });

    it('uses an absolute presigned URL as-is', async () => {
      const result = firstValueFrom(service.fetchFile(doc()));
      answerDownloadUrl('doc-1', 'HTTPS://minio.local/bucket/doc-1?sig=x');
      http.expectOne('HTTPS://minio.local/bucket/doc-1?sig=x').flush(new Blob(['x']));
      expect((await result).type).toBe('application/pdf');
    });

    it('uses any other relative URL as-is', () => {
      service.fetchFile(doc()).subscribe();
      answerDownloadUrl('doc-1', '/files/doc-1');
      http.expectOne('/files/doc-1').flush(new Blob(['x']));
    });

    it("keeps the blob's own type when the document has none", async () => {
      const result = firstValueFrom(service.fetchFile(doc({ contentType: '' })));
      answerDownloadUrl('doc-1', 'http://s3/doc-1');
      http.expectOne('http://s3/doc-1').flush(new Blob(['x'], { type: 'image/png' }));
      expect((await result).type).toBe('image/png');
    });

    it('fails without fetching the bytes when DMS refuses the download URL', async () => {
      const result = firstValueFrom(service.fetchFile(doc()));
      http.expectOne(`${DMS}/metadata/doc-1/download-url`).flush({ success: false, message: 'Forbidden' });
      await expect(result).rejects.toEqual({ error: { message: 'Forbidden' } });
    });
  });

  it('download saves the fetched file under its original name', async () => {
    const createObjectURL = vi.fn(() => 'blob:x');
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn() as unknown as typeof URL.revokeObjectURL;
    const names: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download);
    });
    try {
      const result = firstValueFrom(service.download(doc()));
      answerUrlAndBytes();
      expect(await result).toBeUndefined();
      expect(createObjectURL).toHaveBeenCalledOnce();
      expect(names).toEqual(['licence.pdf']);
    } finally {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    }

    function answerUrlAndBytes(): void {
      http.expectOne(`${DMS}/metadata/doc-1/download-url`).flush({ success: true, responseObject: { url: 'http://s3/doc-1' } });
      http.expectOne('http://s3/doc-1').flush(new Blob(['x']));
    }
  });

  it('sha256 returns the lowercase hex digest of the file', async () => {
    const file = new File(['abc'], 'abc.txt');
    expect(await service.sha256(file)).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});
