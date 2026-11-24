import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { HqOpenFile } from './hq-open-file';
import { HqFileViewerService } from './hq-file-viewer.service';

describe('HqFileViewerService', () => {
  let dialog: { open: ReturnType<typeof vi.fn> };
  let viewer: HqFileViewerService;
  let click: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    dialog = { open: vi.fn() };
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() });
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: dialog }] });
    viewer = TestBed.inject(HqFileViewerService);
  });

  afterEach(() => vi.restoreAllMocks());

  it('opens an image in the shared viewer', () => {
    const blob = new Blob(['i'], { type: 'image/png' });
    viewer.show(blob, 'photo.png');
    expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: { blob, fileName: 'photo.png' } }));
    expect(click).not.toHaveBeenCalled();
  });

  it('opens a PDF in the shared viewer', () => {
    viewer.show(new Blob(['p'], { type: 'application/pdf' }), 'form.pdf');
    expect(dialog.open).toHaveBeenCalledTimes(1);
  });

  it('saves any other file under its name instead of opening the viewer', () => {
    viewer.show(new Blob(['d'], { type: 'application/zip' }), 'notes.zip');
    expect(dialog.open).not.toHaveBeenCalled();
    expect(click).toHaveBeenCalledTimes(1);
  });

  it('tells which files it can preview', () => {
    expect(viewer.canPreview(new Blob([], { type: 'image/webp' }))).toBe(true);
    expect(viewer.canPreview(new Blob([], { type: 'text/plain' }))).toBe(false);
  });
});
