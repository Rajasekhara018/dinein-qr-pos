import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { DocumentPreviewData, DocumentPreviewDialog } from './document-preview-dialog';

describe('DocumentPreviewDialog', () => {
  const dialogRef = { close: vi.fn() };
  let created: string[];
  let revoked: string[];

  function create(blob$: Observable<Blob>, fileName: string | null = 'file.bin'): ComponentFixture<DocumentPreviewDialog> {
    TestBed.resetTestingModule();
    const data: DocumentPreviewData = { title: 'Aadhaar', fileName, blob$ };
    TestBed.configureTestingModule({
      declarations: [DocumentPreviewDialog],
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    const fixture = TestBed.createComponent(DocumentPreviewDialog);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    created = [];
    revoked = [];
    URL.createObjectURL = vi.fn(() => { created.push('blob:x'); return 'blob:x'; });
    URL.revokeObjectURL = vi.fn((url: string) => { revoked.push(url); });
  });

  it('shows the loading state until the file arrives', () => {
    const fixture = create(new Subject<Blob>());

    expect(fixture.componentInstance.loading).toBe(true);
    expect(fixture.nativeElement.querySelector('.spinner-border')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain('Aadhaar');
  });

  it('previews an image by its content type and offers a download', () => {
    const fixture = create(of(new Blob(['x'], { type: 'image/png' })), 'scan.dat');
    const el: HTMLElement = fixture.nativeElement;

    expect(fixture.componentInstance.kind).toBe('image');
    expect(el.querySelector('img.doc-image')?.getAttribute('src')).toBe('blob:x');
    expect(el.querySelector('a[download]')?.getAttribute('download')).toBe('scan.dat');
  });

  it('recognizes an image by its file extension when the type is generic', () => {
    expect(create(of(new Blob(['x'])), 'photo.JPG').componentInstance.kind).toBe('image');
  });

  it('embeds a PDF', () => {
    const fixture = create(of(new Blob(['x'], { type: 'application/pdf' })), 'doc');

    expect(fixture.componentInstance.kind).toBe('pdf');
    expect(fixture.componentInstance.pdfUrl).not.toBeNull();
    expect(fixture.nativeElement.querySelector('iframe.doc-pdf')).not.toBeNull();
  });

  it('recognizes a PDF by extension', () => {
    expect(create(of(new Blob(['x'])), 'report.pdf').componentInstance.kind).toBe('pdf');
  });

  it('shows the unsupported notice for other files, with the title as download name when there is no file name', () => {
    const fixture = create(of(new Blob(['x'], { type: 'application/zip' })), null);

    expect(fixture.componentInstance.kind).toBe('other');
    expect(fixture.nativeElement.textContent).toContain('onboardingSubmissions.documents.unsupported');
    expect(fixture.nativeElement.querySelector('a[download]')?.getAttribute('download')).toBe('Aadhaar');
  });

  it('shows the failure with the server explanation', () => {
    const fixture = create(throwError(() => ({ error: { message: 'File missing in DMS' } })));

    expect(fixture.componentInstance.failed).toBe(true);
    expect(fixture.nativeElement.querySelector('.doc-error-detail')?.textContent).toContain('File missing in DMS');
    expect(fixture.nativeElement.querySelector('a[download]')).toBeNull();
  });

  it('shows the failure without a detail when the server gave none', () => {
    const fixture = create(throwError(() => ({})));

    expect(fixture.componentInstance.errorDetail).toBeNull();
    expect(fixture.nativeElement.querySelector('.doc-error-detail')).toBeNull();
  });

  it('closes from either button', () => {
    const fixture = create(of(new Blob(['x'], { type: 'image/png' })));

    (fixture.nativeElement.querySelector('.doc-close') as HTMLElement).click();
    (fixture.nativeElement.querySelector('.doc-actions .btn-accent') as HTMLElement).click();

    expect(dialogRef.close).toHaveBeenCalledTimes(2);
  });

  it('releases the object url when destroyed', () => {
    const fixture = create(of(new Blob(['x'], { type: 'image/png' })));

    fixture.destroy();

    expect(revoked).toEqual(['blob:x']);
  });

  it('does not revoke anything when no file was ever loaded', () => {
    const fixture = create(new Subject<Blob>());

    fixture.destroy();

    expect(revoked).toEqual([]);
  });
});
