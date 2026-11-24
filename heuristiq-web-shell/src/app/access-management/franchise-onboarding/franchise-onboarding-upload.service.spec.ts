import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of, throwError } from 'rxjs';
import { HqDocumentService } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ONBOARDING_DOCUMENT_PROVIDERS, OnboardingUploadService } from './franchise-onboarding-upload.service';

/** A host that provides the onboarding upload rules the way the onboarding pages do. */
@Component({ selector: 'app-upload-host', standalone: true, template: '', providers: [ONBOARDING_DOCUMENT_PROVIDERS] })
class UploadHost {
  readonly documents = inject(HqDocumentService);
  readonly uploads = inject(OnboardingUploadService);
}

const stored = { id: 'dms-1', originalFileName: 'CHEQUE.JPG', contentType: 'image/jpeg' };
const dms = { upload: vi.fn(), getMetadata: vi.fn(() => of(stored)), sha256: vi.fn() };
const translate = { instant: vi.fn((key: string, params?: Record<string, unknown>) => (params ? `${key}|${JSON.stringify(params)}` : `T(${key})`)) };

function host(): UploadHost {
  return TestBed.createComponent(UploadHost).componentInstance;
}

async function failureOf(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (err) {
    return (err as { error: { message: string } }).error.message;
  }
  throw new Error('expected the upload to fail');
}

describe('OnboardingUploadService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    dms.upload.mockReturnValue(of(stored));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: HqDocumentService, useValue: dms }, { provide: TranslateService, useValue: translate }],
    });
  });

  it('gives the page a DMS client whose uploads send a JPEG without a browser type as image/jpeg', async () => {
    const { documents } = host();
    const file = new File(['jpeg'], 'CHEQUE.JPG', { type: '' });

    const doc = await firstValueFrom(documents.upload(file, 'franchise-onboarding', 'f1', 'abc', 'FRANCHISE_BANK_PROOF'));

    expect(doc).toBe(stored);
    const [sent, category, owner, checksum, type] = dms.upload.mock.calls[0];
    expect((sent as File).type).toBe('image/jpeg');
    expect((sent as File).name).toBe('CHEQUE.JPG');
    expect([category, owner, checksum, type]).toEqual(['franchise-onboarding', 'f1', 'abc', 'FRANCHISE_BANK_PROOF']);
  });

  it('keeps every other DMS call unchanged', async () => {
    const { documents } = host();
    expect(await firstValueFrom(documents.getMetadata('dms-1'))).toBe(stored);
    expect(dms.getMetadata).toHaveBeenCalledWith('dms-1');
  });

  it('refuses a file of another type before sending it, naming the allowed types and size', async () => {
    const { documents } = host();
    const message = await failureOf(firstValueFrom(documents.upload(new File(['x'], 'letter.docx'), 'c', 'o')));

    expect(dms.upload).not.toHaveBeenCalled();
    expect(message).toBe('franchiseOnboarding.upload.invalidType|{"name":"letter.docx","types":"T(franchiseOnboarding.upload.hints)","max":5}');
  });

  it('refuses a file over 5 MB and an empty file', async () => {
    const { uploads } = host();
    const big = new File(['x'], 'scan.jpeg', { type: 'image/jpeg' });
    Object.defineProperty(big, 'size', { value: 5 * 1024 * 1024 + 1 });

    expect(await failureOf(firstValueFrom(uploads.upload(big, 'c', 'o')))).toContain('franchiseOnboarding.upload.tooLarge');
    expect(await failureOf(firstValueFrom(uploads.upload(new File([], 'scan.png'), 'c', 'o')))).toContain('franchiseOnboarding.upload.empty');
    expect(dms.upload).not.toHaveBeenCalled();
  });

  it('turns a DMS type or size refusal into the translated message, and passes other failures on', async () => {
    const { uploads } = host();
    dms.upload.mockReturnValueOnce(throwError(() => ({ error: { message: 'Document content type is not allowed' } })));
    expect(await failureOf(firstValueFrom(uploads.upload(new File(['x'], 'a.jpg'), 'c', 'o'))))
      .toBe('franchiseOnboarding.upload.rejected|{"name":"a.jpg","types":"T(franchiseOnboarding.upload.hints)","max":5}');

    dms.upload.mockReturnValueOnce(throwError(() => ({ error: { message: 'Malware scan unavailable' } })));
    expect(await failureOf(firstValueFrom(uploads.upload(new File(['x'], 'a.jpg'), 'c', 'o')))).toBe('Malware scan unavailable');
  });
});
