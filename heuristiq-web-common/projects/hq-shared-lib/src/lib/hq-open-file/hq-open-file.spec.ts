import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { HqOpenFile, HqOpenFileData } from './hq-open-file';

describe('HqOpenFile', () => {
  let fixture: ComponentFixture<HqOpenFile>;
  let close: ReturnType<typeof vi.fn>;
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;

  beforeEach(() => {
    createObjectURL = vi.fn(() => 'blob:http://localhost/preview-1');
    revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = revokeObjectURL as unknown as typeof URL.revokeObjectURL;
  });

  afterEach(() => {
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  });

  function render(data: HqOpenFileData): HTMLElement {
    close = vi.fn();
    TestBed.configureTestingModule({
      declarations: [HqOpenFile],
      imports: [MatIconModule],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(HqOpenFile);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('previews an image from an object URL it creates for the blob', () => {
    const blob = new Blob(['x'], { type: 'image/png' });
    const el = render({ blob, fileName: 'photo.png' });
    const component = fixture.componentInstance;

    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(component.isImage).toBe(true);
    expect(component.isPdf).toBe(false);
    expect(component.objectUrl).toBe('blob:http://localhost/preview-1');
    expect(el.querySelector('.hq-open-file__name')?.textContent?.trim()).toBe('photo.png');
    const img = el.querySelector('img');
    expect(img?.getAttribute('src')).toBe('blob:http://localhost/preview-1');
    expect(img?.getAttribute('alt')).toBe('photo.png');
    expect(el.querySelector('iframe')).toBeNull();
  });

  it('previews a PDF in an iframe', () => {
    const el = render({ blob: new Blob(['%PDF'], { type: 'application/pdf' }), fileName: 'doc.pdf' });
    expect(fixture.componentInstance.isPdf).toBe(true);
    expect(fixture.componentInstance.isImage).toBe(false);
    const frame = el.querySelector('iframe');
    expect(frame?.getAttribute('src')).toBe('blob:http://localhost/preview-1');
    expect(frame?.getAttribute('title')).toBe('doc.pdf');
    expect(el.querySelector('img')).toBeNull();
  });

  it('shows no preview for other file types', () => {
    const el = render({ blob: new Blob(['a,b'], { type: 'text/csv' }), fileName: 'data.csv' });
    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('iframe')).toBeNull();
    expect(el.querySelector('.hq-open-file__name')?.textContent?.trim()).toBe('data.csv');
  });

  it('closes the dialog from the close button', () => {
    const el = render({ blob: new Blob(['x'], { type: 'image/jpeg' }), fileName: 'a.jpg' });
    const button = el.querySelector<HTMLButtonElement>('button[aria-label="Close preview"]');
    button?.click();
    expect(close).toHaveBeenCalledOnce();
  });

  it('revokes its object URL when destroyed', () => {
    render({ blob: new Blob(['x'], { type: 'image/png' }), fileName: 'a.png' });
    expect(revokeObjectURL).not.toHaveBeenCalled();
    fixture.destroy();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/preview-1');
  });
});
